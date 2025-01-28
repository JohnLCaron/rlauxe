package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.sampling.makeOtherCvrForContest
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// for running workflows with one contest, multiple times for testing

private val quiet = true

// runs test workflow with fake mvrs already generated, and the cvrs are variants of those
// return number of mvrs hand counted
fun runWorkflow(name: String, workflow: RlauxWorkflow, testMvrs: List<Cvr>, quiet: Boolean=false): Int {
    val stopwatch = Stopwatch()

    val previousSamples = mutableSetOf<Int>()
    var rounds = mutableListOf<Round>()
    var roundIdx = 1

    var prevMvrs = emptyList<Cvr>()
    var done = false
    while (!done) {
        val indices = workflow.chooseSamples(prevMvrs, roundIdx, show=false)

        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        rounds.add(currRound)
        previousSamples.addAll(indices)

        if (!quiet) println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        val sampledMvrs = indices.map {
            testMvrs[it]
        }

        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        if (!quiet) println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        prevMvrs = sampledMvrs
        roundIdx++
    }

    if (!quiet) {
        rounds.forEach { println(it) }
        workflow.showResults()
    }
    return rounds.last().sampledIndices.size
}

data class Round(val round: Int, val sampledIndices: List<Int>, val previousSamples: Set<Int>) {
    var newSamples: Int = 0
    init {
        newSamples = sampledIndices.count { it !in previousSamples }
    }
    override fun toString(): String {
        return "Round(round=$round, newSamples=$newSamples)"
    }
}

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

class ClcaWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Double>,
    val auditConfigIn: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Nb: Int = Nc
    ): WorkflowTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val auditConfig = auditConfigIn ?:
            AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10,
                clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct))

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        if (!auditConfig.hasStyles && Nb > Nc) {
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeOtherCvrForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clca = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs, quiet = quiet)
        return WorkflowTask(
            "genAuditWithErrorsPlots mvrsFuzzPct = $mvrsFuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

class PollingWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
    val auditConfigIn: AuditConfig? = null,
    val Nb: Int = Nc,
    ) : WorkflowTaskGenerator {
    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.POLLING, true, seed = Random.nextLong(), ntrials = 10,
            pollingConfig = PollingConfig(fuzzPct = fuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, fuzzPct)
        var ballotManifest = sim.makeBallotManifest(auditConfig.hasStyles)

        if (!auditConfig.hasStyles && Nb > Nc) {
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeOtherCvrForContest(otherContestId) }
            testMvrs = testMvrs + otherCvrs

            val otherBallots = List<Ballot>(Nb - Nc) { Ballot("other${Nc+it}", false, null) }
            ballotManifest = BallotManifest(ballotManifest.ballots + otherBallots, emptyList())
        }

        val polling = PollingWorkflow(auditConfig, listOf(sim.contest), ballotManifest, Nb, quiet = quiet)
        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            polling,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 2.0)
        )
    }
}

class OneAuditWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
) : WorkflowTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val contestOA2 = makeContestOA(margin, Nc, cvrPercent = cvrPercent, phantomPct, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

        val oneaudit = OneAuditWorkflow(
            AuditConfig(
                AuditType.ONEAUDIT, true, seed = Random.nextLong(), ntrials = 10,
                pollingConfig = PollingConfig(fuzzPct = fuzzPct)
            ),
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
        val nmvrs = runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minAssertion()!!

        return if (minAssertion.roundResults.isEmpty()) {
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                TestH0Status.FailPct,
                0.0, 0.0, 0.0, 0.0,
                otherParameters,
                100.0,
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
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status.fail) 100.0 else 0.0
            )
        }
    }
}

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}


data class WorkflowResult(val Nc: Int,
                          val margin: Double,
                          val status: TestH0Status,
                          val nrounds: Double,
                          val samplesUsed: Double, // redundant
                          val samplesNeeded: Double,
                          val nmvrs: Double,
                          val parameters: Map<String, Double>,
                          val failPct: Double, // from avgWorkflowResult()
)

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val failures = runs.count { it.status.fail }
    val failPct = if (runs.isEmpty()) 100.0 else 100.0 * failures / runs.size
    val successRuns = runs.filter { !it.status.fail }

    return if (runs.isEmpty()) {
        WorkflowResult(
            0,
            0.0,
            TestH0Status.AllFailPct,
            0.0, 0.0, 0.0,0.0,
            emptyMap(),
            100.0,
            )
    } else if (successRuns.isEmpty()) {
        val first = runs.first()
        WorkflowResult(
            first.Nc,
            first.margin,
            TestH0Status.FailPct,
            0.0, first.Nc.toDouble(), first.Nc.toDouble(), first.Nc.toDouble(),
            first.parameters,
            100.0,
            )
    } else {
        val first = successRuns.first()
        WorkflowResult(
            first.Nc,
            first.margin,
            first.status,
            successRuns.map { it.nrounds }.average(),
            successRuns.map { it.samplesUsed }.average(),
            successRuns.map { it.samplesNeeded }.average(),
            successRuns.map { it.nmvrs }.average(),
            first.parameters,
            failPct,
        )
    }
}

