package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
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

    override fun generateNewTask(): WorkflowTask {
        val sim = ContestSimulation.make2wayTestContest(Nc=N, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, fuzzPct)

        val clca = ComparisonWorkflow(
            AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            listOf(sim.contest), emptyList(), testCvrs, quiet = quiet
        )
        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 3.0)
        )
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

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {

        val sim = ContestSimulation.make2wayTestContest(Nc=N, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, fuzzPct)
        val ballotManifest = sim.makeBallotManifest(true)

        val polling = PollingWorkflow(
                AuditConfig(AuditType.POLLING, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
                listOf(sim.contest), ballotManifest, N, quiet = quiet
            )

        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            polling,
            testMvrs,
            parameters + mapOf("fuzzPct" to fuzzPct, "auditType" to 2.0)
        )
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

    override fun generateNewTask(): WorkflowTask {
        val contestOA2 = makeContestOA(margin, N, cvrPercent = cvrPercent, phantomPct, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

        val oneaudit = OneAuditWorkflow(
            AuditConfig(AuditType.ONEAUDIT, true, seed = Random.nextLong(), ntrials = 10, fuzzPct = fuzzPct),
            listOf(contestOA2), oaCvrs, quiet = quiet
        )
        return WorkflowTask(
            "genAuditWithErrorsPlots fuzzPct = $fuzzPct",
            oneaudit,
            oaMvrs,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to fuzzPct, "auditType" to 1.0)
        )
    }
}

class WorkflowTask(
    val name: String,
    val workflow: RlauxWorkflow,
    val testCvrs: List<Cvr>,
    val otherParameters: Map<String, Double>,
) : ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run(): WorkflowResult {
        runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minAssertion()!!

        return if (minAssertion.roundResults.isEmpty()) {
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                TestH0Status.FailPct,
                0.0, 0.0, 0.0,
                otherParameters
            )
        } else {
            val lastRound = minAssertion.roundResults.last()
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                lastRound.samplesNeeded.toDouble(),
                otherParameters
            )
        }
    }
}

fun runRepeatedTaskAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val failures = runs.count{ it.status.fail }
    val failPct = if (runs.isEmpty()) 0.0 else 100.0 * failures / runs.size
    val successRuns = runs.filter { !it.status.fail }

    return if (runs.isEmpty()) {
        WorkflowResult(
            0,
            0.0,
            TestH0Status.AllFailPct,
            0.0, 0.0, 0.0,
            emptyMap()
        )
    } else if (successRuns.isEmpty()) {
        val first = runs.first()
        WorkflowResult(
            first.N,
            first.margin,
            TestH0Status.FailPct,
            0.0, first.N.toDouble(), first.N.toDouble(),
            first.parameters,
        )
    } else {
        val first = successRuns.first()
        WorkflowResult(
            first.N,
            first.margin,
            first.status,
            successRuns.map { it.nrounds }.average(),
            successRuns.map { it.samplesUsed }.average(),
            successRuns.map { it.samplesNeeded }.average(),
            first.parameters,
            failPct,
        )
    }
}

class TestGenWorkflowTasks {
    val nruns = 10

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

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedTaskAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
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

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val workflowGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, workflowGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedTaskAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "ClcaMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

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

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedTaskAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "OneAuditMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.parameters["cvrPercent"]!!.toDouble()) }
    }
}
