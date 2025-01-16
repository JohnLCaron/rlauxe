package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.GenAuditSampleSizePlots.*
import kotlin.random.Random
import kotlin.test.Test

private val quiet = true
private val nruns = 200  // number of times to run workflow

class GenOneAuditVsPolling {

    @Test
    fun genOneAuditVsPolling() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val cvrPercents = listOf(0.0, 0.5, 1.0)

        val underVotePct = 0.0..0.0
        val phantomPct = 0.00..0.00

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->

            cvrPercents.forEach { cvrPercent ->
                val contestOA2 = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
                val oaCvrs2 = contestOA2.makeTestCvrs() // one for each ballot, with and without CVRS
                val oneaudit2 = OneAuditWorkflow(
                    AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                    listOf(contestOA2), oaCvrs2, quiet = quiet
                )
                val otherParameters2 = mapOf(
                    "auditType" to 1.0,
                    "nruns" to nruns.toDouble(),
                    "cvrPercent" to cvrPercent,
                    "skewVotes" to 0.0,
                    "undervotePercent" to 0.0,
                )
                tasks.add(
                    RepeatedTaskRunner(
                        nruns,
                        OneAuditWorkflowTask(
                            "genOneAuditWorkflowMarginPlots margin = $margin",
                            oneaudit2,
                            oaCvrs2,
                            otherParameters2,
                        )
                    )
                )
            }

            // polling
            val marginRange = margin..margin
            val test = MultiContestTestData(
                1,
                1,
                N,
                marginRange = marginRange,
                underVotePctRange = underVotePct,
                phantomPctRange = phantomPct
            )
            val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
            val ballots = test.makeBallotsForPolling(true)
            val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), fuzzPct = null, ntrials = 10),
                test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet = quiet
            )
            tasks.add(
                RepeatedTaskRunner(
                    nruns,
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
        val filename = "OneAuditVsPolling"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "auditType") {
            val cvrPercentR = it.parameters["cvrPercent"] ?: 0.0
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(cvrPercentR, 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    @Test
    fun plotLinearOrLog() {
        val dirName = "/home/stormy/temp/oneaudit"
        val filename = "OneAuditVsPolling"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "auditType", useLog=false) {
            val cvrPercentR = it.parameters["cvrPercent"] ?: 0.0
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(cvrPercentR, 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }
}