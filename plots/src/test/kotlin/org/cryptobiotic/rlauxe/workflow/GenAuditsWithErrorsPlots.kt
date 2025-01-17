package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import kotlin.random.Random
import kotlin.test.Test

class GenAuditsWithErrorsPlots {
    private val quiet = true
    private val nruns = 500  // number of times to run workflow

    @Test
    fun genAuditWithErrorsPlots() {
        val N = 50000
        val margin = .04
        val cvrPercent = .50
        val fuzzPcts = listOf(.005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10, .11, .12)

        val underVotePct = 0.0..0.0
        val phantomPct = 0.00..0.00

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            // oneaudit
            val contestOA2 = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
            val oaCvrs = contestOA2.makeTestCvrs()
            val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

            val oneaudit = OneAuditWorkflow(
                AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), ntrials = 10, fuzzPct=fuzzPct),
                listOf(contestOA2), oaCvrs, quiet = quiet
            )
            val otherParameters2 = mapOf(
                "auditType" to 1.0,
                "nruns" to nruns.toDouble(),
                "cvrPercent" to cvrPercent,
                "fuzzPct" to fuzzPct,
                "undervotePercent" to 0.0,
            )
            tasks.add(
                RepeatedTaskRunner(
                    nruns,
                    OneAuditWorkflowTask(
                        "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
                        oneaudit,
                        oaMvrs,
                        otherParameters2,
                    )
                )
            )

            // polling and clca
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
            val testMvrs = makeFuzzedCvrsFrom(test.contests, testCvrs, fuzzPct)

            // polling
            val ballots = test.makeBallotsForPolling(true)
            val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), ntrials = 10, fuzzPct=fuzzPct),
                test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet = quiet
            )
            tasks.add(
                RepeatedTaskRunner(
                    nruns,
                    PollingWorkflowTask(
                        "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
                        polling,
                        testMvrs,
                        mapOf("auditType" to 2.0, "nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
                    )
                )
            )

            // clca
            val clca = ComparisonWorkflow(
                AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10, fuzzPct=fuzzPct),
                test.contests, emptyList(), testCvrs, quiet = quiet
            )
            tasks.add(
                RepeatedTaskRunner(
                    nruns,
                    ClcaWorkflowTask(
                        "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
                        clca,
                        testMvrs,
                        mapOf("auditType" to 3.0, "nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
                    )
                )
            )
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)
    }

    @Test
    fun makePlots() {
        showSampleSizesVsErrorPct(true)
        showSampleSizesVsErrorPct(false)
        showFailuresVsErrorPct()
        showNroundsVsErrorPct()
    }

    fun showSampleSizesVsErrorPct(useLog: Boolean) {
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsErrorPct(results, "auditType", useLog=useLog) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    fun showFailuresVsErrorPct() {
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showFailuresVsErrorPct(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    fun showNroundsVsErrorPct() {
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showNroundsVsErrorPct(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }
}