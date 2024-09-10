@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.yield
import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.CompareWithoutReplacement
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.core.makePollingAudit
import org.cryptobiotic.rlauxe.plots.SRT
import kotlin.test.Test

// PlotSampleSizes
// DiffMeans, PlotDiffMeans
// DiffAuditType

// compare ballot polling to card comparison
class DiffAuditType {
    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    val N = 10000

    data class AlphaMartTask(val idx: Int, val N: Int, val theta: Double, val cvrs: List<Cvr>)

    @Test
    fun plotOver() {
        val theta = .505
        val cvrs = makeCvrsByExactTheta(N, theta)

        val rrOver = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = .01,
            silent = false
        )
        println("rrOver")
        println("polling = ${rrOver.first}")
        println("comparison = ${rrOver.second}")
    }

    @Test
    fun plotUnder() {
        val theta = .51
        val cvrs = makeCvrsByExactTheta(N, theta)

        val rrUnder = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.03,
            silent = false
        )
        println("rrUnder")
        println("polling = ${rrUnder.first}")
        println("comparison = ${rrUnder.second}")
    }

    @Test
    fun plotEven() {
        val theta = .51
        val cvrs = makeCvrsByExactTheta(N, theta)

        val rrEven = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.01,
            silent = false
        )
        println("rrEven")
        println("polling = ${rrEven.first}")
        println("comparison = ${rrEven.second}")
    }

    @Test
    fun plotAuditTypesConcurrent() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val theta = listOf(.505, .55, .7)
        // val thetas = theta.map{ theta2margin(it) }
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<AlphaMartTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            thetas.forEach { theta ->
                val cvrs = makeCvrsByExactTheta(N, theta)
                tasks.add(AlphaMartTask(taskIdx++, N, theta, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 1000

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(10, 50, 250, 1250)

        val writer = SRTwriter("/home/stormy/temp/DiffMeans/SRT$nrepeat.csv")
        var totalCalculations = 0

        reportedMeanDiffs.forEach { reportedMeanDiff ->
            val dlcalcs = mutableMapOf<Int, List<SRT>>()
            dl.forEach { d ->
                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                calculate(task, nrepeat, d = d, reportedMeanDiff = reportedMeanDiff)
                            })
                    }

                    // wait for all calculations to be done
                    joinAll(*calcJobs.toTypedArray())
                }
                /*
                dlcalcs[d] = calculations.toList()
                writer.writeCalculations(calculations)
                println("$reportedMeanDiff, $d ncalcs = ${calculations.size}")
                totalCalculations += calculations.size

                if (showAllPlots) {
                    plotSRTsamples(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTstdev(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 10, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 20, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 30, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                } else if (showPctPlots) {
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                }

                 */

                calculations = mutableListOf<Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>>()
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    fun calculate(task: AlphaMartTask, nrepeat: Int, d: Int, reportedMeanDiff: Double): Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult> {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val rr = runDiffAuditTypes(
            task.theta,
            task.cvrs,
            reportedMeanDiff = reportedMeanDiff,
            nrepeat = nrepeat,
            d = d,
            silent = true
        )
        println("${task.idx} (${calculations.size}): ${task.N}, ${task.theta}, \npolling=${rr.first} \ncomparison=${rr.second}")
        return rr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<AlphaMartTask>): ReceiveChannel<AlphaMartTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<AlphaMartTask>,
        calculate: (AlphaMartTask) -> Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            if (calculation != null) {
                mutex.withLock {
                    calculations.add(calculation)
                }
            }
            yield()
        }
    }

    fun runDiffAuditTypes(
        theta: Double,
        cvrs: List<Cvr>,
        reportedMeanDiff: Double,
        nrepeat: Int,
        d: Int = 500,
        silent: Boolean = true,
    ): Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" N=${cvrs.size} theta=$theta withoutReplacement")

        // ignore the "reported winner". just focus on d vs reportedMeanDiff
        val reportedMean = theta + reportedMeanDiff

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        // polling
        val pollingAudit = makePollingAudit(contests = listOf(contest))
        val pollingAssertions = pollingAudit.assertions[contest]
        require(pollingAssertions!!.size == 1)
        val pollingAssertion = pollingAssertions.first()
        val pollingSampler = PollWithoutReplacement(cvrs, pollingAssertion.assorter)
        //println("pollingSampler mean=${pollingSampler.truePopulationMean()} count=${pollingSampler.truePopulationCount()}")
        val pollingResult = runAlphaMartRepeated(
            drawSample = PollWithoutReplacement(cvrs, pollingAssertion.assorter),
            maxSamples = N,
            theta = theta,
            eta0 = reportedMean, // use the reportedMean for the initial guess
            d = d,
            nrepeat = nrepeat,
            withoutReplacement = true,
        )

        // comparison
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertions = compareAudit.assertions[contest]
        require(compareAssertions!!.size == 1)
        val compareAssertion = compareAssertions.first()
        val compareSampler = CompareWithoutReplacement(cvrs, compareAssertion.assorter)
        // println("compareSampler mean=${compareSampler.truePopulationMean()} count=${compareSampler.truePopulationCount()}")

        val compareResult = runAlphaMartRepeated(
            drawSample = CompareWithoutReplacement(cvrs, compareAssertion.assorter),
            maxSamples = N,
            theta = theta,
            eta0 = reportedMean, // use the reportedMean for the initial guess
            d = d,
            nrepeat = nrepeat,
            withoutReplacement = true,
        )

        return Pair(pollingResult, compareResult)
    }
}
