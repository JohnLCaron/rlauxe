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

class GenAuditSampleSizePlots {

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
            fuzzPct = null,
            ntrials = 10
        )
        println("N=${N} ntrials=${auditConfig.ntrials} fuzzPct = ${auditConfig.fuzzPct}")

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        cvrPercents.forEach { cvrPercent ->
            margins.forEach { margin ->
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
                val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
                val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs, quiet = quiet)
                val otherParameters = mapOf("cvrPercent" to cvrPercent, "nruns" to nruns.toDouble(), "ntrials" to 10.toDouble(),)
                tasks.add(
                    RepeatedTaskRunner(10,
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

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { WorkflowResult.avgRepeatedRuns(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "OneAuditMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.parameters["cvrPercent"]!!.toDouble()) }
    }

    class OneAuditWorkflowTask(val name: String,
                               var workflow: RlauxWorkflow,
                               var testCvrs: List<Cvr>,
                               val otherParameters: Map<String, Double>,
    ): ConcurrentTaskG<WorkflowResult> {
        override fun name() = name
        override fun run() : WorkflowResult {
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

    @Test
    fun genPollingWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct= 0.0 .. 0.0
        val phantomPct= 0.00 .. 0.00
        val ncontests = 1
        val nbs = 1
        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = Random.nextLong(),
            quantile = .80,
            fuzzPct = null,
            ntrials = 10
        )

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val marginRange= margin .. margin
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
                RepeatedTaskRunner(nruns,
                    PollingWorkflowTask(
                        "genPollingWorkflowMarginPlots margin = $margin",
                        workflow,
                        testCvrs,
                        mapOf("nruns" to nruns.toDouble())
                    )
                )
            )
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { WorkflowResult.avgRepeatedRuns(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "PollingMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
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

    @Test
    fun genClcaWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct= 0.0 .. 0.0
        val phantomPct= 0.00 .. 0.00
        val ncontests = 1
        val nbs = 1

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val marginRange= margin .. margin
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
                AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10)
            val workflow = ComparisonWorkflow(auditConfig, test.contests, emptyList(), testCvrs, quiet = quiet)

            val clcaRuns = 1
            tasks.add(
                RepeatedTaskRunner(clcaRuns,
                    ClcaWorkflowTask(
                        "genClcaWorkflowMarginPlots margin = $margin",
                        workflow,
                        testCvrs,
                        mapOf("nruns" to clcaRuns.toDouble())
                    )
                )
            )
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { WorkflowResult.avgRepeatedRuns(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "ClcaMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results,"category") { "all" }
    }

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

    /*
    @Test
    fun genAllWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct= 0.0 .. 0.0
        val phantomPct= 0.00 .. 0.00
        val ncontests = 1
        val nbs = 1

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            // oneaudit 20%
            val contestOA2 = makeContestOA(margin, N, cvrPercent = .20, 0.0, undervotePercent = 0.0)
            val oaCvrs2 = contestOA2.makeTestCvrs() // one for each ballot, with and without CVRS
            val oneaudit2 = OneAuditWorkflow(
                AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                listOf(contestOA2), oaCvrs2, quiet = quiet
            )
            val otherParameters2 = mapOf("auditType" to 4.0, "nruns" to nruns.toDouble(), "cvrPercent" to .20, "skewVotes" to 0.0, "undervotePercent" to 0.0,)
            tasks.add(
                RepeatedTaskRunner(nruns,
                    OneAuditWorkflowTask(
                        "genOneAuditWorkflowMarginPlots margin = $margin",
                        oneaudit2,
                        oaCvrs2,
                        otherParameters2,
                    )
                )
            )

            // oneaudit 80%
            val contestOA = makeContestOA(margin, N, cvrPercent = .80, 0.0, undervotePercent = 0.0)
            val oaCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
            val oneaudit = OneAuditWorkflow(
                AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                listOf(contestOA), oaCvrs, quiet = quiet
            )
            val otherParameters = mapOf("auditType" to 1.0, "nruns" to nruns.toDouble(), "cvrPercent" to .80, "skewVotes" to 0.0, "undervotePercent" to 0.0,)
            tasks.add(
                RepeatedTaskRunner(nruns,
                    OneAuditWorkflowTask(
                        "genOneAuditWorkflowMarginPlots margin = $margin",
                        oneaudit,
                        oaCvrs,
                        otherParameters,
                    )
                )
            )

            val marginRange= margin .. margin
            val test = MultiContestTestData(
                ncontests,
                nbs,
                N,
                marginRange = marginRange,
                underVotePctRange = underVotePct,
                phantomPctRange = phantomPct
            )
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms

            // polling
            val ballots = test.makeBallotsForPolling(true)
            val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet = quiet
            )
            tasks.add(
                RepeatedTaskRunner(nruns,
                    PollingWorkflowTask(
                        "genPollingWorkflowMarginPlots margin = $margin",
                        polling,
                        testCvrs,
                        mapOf("auditType" to 2.0, "nruns" to nruns.toDouble())
                    )
                )
            )

            // clca
            val clca = ComparisonWorkflow(
                AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                test.contests, emptyList(), testCvrs, quiet = quiet
            )

            tasks.add(
                RepeatedTaskRunner(nruns,
                    ClcaWorkflowTask(
                        "genClcaWorkflowMarginPlots margin = $margin",
                        clca,
                        testCvrs,
                        mapOf("auditType" to 3.0, "nruns" to nruns.toDouble())
                    )
                )
            )
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { WorkflowResult.avgRepeatedRuns(it) }

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "AllTypesNoErrorsRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit 80%"
                2.0 -> "polling"
                3.0 -> "clca"
                4.0 -> "oneaudit 20%"
                else -> "unknown"
            }
        }
    }

    @Test
    fun plotLinearOrLog() {
        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "AllTypesNoErrorsRepeated"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "auditType", useLog=true) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit 80%"
                2.0 -> "polling"
                3.0 -> "clca"
                4.0 -> "oneaudit 20%"
                else -> "unknown"
            }
        }
    }

     */
}