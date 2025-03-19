package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.raire.simulateRaireTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.sqrt

private val quiet = true

// runs test workflow rounds until finished
// return last audit round
fun runWorkflow(name: String, workflow: RlauxWorkflowIF, quiet: Boolean=true): AuditRound? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRound? = null
    var done = false
    while (!done) {
        nextRound = workflow.startNewRound(quiet=quiet)
        if (nextRound.sampleNumbers.isEmpty()) {
            done = true

        } else {
            stopwatch.start()
            workflow.setMvrsBySampleNumber(nextRound.sampleNumbers )
            if (!quiet) println("\nrunAudit ${nextRound.roundIdx}")
            done = workflow.runAudit(nextRound, quiet)
            if (!quiet) println(" runAudit ${nextRound.roundIdx} done=$done samples=${nextRound.sampleNumbers.size}")
        }
    }

    /*
    if (!quiet && rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    } */

    return nextRound
}

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

class RaireWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val nsimEst: Int = 100,
    ): WorkflowTaskGenerator {
    override fun name() = "RaireWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val useConfig = auditConfig ?:
        AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror))

        val (rcontest, testCvrs) = simulateRaireTestData(N=Nc, contestId=111, ncands=4, minMargin=margin, undervotePct=underVotePct, phantomPct=phantomPct, quiet = true)
        var testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val clca = ClcaWorkflow(useConfig, emptyList(), listOf(rcontest),
            BallotCardsClcaStart(testCvrs, testMvrs, useConfig.seed))
        return WorkflowTask(
            name(),
            clca,
            // testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0)
        )
    }
}

class WorkflowTask(
    val name: String,
    val workflow: RlauxWorkflowIF,
    val otherParameters: Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run(): WorkflowResult {
        val lastRound = runWorkflow(name, workflow, quiet = quiet)
        if (lastRound == null) {
            return WorkflowResult(
                name,
                0,
                0.0,
                TestH0Status.ContestMisformed, // TODO why empty?
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        }

        val nmvrs = lastRound.sampleNumbers.size // LOOK ??
        val contest = lastRound.contestRounds.first() // theres only one

        val minAssertion = contest.minAssertion() // TODO why would this fail ?
            ?: return WorkflowResult(
                name,
                contest.Nc,
                    0.0,
                    TestH0Status.ContestMisformed,
                    0.0, 0.0, 0.0,
                    otherParameters,
                    100.0,
                )

        val assorter = minAssertion.assertion.assorter
        return if (minAssertion.auditResult == null) { // TODO why is this empty?
            WorkflowResult(
                name,
                contest.Nc,
                assorter.reportedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRound = minAssertion.auditResult!!
            WorkflowResult(
                name,
                contest.Nc,
                assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0
            )
        }
    }
}

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>, nthreads:Int = 40): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks, nthreads=nthreads)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

data class WorkflowResult(
    val name: String,
    val Nc: Int,
    val margin: Double,
    val status: TestH0Status,
    val nrounds: Double,
    val samplesUsed: Double,  // weighted
    val nmvrs: Double, // weighted
    val parameters: Map<String, Any>,

    // from avgWorkflowResult()
    val failPct: Double = 100.0,
    val usedStddev: Double = 0.0, // success only
    val mvrMargin: Double = 0.0,
) {
    fun Dparam(key: String) = (parameters[key]!! as String).toDouble()
}

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val successRuns = runs.filter { it.status.success }

    val result =  if (runs.isEmpty()) { // TODO why all empty?
        WorkflowResult(
            "empty",
            0,
            0.0,
            TestH0Status.ContestMisformed,
            0.0, 0.0, 0.0,
            emptyMap(),
            )
    } else if (successRuns.isEmpty()) { // TODO why all empty?
        val first = runs.first()
        WorkflowResult(
            first.name,
            first.Nc,
            first.margin,
            TestH0Status.MinMargin, // TODO maybe TestH0Status.AllFail ?
            0.0, first.Nc.toDouble(), first.Nc.toDouble(),
            first.parameters,
            mvrMargin=runs.filter{ it.nrounds > 0 }.map { it.mvrMargin }.average(),
        )
    } else {
        val first = successRuns.first()
        /* if (first.name == "ClcaSingleRoundAuditTaskGenerator" &&
            ((first.parameters["cat"] as String) == "max99") &&
            ((first.parameters["mvrsFuzzPct"] as Double) == .05)) {
            print("")
        } */
        val failures = runs.size - successRuns.count()
        val successPct = successRuns.count() / runs.size.toDouble()
        val failPct = failures / runs.size.toDouble()
        val Nc = first.Nc
        val welford = Welford()
        successRuns.forEach { welford.update(it.samplesUsed) }

        WorkflowResult(
            first.name,
            Nc,
            first.margin,
            first.status, // hmm kinda bogus
            runs.filter{ it.nrounds > 0 } .map { it.nrounds }.average(),
            samplesUsed = successPct * welford.mean + failPct * Nc,
            nmvrs = successPct * successRuns.map { it.nmvrs }.average() + failPct * Nc,
            first.parameters,

            100.0 * failPct,
            usedStddev=sqrt(welford.variance()), // success only
            mvrMargin=runs.filter{ it.nrounds > 0 }.map { it.mvrMargin }.average(),
        )
    }

    return result
}

