package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.RlauxAuditIF
import org.cryptobiotic.rlauxe.audit.runAudit
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.sqrt

private val quiet = true

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

// A WorkflowTask is always for a single contest (unlike a Workflow which may be multi-contest)
class WorkflowTask(
    val name: String,
    val workflow: RlauxAuditIF,
    val otherParameters: Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name
    override fun run(): WorkflowResult {
        val lastRound = runAudit(name, workflow, quiet = quiet)
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

        // since its single contest, does the lastRound always have the entire set of mvr sampleNumbers?
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

