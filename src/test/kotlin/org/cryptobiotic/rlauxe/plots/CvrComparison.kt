@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.plots

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
import org.cryptobiotic.rlauxe.core.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.integration.AlphaMartRepeatedResult
import org.cryptobiotic.rlauxe.integration.createPctRatio
import org.cryptobiotic.rlauxe.core.makeCvrsByExactTheta
import org.cryptobiotic.rlauxe.integration.plotPctRatio
import org.cryptobiotic.rlauxe.integration.runAlphaMartRepeated
import kotlin.collections.first
import kotlin.test.Test

// create the raw data for showing plots of comparison with theta != eta0
// these are 4 dimensional: N, theta, d, diffMean
class CvrComparison {
    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    data class ComparisonTask(val idx: Int, val N: Int, val cvrMean: Double, val cvrs: List<Cvr>)

    @Test
    fun plotOver() {
        val cvrMean = .51
        val cvrs = makeCvrsByExactTheta(10000, cvrMean)

        val rrOver = runComparisonWithMeanDiff(
            cvrMean,
            cvrs,
            nrepeat = 100,
            cvrMeanDiff = -.01,
            silent = false
        )
        println("rrOver = $rrOver")
    }

    @Test
    fun plotUnder() {
        val cvrMean = .51
        val cvrs = makeCvrsByExactTheta(10000, cvrMean)

        val rrUnder = runComparisonWithMeanDiff(
            cvrMean,
            cvrs,
            nrepeat = 100,
            cvrMeanDiff = .03,
            silent = false
        )
        println("rrUnder = $rrUnder")
    }

    @Test
    fun plotEven() {
        val cvrMean = .51
        val cvrs = makeCvrsByExactTheta(10000, cvrMean)

        val rrEven = runComparisonWithMeanDiff(
            cvrMean,
            cvrs,
            nrepeat = 100,
            cvrMeanDiff = .01,
            silent = false
        )
        println("rrEven = $rrEven")
    }

    @Test
    fun cvrComparisonConcurrent() {
        val cvrMeans = listOf(.51) // listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(10000) // listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<ComparisonTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactTheta(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 10

        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(10, 50, 250, 1250)

        val writer = SRTwriter("/home/stormy/temp/CvrComparison/SRT$nrepeat.csv")
        var totalCalculations = 0

        cvrMeanDiffs.forEach { cvrMeanDiff ->
            val dlcalcs = mutableMapOf<Int, List<SRT>>()
            dl.forEach { d ->
                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                calculate(task, nrepeat, d=d, cvrMeanDiff=cvrMeanDiff)
                            })
                    }

                    // wait for all calculations to be done
                    joinAll(*calcJobs.toTypedArray())
                }
                dlcalcs[d] = calculations.toList()
                writer.writeCalculations(calculations)
                println(" cvrMeanDiff=$cvrMeanDiff, $d ncalcs = ${calculations.size}")
                totalCalculations += calculations.size

                if (showAllPlots) {
                    plotSRTsamples(calculations, cvrMeans, nlist, "d=$d cvrMeanDiff=$cvrMeanDiff")
                    plotSRTpct(calculations, cvrMeans, nlist, "d=$d cvrMeanDiff=$cvrMeanDiff")
                    plotSRTstdev(calculations, cvrMeans, nlist, "d=$d cvrMeanDiff=$cvrMeanDiff")
                    plotSRTsuccess(calculations, cvrMeans, nlist, 10, nrepeat, "d=$d cvrMeanDiff=$cvrMeanDiff")
                    plotSRTsuccess(calculations, cvrMeans, nlist, 20, nrepeat, "d=$d cvrMeanDiff=$cvrMeanDiff")
                    plotSRTsuccess(calculations, cvrMeans, nlist, 30, nrepeat, "d=$d cvrMeanDiff=$cvrMeanDiff")
                } else if (showPctPlots) {
                    plotSRTpct(calculations, cvrMeans, nlist, "d=$d cvrMeanDiff=$cvrMeanDiff")
                }

                calculations = mutableListOf<SRT>()
            }

            if (showGeoMeanPlots) {
                val newdlc = createPctRatio(dlcalcs, cvrMeans, nlist)
                plotPctRatio(newdlc, cvrMeans, nlist, cvrMeanDiff)
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    fun calculate(task: ComparisonTask, nrepeat: Int, d: Int, cvrMeanDiff: Double): SRT {
        // if (margin2theta(task.margin) + cvrMeanDiff <= .5) return null
        val rr = runComparisonWithMeanDiff(task.cvrMean, task.cvrs, cvrMeanDiff=cvrMeanDiff, nrepeat = nrepeat, d = d, silent = true)
        val sr = makeSRT(task.N, task.cvrMean, rr, cvrMeanDiff, d)
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, ${rr.eta0}, $sr")
        return sr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<ComparisonTask>): ReceiveChannel<ComparisonTask> =
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
        input: ReceiveChannel<ComparisonTask>,
        calculate: (ComparisonTask) -> SRT?,
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

    fun runComparisonWithMeanDiff(
        cvrMean: Double,
        cvrs: List<Cvr>,
        cvrMeanDiff: Double,
        nrepeat: Int,
        d: Int = 100,
        silent: Boolean = true
    ): AlphaMartRepeatedResult {
        val N = cvrs.size
        val theta = cvrMean - cvrMeanDiff // the true mean
        if (!silent) println(" N=${cvrs.size} theta=$theta d=$d diffMean=$cvrMeanDiff")
        
        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertions = compareAudit.assertions[contest]
        require(compareAssertions!!.size == 1)
        val compareAssertion = compareAssertions.first()
        val sampleWithErrors = ComparisonWithErrors(cvrs, compareAssertion.assorter, theta)

        val compareResult = runAlphaMartRepeated(
            drawSample = sampleWithErrors,
            maxSamples = N,
            theta = theta,
            eta0 = 20.0, 
            d = d,
            nrepeat = nrepeat,
            withoutReplacement = true,
        )
        return compareResult
    }
}
