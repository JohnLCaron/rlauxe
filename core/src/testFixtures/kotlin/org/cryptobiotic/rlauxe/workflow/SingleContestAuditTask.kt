package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.betting.TestH0Status

private val quiet = true

private val logger = KotlinLogging.logger("ContestAuditTask")

interface ContestAuditTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTask<WorkflowResult>
}

// A ContestAuditTask is always for a single contest (unlike a Workflow which may be multi-contest)
class SingleContestAuditTask(
    val name: String,
    val workflow: AuditWorkflow,
    val otherParameters: Map<String, Any>,
) : ConcurrentTask<WorkflowResult> {

    override fun name() = name
    override fun run(): WorkflowResult {

        // run all needed rounds. lastRound shows how many rounds were needed
        val lastRound = runTestAuditToCompletion(name, workflow, quiet = quiet)

        if (lastRound == null) {  // nextRound.roundIdx > maxRounds)
            logger.error { "lastRound is null, setting contest to FailMaxRoundsAllowed"}
            return WorkflowResult(
                name,
                0,
                0.0,
                TestH0Status.FailMaxRoundsAllowed,
                0.0, 0.0, 0.0,
                otherParameters,
            )
        }

        // single contest
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

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTask<List<WorkflowResult>>>, nthreads:Int = 40): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunner<List<WorkflowResult>>().run(tasks, nthreads=nthreads)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

fun runWorkflows(tasks: List<ConcurrentTask<List<WorkflowResult>>>, nthreads:Int = 40): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunner<List<WorkflowResult>>().run(tasks, nthreads=nthreads)
    val results: List<WorkflowResult> = rresults.flatten()
    return results
}