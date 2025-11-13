@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.alpha

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
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.plots.createPctRatio
import org.cryptobiotic.rlauxe.plots.plotPctRatio
import org.cryptobiotic.rlauxe.plots.plotSRTpct
import org.cryptobiotic.rlauxe.plots.plotSRTsamples
import org.cryptobiotic.rlauxe.plots.plotSRTstdev
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.estimate.PollWithoutReplacement
import kotlin.test.Test

// CANDIDATE FOR REFACTOR

// PlotSampleSizes
// DiffMeans, PlotDiffMeans

// create the raw data for showing plots of polling with theta != eta0
// these are 4 dimensional: N, theta, d, diffMean
class CreatePollingDiffMeans {

    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    val N = 50000

    // theta is the true mean
    data class AlphaMartTask(val idx: Int, val N: Int, val theta: Double, val cvrs: List<Cvr>)

    @Test
    fun plotOver() {
        val theta = .51
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrOver = runAlphaMartWithMeanDiff(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = .01,
            silent = false
        ).first()
        println("rrOver = $rrOver")
    }

    // @Test reported mean must be >= .5
    fun plotUnder() {
        val theta = .51
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrUnder = runAlphaMartWithMeanDiff(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.03,
            silent = false
        ).first()
        println("rrUnder = $rrUnder")
    }

    @Test
    fun plotEven() {
        val theta = .51
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrEven = runAlphaMartWithMeanDiff(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.01,
            silent = false
        ).first()
        println("rrEven = $rrEven")
    }

    @Test
    fun createPollingDiffMeansConcurrent() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val theta = listOf(.505, .55, .7)
        // val thetas = theta.map{ theta2margin(it) }
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<AlphaMartTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            thetas.forEach { theta ->
                val cvrs = makeCvrsByExactMean(N, theta)
                tasks.add(AlphaMartTask(taskIdx++, N, theta, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 100

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(10, 50, 250, 1250)

        val writer = SRTcsvWriter("/home/stormy/rla/PollingDiffMeans/SRT$nrepeat.csv")
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
                dlcalcs[d] = calculations.toList()
                writer.writeCalculations(calculations)
                println("$reportedMeanDiff, $d ncalcs = ${calculations.size}")
                totalCalculations += calculations.size

                if (showAllPlots) {
                    plotSRTsamples(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTstdev(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    //plotSRTsuccess(calculations, thetas, nlist, 10, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    //plotSRTsuccess(calculations, thetas, nlist, 20, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    //plotSRTsuccess(calculations, thetas, nlist, 30, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                } else if (showPctPlots) {
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                }

                calculations = mutableListOf<SRT>()
            }

            if (showGeoMeanPlots) {
                val newdlc = createPctRatio(dlcalcs, thetas, nlist)
                plotPctRatio(newdlc, thetas, nlist, reportedMeanDiff)
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    fun calculate(task: AlphaMartTask, nrepeat: Int, d: Int, reportedMeanDiff: Double): SRT {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val rr = runAlphaMartWithMeanDiff(
            task.theta,
            task.cvrs,
            reportedMeanDiff = reportedMeanDiff,
            nrepeat = nrepeat,
            d = d,
            silent = true
        ).first()
        val reportedMean = task.theta + reportedMeanDiff // TODO CHECK THIS
        val sr = rr.makeSRT(reportedMean, reportedMeanDiff)
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.theta}, $sr")
        return sr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<AlphaMartTask>): ReceiveChannel<AlphaMartTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<SRT>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<AlphaMartTask>,
        calculate: (AlphaMartTask) -> SRT?,
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

    fun runAlphaMartWithMeanDiff(
        theta: Double,
        cvrs: List<Cvr>,
        reportedMeanDiff: Double,
        nrepeat: Int,
        d: Int = 500,
        silent: Boolean = true,
    ): List<RunTestRepeatedResult> {
        if (!silent) println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val reportedMean = theta + reportedMeanDiff

        val info = ContestInfo("contest0", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)
        val contestUA = makeContestUAfromCvrs(info, cvrs, isComparison = false)

        val results = mutableListOf<RunTestRepeatedResult>()
        contestUA.pollingAssertions.map { assert ->
            if (!silent && showContests) println("Assertions for Contest ${contestUA.name}")
            if (!silent && showContests) println("  ${assert}")

            val contestUA = ContestUnderAudit(makeContestsFromCvrs(cvrs).first()).addStandardAssertions()
            val cvrSampler = PollWithoutReplacement(contestUA.id,  cvrs, assert.assorter)

            val result = runAlphaMartRepeated(
                drawSample = cvrSampler,
                N = N,
                eta0 = reportedMean, // use the reportedMean for the initial guess
                d = d,
                ntrials = nrepeat,
                withoutReplacement = true,
                upperBound = assert.assorter.upperBound()
            )
            if (!silent) {
                println(result)
                println(
                    "failPct=${result.failPct()} status=${result.status}"
                )
            }
            results.add(result)
        }
        return results
    }
}
