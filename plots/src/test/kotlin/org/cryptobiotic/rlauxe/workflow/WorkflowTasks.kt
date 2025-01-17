package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.core.ComparisonAssertion
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.util.df
import kotlin.random.Random
import kotlin.test.Test

private val quiet = true
private val nruns = 100  // number of times to run workflow


class ClcaWorkflowTask(val name: String,
                       val workflow: RlauxWorkflow,
                       val testCvrs: List<Cvr>,
                       val otherParameters: Map<String, Double>,
): ConcurrentTaskG<WorkflowResult> {
    override fun name() = name

    override fun run() : WorkflowResult {
        runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minComparisonAssertion()!!

        val lastRound = minAssertion.roundResults.last()
        val srt = WorkflowResult(
            contestUA.Nc,
            minAssertion.assorter.reportedMargin(),
            lastRound.status,
            minAssertion.round.toDouble(),
            lastRound.samplesUsed.toDouble(),
            lastRound.samplesNeeded.toDouble(),
            otherParameters
        )
        return srt
    }

    override fun shuffle() {
        workflow.shuffle(Random.nextLong())
    }
}

class OneAuditWorkflowTask(
    val name: String,
    var workflow: RlauxWorkflow,
    var testCvrs: List<Cvr>,
    val otherParameters: Map<String, Double>,
) : ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run(): WorkflowResult {
        runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion: ComparisonAssertion = contestUA.minComparisonAssertion()!!

        // val N: Int, val margin: Double, val nrounds: Int,
        //                           val usedSamples: Int, val neededSamples: Int,
        //                           val testParameters: Map<String, Double>
        val lastRound = minAssertion.roundResults.last()
        val srt = WorkflowResult(
            contestUA.Nc,
            minAssertion.assorter.reportedMargin(),
            lastRound.status,
            minAssertion.round.toDouble(),
            lastRound.samplesUsed.toDouble(),
            lastRound.samplesNeeded.toDouble(),
            otherParameters
        )
        return srt
    }

    override fun shuffle() {
        workflow.shuffle(Random.nextLong())
    }
}

class PollingWorkflowTask(val name: String,
                          val workflow: RlauxWorkflow,
                          var testCvrs: List<Cvr>,
                          val otherParameters: Map<String, Double>,
): ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run() : WorkflowResult {
        runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minPollingAssertion()!!

        // val N: Int, val margin: Double, val nrounds: Int,
        //                           val usedSamples: Int, val neededSamples: Int,
        //                           val testParameters: Map<String, Double>
        val lastRound = minAssertion.roundResults.last()
        val srt = WorkflowResult(
            contestUA.Nc,
            minAssertion.assorter.reportedMargin(),
            lastRound.status,
            minAssertion.round.toDouble(),
            lastRound.samplesUsed.toDouble(),
            lastRound.samplesNeeded.toDouble(),
            otherParameters
        )
        return srt
    }

    override fun shuffle() {
        workflow.shuffle(Random.nextLong())
    }
}

class TestWorkflowTasks {

    @Test
    fun genOneAuditWorkflowMarginPlots() {
        val N = 50000
        val cvrPercents = listOf(.2, .4, .6, .8, .9, .95, .99)
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT,
            hasStyles = true,
            seed = Random.nextLong(),
            quantile = .80,
            ntrials = 10
        )
        println("N=${N} ntrials=${auditConfig.ntrials} fuzzPct = ${auditConfig.fuzzPct}")

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        cvrPercents.forEach { cvrPercent ->
            margins.forEach { margin ->
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
                val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
                val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs, quiet = quiet)
                val otherParameters =
                    mapOf("cvrPercent" to cvrPercent, "nruns" to nruns.toDouble(), "ntrials" to 10.toDouble(),)
                tasks.add(
                    RepeatedTaskRunner(
                        10,
                        OneAuditWorkflowTask(
                            "genOneAuditWorkflowMarginPlots margin = $margin",
                            workflow,
                            testCvrs,
                            otherParameters,
                        )
                    )
                )
            }
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "OneAuditMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.parameters["cvrPercent"]!!.toDouble()) }
    }

    @Test
    fun genPollingWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct = 0.0..0.0
        val phantomPct = 0.00..0.00
        val ncontests = 1
        val nbs = 1
        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = Random.nextLong(),
            quantile = .80,
            ntrials = 10
        )

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val marginRange = margin..margin
            val test = MultiContestTestData(
                ncontests,
                nbs,
                N,
                marginRange = marginRange,
                underVotePctRange = underVotePct,
                phantomPctRange = phantomPct
            )
            val ballots = test.makeBallotsForPolling(true)
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
            val workflow = PollingWorkflow(
                auditConfig,
                test.contests,
                BallotManifest(ballots, test.ballotStyles),
                N,
                quiet = quiet
            )
            tasks.add(
                RepeatedTaskRunner(
                    nruns,
                    PollingWorkflowTask(
                        "genPollingWorkflowMarginPlots margin = $margin",
                        workflow,
                        testCvrs,
                        mapOf("nruns" to nruns.toDouble())
                    )
                )
            )
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "PollingMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

    @Test
    fun genClcaWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct = 0.0..0.0
        val phantomPct = 0.00..0.00
        val ncontests = 1
        val nbs = 1

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val marginRange = margin..margin
            val test = MultiContestTestData(
                ncontests,
                nbs,
                N,
                marginRange = marginRange,
                underVotePctRange = underVotePct,
                phantomPctRange = phantomPct
            )
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
            val auditConfig =
                AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10)
            val workflow = ComparisonWorkflow(auditConfig, test.contests, emptyList(), testCvrs, quiet = quiet)

            val clcaRuns = 1
            tasks.add(
                RepeatedTaskRunner(
                    clcaRuns,
                    ClcaWorkflowTask(
                        "genClcaWorkflowMarginPlots margin = $margin",
                        workflow,
                        testCvrs,
                        mapOf("nruns" to clcaRuns.toDouble())
                    )
                )
            )
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "ClcaMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }
}
