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
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.df
import kotlin.random.Random
import kotlin.test.Test

private val quiet = true

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

class ClcaWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
): WorkflowTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): ClcaWorkflowTask {
        val test = MultiContestTestData(
            1,
            1,
            N,
            marginRange = margin..margin,
            underVotePctRange = underVotePct..underVotePct,
            phantomPctRange = phantomPct..phantomPct
        )
        val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(test.contests, testCvrs, fuzzPct)

        val clca = ComparisonWorkflow(
            AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            test.contests, emptyList(), testCvrs, quiet = quiet
        )
        return ClcaWorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 3.0)
        )
    }
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
            lastRound.status,
            minAssertion.round.toDouble(),
            lastRound.samplesUsed.toDouble(),
            lastRound.samplesNeeded.toDouble(),
            otherParameters
        )
        return srt
    }
}

class PollingWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
    ) : WorkflowTaskGenerator {
    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): PollingWorkflowTask {
        val test = MultiContestTestData(
            1,
            1,
            N,
            marginRange = margin..margin,
            underVotePctRange = underVotePct..underVotePct,
            phantomPctRange = phantomPct..phantomPct
        )
        val testCvrs = test.makeCvrsFromContests() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(test.contests, testCvrs, fuzzPct)

        val ballots = test.makeBallotsForPolling(true)
        val polling = PollingWorkflow(
            AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            test.contests, BallotManifest(ballots, test.ballotStyles), N, quiet = quiet
        )

        return PollingWorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            polling,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 2.0)
        )
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
}

class OneAuditWorkflowTaskGenerator(
    val N: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Double>,
) : WorkflowTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): OneAuditWorkflowTask {
        val contestOA2 = makeContestOA(margin, N, cvrPercent = cvrPercent, phantomPct, undervotePercent = underVotePct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

        val oneaudit = OneAuditWorkflow(
            AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            listOf(contestOA2), oaCvrs, quiet = quiet
        )
        return OneAuditWorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            oneaudit,
            oaMvrs,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to fuzzPct, "auditType" to 1.0)
        )
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
}


class TestWorkflowTasks {
    val nruns = 10

    // class ClcaWorkflowTaskGenerator(
    //    val N: Int, // including undervotes but not phantoms
    //    val margin: Double,
    //    val underVotePct: Double,
    //    val phantomPct: Double,
    //    val fuzzPct: Double,
    //)
    @Test
    fun genClcaWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val workflowGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, workflowGenerator))
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "ClcaMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

    // class OneAuditWorkflowTaskGenerator(
    //    val N: Int, // including undervotes but not phantoms
    //    val margin: Double,
    //    val underVotePct: Double,
    //    val phantomPct: Double,
    //    val cvrPercent: Double,
    //    val fuzzPct: Double,
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
                val workflowGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    mapOf("nruns" to nruns.toDouble()))
                tasks.add(RepeatedTaskRunner(nruns, workflowGenerator))
            }
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "OneAuditMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.parameters["cvrPercent"]!!.toDouble()) }
    }

    // class PollingWorkflowTaskGenerator(
    //    val N: Int, // including undervotes but not phantoms
    //    val margin: Double,
    //    val underVotePct: Double,
    //    val phantomPct: Double,
    //    val fuzzPct: Double,
    @Test
    fun genPollingWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val workflowGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, workflowGenerator))
        }

        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "PollingMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }
}
