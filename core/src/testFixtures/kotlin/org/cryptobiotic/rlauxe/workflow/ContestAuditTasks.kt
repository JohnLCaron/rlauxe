package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.sqrt

private val quiet = true

private val logger = KotlinLogging.logger("ContestAuditTask")

interface ContestAuditTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

// A ContestAuditTask is always for a single contest (unlike a Workflow which may be multi-contest)
class ContestAuditTask(
    val name: String,
    val workflow: AuditWorkflow,
    val otherParameters: Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name
    override fun run(): WorkflowResult {

        val lastRound = runTestAuditToCompletion(name, workflow, quiet = quiet)

        if (lastRound == null) {
            logger.error { "lastRound is null, setting contest to ContestMisformed"}
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

        // TODO since its single contest, does the lastRound always have the entire set of mvr sampleNumbers?
        val nmvrs = lastRound.samplePrns.size // LOOK ??
        val contest = lastRound.contestRounds.first() // theres only one contest

        val minAssertion = contest.minAssertion() // TODO why would this fail ?
        if (minAssertion == null) {
            logger.error { "minAssertion is null, setting contest to ContestMisformed"}
            return WorkflowResult(
                name,
                contest.Npop,
                0.0,
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        }

        val assorter = minAssertion.assertion.assorter
        return if (minAssertion.auditResult == null) { // TODO why is this empty?
            logger.error { "minAssertion.auditResult is null, setting contest to ContestMisformed"}
            WorkflowResult(
                name,
                contest.Npop,
                assorter.dilutedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRoundResult = minAssertion.auditResult!!
            WorkflowResult(
                name,
                contest.Npop,
                assorter.dilutedMargin(),
                lastRoundResult.status,
                nrounds = minAssertion.roundProved.toDouble(),
                samplesUsed = lastRoundResult.samplesUsed.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRoundResult.status != TestH0Status.StatRejectNull) 100.0 else 0.0
            )
        }
    }
}

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>, nthreads:Int = 40): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks, nthreads=nthreads)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

fun runWorkflows(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>, nthreads:Int = 40): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks, nthreads=nthreads)
    val results: List<WorkflowResult> = rresults.flatten()
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

    ////
    val startingRates: ClcaErrorCounts? = null, // starting error rates (clca only)
    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
) {
    fun Dparam(key: String): Double {
        return (parameters[key]!! as String).toDouble()
    }

    fun show() = buildString {
        appendLine("WorkflowResult(name='$name', Nc=$Nc, margin=$margin, status=$status, nrounds=$nrounds, samplesUsed=$samplesUsed, nmvrs=$nmvrs, parameters=$parameters, failPct=$failPct, usedStddev=$usedStddev, mvrMargin=$mvrMargin")
        if (startingRates != null) append("  startingRates=${startingRates.show()}")
        if (measuredCounts != null) append("  measuredCounts=${measuredCounts.show()}")
        appendLine(")")
    }
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

