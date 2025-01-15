package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.util.df
import kotlin.random.Random

private val quiet = true

class GenOneAuditSampleSizePlots {

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

        val tasks = mutableListOf<OneAuditWorkflowTask>()
        cvrPercents.forEach { cvrPercent ->
            margins.forEach { margin ->
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
                val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
                val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs, quiet=quiet)
                val otherParameters = mapOf("cvrPercent" to cvrPercent, "skewVotes" to 0.0, "undervotePercent" to 0.0,)
                tasks.add(
                    OneAuditWorkflowTask(
                        "genOneAuditWorkflowMarginPlots margin = $margin",
                        workflow, testCvrs,
                        otherParameters,
                    )
                )
            }
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = ConcurrentTaskRunnerG<WorkflowResult>().run(tasks)

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "OneAuditMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.parameters["cvrPercent"]!!) }
    }

    class OneAuditWorkflowTask(val name: String,
                      val workflow: RlauxWorkflow,
                      val testCvrs: List<Cvr>,
                      val otherParameters: Map<String, Double>,
    ): ConcurrentTaskG<WorkflowResult> {
        override fun name() = name
        override fun run() : WorkflowResult {
            runWorkflow(workflow, testCvrs, quiet=quiet)

            val contestUA = workflow.getContests().first() // theres only one
            val minAssertion: ComparisonAssertion = contestUA.minComparisonAssertion()!!

            // val N: Int, val margin: Double, val nrounds: Int,
            //                           val usedSamples: Int, val neededSamples: Int,
            //                           val testParameters: Map<String, Double>
            val lastRound = minAssertion.roundResults.last()
            val srt = WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                minAssertion.round,
                lastRound.samplesUsed,
                lastRound.samplesNeeded,
                otherParameters
                )
            return srt
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
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = Random.nextLong(), quantile=.80, fuzzPct = null, ntrials=10)

        val tasks = mutableListOf<PollingWorkflowTask>()
        margins.forEach { margin ->
            val marginRange= margin .. margin
            val test = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
            val ballots = test.makeBallotsForPolling(true)
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
            val workflow = PollingWorkflow(auditConfig, test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet=quiet)
            tasks.add(
                PollingWorkflowTask(
                    "genPollingWorkflowMarginPlots margin = $margin",
                    workflow,
                    testCvrs,
                    emptyMap(),
                )
            )
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = ConcurrentTaskRunnerG<WorkflowResult>().run(tasks)

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "PollingMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

    class PollingWorkflowTask(val name: String,
                               val workflow: RlauxWorkflow,
                               val testCvrs: List<Cvr>,
                               val otherParameters: Map<String, Double>,
    ): ConcurrentTaskG<WorkflowResult> {
        override fun name() = name
        override fun run() : WorkflowResult {
            runWorkflow(workflow, testCvrs, quiet=quiet)

            val contestUA = workflow.getContests().first() // theres only one
            val minAssertion = contestUA.minPollingAssertion()!!

            // val N: Int, val margin: Double, val nrounds: Int,
            //                           val usedSamples: Int, val neededSamples: Int,
            //                           val testParameters: Map<String, Double>
            val lastRound = minAssertion.roundResults.last()
            val srt = WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                minAssertion.round,
                lastRound.samplesUsed,
                lastRound.samplesNeeded,
                otherParameters
            )
            return srt
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

        val tasks = mutableListOf<ClcaWorkflowTask>()
        margins.forEach { margin ->
            val marginRange= margin .. margin
            val test = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
            val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, seed=Random.nextLong(), fuzzPct=null, ntrials=10)
            val workflow = ComparisonWorkflow(auditConfig, test.contests, emptyList(), testCvrs, quiet=quiet)

            tasks.add(
                ClcaWorkflowTask(
                    "genClcaWorkflowMarginPlots margin = $margin",
                    workflow,
                    testCvrs,
                    emptyMap(),
                )
            )
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = ConcurrentTaskRunnerG<WorkflowResult>().run(tasks)

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
            runWorkflow(workflow, testCvrs, quiet=quiet)

            val contestUA = workflow.getContests().first() // theres only one
            val minAssertion = contestUA.minComparisonAssertion()!!

            // val N: Int, val margin: Double, val nrounds: Int,
            //                           val usedSamples: Int, val neededSamples: Int,
            //                           val testParameters: Map<String, Double>
            val lastRound = minAssertion.roundResults.last()
            val srt = WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                minAssertion.round,
                lastRound.samplesUsed,
                lastRound.samplesNeeded,
                otherParameters
            )
            return srt
        }
    }

    @Test
    fun genAllWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val underVotePct= 0.0 .. 0.0
        val phantomPct= 0.00 .. 0.00
        val ncontests = 1
        val nbs = 1

        val tasks = mutableListOf<ConcurrentTaskG<WorkflowResult>>()
        margins.forEach { margin ->
            // oneaudit 20%
            val contestOA2 = makeContestOA(margin, N, cvrPercent = .20, 0.0, undervotePercent = 0.0)
            val oaCvrs2 = contestOA2.makeTestCvrs() // one for each ballot, with and without CVRS
            val oneaudit2 = OneAuditWorkflow(
                AuditConfig(AuditType.ONEAUDIT, true, seed=Random.nextLong(), fuzzPct=null, ntrials=10),
                listOf(contestOA2), oaCvrs2, quiet=quiet)
            val otherParameters2 = mapOf("auditType" to 4.0, "cvrPercent" to .50, "skewVotes" to 0.0, "undervotePercent" to 0.0,)
            tasks.add(
                OneAuditWorkflowTask(
                    "genOneAuditWorkflowMarginPlots margin = $margin",
                    oneaudit2,
                    oaCvrs2,
                    otherParameters2,
                )
            )

            // oneaudit 80%
            val contestOA = makeContestOA(margin, N, cvrPercent = .80, 0.0, undervotePercent = 0.0)
            val oaCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
            val oneaudit = OneAuditWorkflow(
                AuditConfig(AuditType.ONEAUDIT, true, seed=Random.nextLong(), fuzzPct=null, ntrials=10),
                listOf(contestOA), oaCvrs, quiet=quiet)
            val otherParameters = mapOf("auditType" to 1.0, "cvrPercent" to .50, "skewVotes" to 0.0, "undervotePercent" to 0.0,)
            tasks.add(
                OneAuditWorkflowTask(
                    "genOneAuditWorkflowMarginPlots margin = $margin",
                    oneaudit,
                    oaCvrs,
                    otherParameters,
                )
            )

            val marginRange= margin .. margin
            val test = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms

            // polling
            val ballots = test.makeBallotsForPolling(true)
            val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed=Random.nextLong(), fuzzPct=null, ntrials=10),
                test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet=quiet)
            tasks.add(
                PollingWorkflowTask(
                    "genPollingWorkflowMarginPlots margin = $margin",
                    polling,
                    testCvrs,
                    mapOf("auditType" to 2.0)
                )
            )

            // clca
            val clca = ComparisonWorkflow(
                AuditConfig(AuditType.CARD_COMPARISON, true, seed=Random.nextLong(), fuzzPct=null, ntrials=10),
                test.contests, emptyList(), testCvrs, quiet=quiet)

            tasks.add(
                ClcaWorkflowTask(
                    "genClcaWorkflowMarginPlots margin = $margin",
                    clca,
                    testCvrs,
                    mapOf("auditType" to 3.0)
                )
            )
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = ConcurrentTaskRunnerG<WorkflowResult>().run(tasks)

        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "AllTypesNoErrors"
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
}