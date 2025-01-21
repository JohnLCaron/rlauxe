package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import kotlin.random.Random

private val quiet = true

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

class ClcaWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
): WorkflowTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val sim = ContestSimulation.make2wayTestContest(Nc=N, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, fuzzPct)

        val clca = ComparisonWorkflow(
            AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            listOf(sim.contest), emptyList(), testCvrs, quiet = quiet
        )
        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 3.0)
        )
    }
}

class PollingWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
    ) : WorkflowTaskGenerator {
    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {

        val sim = ContestSimulation.make2wayTestContest(Nc=N, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, fuzzPct)
        val ballotManifest = sim.makeBallotManifest(true)

        val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
                listOf(sim.contest), ballotManifest, N, quiet = quiet
            )

        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            polling,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 2.0)
        )
    }
}

class OneAuditWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
) : WorkflowTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val contestOA2 = makeContestOA(margin, N, cvrPercent = cvrPercent, phantomPct, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

        val oneaudit = OneAuditWorkflow(
            AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            listOf(contestOA2), oaCvrs, quiet = quiet
        )
        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            oneaudit,
            oaMvrs,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to fuzzPct, "auditType" to 1.0)
        )
    }
}

class WorkflowTask(
    val name: String,
    val workflow: RlauxWorkflow,
    val testCvrs: List<Cvr>,
    val otherParameters: Map<String, Double>,
) : ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run(): WorkflowResult {
        runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minAssertion()!!

        return if (minAssertion.roundResults.isEmpty()) {
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                TestH0Status.FailPct,
                0.0, 0.0, 0.0,
                otherParameters
            )
        } else {
            val lastRound = minAssertion.roundResults.last()
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                lastRound.samplesNeeded.toDouble(),
                otherParameters
            )
        }
    }
}

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val failures = runs.count{ it.status.fail }
    val failPct = if (runs.isEmpty()) 0.0 else 100.0 * failures / runs.size
    val successRuns = runs.filter { !it.status.fail }

    return if (runs.isEmpty()) {
        WorkflowResult(
            0,
            0.0,
            TestH0Status.AllFailPct,
            0.0, 0.0, 0.0,
            emptyMap()
        )
    } else if (successRuns.isEmpty()) {
        val first = runs.first()
        WorkflowResult(
            first.N,
            first.margin,
            TestH0Status.FailPct,
            0.0, first.N.toDouble(), first.N.toDouble(),
            first.parameters,
        )
    } else {
        val first = successRuns.first()
        WorkflowResult(
            first.N,
            first.margin,
            first.status,
            successRuns.map { it.nrounds }.average(),
            successRuns.map { it.samplesUsed }.average(),
            successRuns.map { it.samplesNeeded }.average(),
            first.parameters,
            failPct,
        )
    }
}

