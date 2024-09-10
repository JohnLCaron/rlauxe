@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.core

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
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.test.Test

import kotlin.collections.getOrPut
import kotlin.math.min
import kotlin.text.format

// PlotSampleSizes
// DiffMeans, PlotDiffMeans

class DiffMeans {
    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    val N = 50000

    data class AlphaMartTask(val idx: Int, val N: Int, val theta: Double, val cvrs: List<Cvr>)

    @Test
    fun plotOver() {
        val theta = .51
        val cvrs = makeCvrsByExactTheta(N, theta)

        val rrOver = runAlphaMartWithMeanDiff(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = .01,
            silent = false
        ).first()
        println("rrOver = $rrOver")
    }

    @Test
    fun plotUnder() {
        val theta = .51
        val cvrs = makeCvrsByExactTheta(N, theta)

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
        val cvrs = makeCvrsByExactTheta(N, theta)

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
    fun plotDiffMeansConcurrent() {
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
                                calculate(task, nrepeat, d=d, reportedMeanDiff=reportedMeanDiff)
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
                    plotSRTsuccess(calculations, thetas, nlist, 10, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 20, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 30, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                } else if (showPctPlots) {
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                }

                calculations = mutableListOf<SRT>()
            }

            if (showGeoMeanPlots) {
                val newdlc = creatSRpctRatio(dlcalcs, thetas, nlist)
                newdlc.forEach { dl, sps ->
                    plotSRTpct(
                        sps,
                        thetas,
                        nlist,
                        "pct/pctMin d=$dl, reportedMeanDiff=$reportedMeanDiff",
                        isInt = false
                    )
                    val x = sps.map { it.pct }
                    println("geometric mean = ${geometricMean(x)}")
                }
                println("===============================================")
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    // construct new dlcalcs replacing pct with ratio = pct/pctMin
    fun creatSRpctRatio(dlcalcs: Map<Int, List<SRT>>, thetas: List<Double>, ns: List<Int>): Map<Int, List<SRT>> {
        val newdlc = mutableMapOf<Int, MutableList<SRT>>() // N, m -> fld
        // val newsrs = mutableListOf<SRT>()
        val dlmapPct = dlcalcs.mapValues { entry -> entry.key to makeMapFromSRTs(entry.value, thetas, ns) { it.pct} }.toMap() // dl -> N, m -> pct
        // makeSRmap(srs: List<SRT>, extract: (SRT) -> Double): Map<Int, Map<Double, Double>>
        thetas.forEach { margin ->
            ns.forEach { N ->
                var pctMin = 100.0
                var dMin = 0
                dlmapPct.forEach { entry ->
                    val (_, mmap: Map<Int, Map<Double, Double>>) = entry.value
                    val dmap = mmap[N]
                    val pct = if (dmap != null) dmap[margin] ?: 100.0 else 100.0
                    pctMin = min(pct, pctMin)
                }
                dlmapPct.forEach { entry ->
                    val (d, mmap) = entry.value
                    val dmap = mmap[N]
                    val pct = if (dmap != null) extractPct(dmap[margin]) else 100.0
                    val ratio = pct / pctMin
                    // data class SRT(val N: Int, val margin: Double, val nsamples: Double, val pct: Double, val stddev: Double, val hist: Histogram?)
                    val sr = SRT(N, margin, 0.0, ratio, 0.0, null, 0.0, dMin)
                    val newsrs = newdlc.getOrPut(d) { mutableListOf() }
                    newsrs.add(sr)
                }
            }
        }
        return newdlc
    }

    fun extractPct(pct: Double?): Double {
        if (pct == null) return 100.0
        if (pct < 0) return 100.0
        return pct
    }

    fun calculate(task: AlphaMartTask, nrepeat: Int, d: Int, reportedMeanDiff: Double): SRT {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val rr = runAlphaMartWithMeanDiff(task.theta, task.cvrs, reportedMeanDiff=reportedMeanDiff, nrepeat = nrepeat, d = d, silent = true).first()
        val sr = makeSRT(task.N, task.theta, rr, reportedMeanDiff, d)
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.theta}, ${rr.eta0}, $sr")
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
        silent: Boolean = true
    ): List<AlphaMartRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" N=${cvrs.size} theta=$theta withoutReplacement")

        // ignore the "reported winner". just focus on d vs reportedMeanDiff
        val reportedMean = theta + reportedMeanDiff
        // val reportedWinner = if (reportedMean > .5) 0 else 1 // TODO tie ??
        // val reportedWinnerMean = if (reportedMean > .5) reportedMean else (1.0 - (theta + reportedMeanDiff))

        val contest = AuditContest("contest0", 0, listOf(0,1), listOf(0))
        val audit = makePollingAudit(contests = listOf(contest))

        val results = mutableListOf<AlphaMartRepeatedResult>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach { assert ->
                if (!silent && showContests) println("  ${assert}")

                val cvrSampler = PollWithoutReplacement(cvrs, assert.assorter)

                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    theta=theta,
                    eta0 = reportedMean, // use the reportedMean for the initial guess
                    d = d,
                    nrepeat = nrepeat,
                    withoutReplacement = true,
                )
                if (!silent) {
                    println(result)
                    println(
                        "truePopulationCount=${ff.format(cvrSampler.truePopulationCount())} truePopulationMean=${
                            ff.format(cvrSampler.truePopulationMean())
                        } failPct=${result.failPct} status=${result.status}"
                    )
                }
                results.add(result)
            }
        }
        return results
    }
}

class SRTwriter(filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()

    init {
        writer.write("N, theta, nsamples, stddev, reportedMeanDiff, d\n")
    }

    fun writeCalculations(calculations: List<SRT>) {
        calculations.forEach {
            writer.write(toCSV(it))
        }
    }

    // data class SRT(val N: Int, val theta: Double, val nsamples: Double, val pct: Double, val stddev: Double,
    // val hist: Histogram?, val reportedMeanDiff: Double, val d: Int)
    fun toCSV(srt: SRT) = buildString {
        append("${srt.N}, ${srt.theta}, ${srt.nsamples}, ${srt.stddev}, ${srt.reportedMeanDiff}, ${srt.d}\n")
    }

    fun close() {
        writer.close()
    }
}