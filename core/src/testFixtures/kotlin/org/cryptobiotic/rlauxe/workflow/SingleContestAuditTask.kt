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
class SingleContestAuditTask(
    val name: String,
    val workflow: AuditWorkflow,
    val otherParameters: Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name
    override fun run(): WorkflowResult {

        // run all needed rounds. lastRound shows how many rounds were needed
        val lastRound = runTestAuditToCompletion(name, workflow, quiet = quiet)

        if (lastRound == null) {  // TODO why would this be null?
            logger.error { "lastRound is null, setting contest to ContestMisformed"}
            return WorkflowResult(
                name,
                0,
                0.0,
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
            )
        }

        // TODO since its single contest, does the lastRound always have the entire set of mvr sampleNumbers?
        val nmvrs = lastRound.samplePrns.size
        val contest = lastRound.contestRounds.first() // theres only one contest

        val minAssertion = contest.minAssertion()
        if (minAssertion == null) {  // TODO why would this be null ?
            logger.error { "minAssertion is null, setting contest to ContestMisformed"}
            return WorkflowResult(
                name,
                contest.Npop,
                0.0,
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
            )
        }

        val assorter = minAssertion.assertion.assorter
        return if (minAssertion.auditResult == null) { // TODO why would this be null ?
            logger.error { "minAssertion.auditResult is null, setting contest to ContestMisformed"}
            WorkflowResult(
                name,
                contest.Npop,
                assorter.dilutedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
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
                nmvrs = nmvrs.toDouble(),
                otherParameters,
                wtf = (nmvrs - lastRoundResult.samplesUsed) / minAssertion.roundProved.toDouble(),
                failPct = if (lastRoundResult.status != TestH0Status.StatRejectNull) 100.0 else 0.0
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