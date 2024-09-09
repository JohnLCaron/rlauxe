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
import kotlin.test.Test

import kotlin.collections.getOrPut
import kotlin.math.min
import kotlin.math.sqrt

val showCalculation = false
val showContests = false
val showAllPlots = true
val showGeoMeanPlots = true

data class SR(val N: Int, val margin: Double, val nsamples: Double, val pct: Double, val stddev: Double, val hist: Histogram?)

fun plotSRSnVt(srs: List<SR>, margins: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "number votes sampled: " + title
    plotSRS(srs, margins, ns, utitle, true) { it.nsamples }
}

fun plotSamplePctnVt(srs: List<SR>, margins: List<Double>, ns: List<Int>, title: String = "", isInt:Boolean=true) {
    val utitle = "pct votes sampled: " + title
    plotSRS(srs, margins, ns, utitle, isInt) { it.pct }
}

fun plotStddevSnVt(srs: List<SR>, margins: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "stddev votes sampled: " + title
    plotSRS(srs, margins, ns, utitle, true) { it.stddev }
}

fun plotSuccesses(srs: List<SR>, margins: List<Double>, ns: List<Int>, sampleMaxPct: Int, nrepeat: Int, title: String = "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, margins, ns, utitle, true) {
        val cumul = it.hist!!.cumul(sampleMaxPct)
        (100.0 * cumul) / nrepeat
    }
}

fun plotSRS(srs: List<SR>, margins: List<Double>, ns: List<Int>, title: String, isInt: Boolean, extract: (SR) -> Double) {
    println()
    println(title)
    print("     N, ")
    val theta = margins.sorted().map { .5 + it * .5 }
    theta.forEach { print("${"%6.3f".format(it)}, ") }
    println()

    val mmap = makeMapFromSRs(srs, margins, ns, extract)

    mmap.forEach { dkey, dmap ->
        print("${"%6d".format(dkey)}, ")
        dmap.toSortedMap().forEach { nkey, fld ->
            if (isInt)
                print("${"%6d".format(fld.toInt())}, ")
            else
                print("${"%6.3f".format(fld)}, ")
        }
        println()
    }
}

fun makeMapFromSRs(srs: List<SR>, margins: List<Double>, ns: List<Int>, extract: (SR) -> Double): Map<Int, Map<Double, Double>> {
    val mmap = mutableMapOf<Int, MutableMap<Double, Double>>() // N, m -> fld

    // fill with all the maps initialized to -1
    ns.forEach { N ->
        mmap[N] = mutableMapOf()
        val nmap = mmap[N]!!
        margins.forEach { margin ->
            nmap[margin] = -1.0
        }
    }

    srs.forEach {
        val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
        dmap[it.margin] = extract(it)
    }

    return mmap.toSortedMap()
}

fun makeSR(N: Int, margin: Double, rr: AlphaMartRepeatedResult): SR {
    val (sampleCountAvg, sampleCountVar, _) = rr.nsamplesNeeded.result()
    val pct = (100.0 * sampleCountAvg / N)
    return SR(N, margin, sampleCountAvg, pct, sqrt(sampleCountVar), rr.hist)
}

data class CalcTask(val idx: Int, val N: Int, val margin: Double, val cvrs: List<Cvr>)


class PlotSampleSizes {

    @Test
    fun plotSampleSizeConcurrent() {
        val theta = listOf(.55) // .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val theta = listOf(.505, .55, .7)
        val margins = theta.map{ theta2margin(it) }
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<CalcTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMargin(N, margin)
                tasks.add(CalcTask(taskIdx++, N, margin, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 100

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0) // , -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(500)

        reportedMeanDiffs.forEach { reportedMeanDiff ->
            val dlcalcs = mutableMapOf<Int, List<SR>>()
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
                // plotSamplePctnVt(calculations, margins, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")

                if (showAllPlots) {
                    plotSRSnVt(calculations, margins, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSamplePctnVt(calculations, margins, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotStddevSnVt(calculations, margins, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSuccesses(calculations, margins, nlist, 10, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSuccesses(calculations, margins, nlist, 20, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSuccesses(calculations, margins, nlist, 30, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                }

                calculations = mutableListOf<SR>()
            }

            if (showGeoMeanPlots) {
                val newdlc = creatSRpctRatio(dlcalcs, margins, nlist)
                newdlc.forEach { dl, sps ->
                    plotSamplePctnVt(
                        sps,
                        margins,
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
    }

    // construct new dlcalcs replacing pct with ratio = pct/pctMin
    fun creatSRpctRatio(dlcalcs: Map<Int, List<SR>>, margins: List<Double>, ns: List<Int>): Map<Int, List<SR>> {
        val newdlc = mutableMapOf<Int, MutableList<SR>>() // N, m -> fld
        // val newsrs = mutableListOf<SR>()
        val dlmapPct = dlcalcs.mapValues { entry -> entry.key to makeMapFromSRs(entry.value, margins, ns) { it.pct} }.toMap() // dl -> N, m -> pct
        // makeSRmap(srs: List<SR>, extract: (SR) -> Double): Map<Int, Map<Double, Double>>
        margins.forEach { margin ->
            ns.forEach { N ->
                var pctMin = 100.0
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
                    // data class SR(val N: Int, val margin: Double, val nsamples: Double, val pct: Double, val stddev: Double, val hist: Histogram?)
                    val sr = SR(N, margin, 0.0, ratio, 0.0, null)
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

    fun calculate(task: CalcTask, nrepeat: Int, d: Int, reportedMeanDiff: Double): SR? {
        if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val rr = runAlphaMartWithMeanDifference(task.margin, task.cvrs, nrepeat = nrepeat, d = d, reportedMeanDiff=reportedMeanDiff, silent = true).first()
        val sr = makeSR(task.N, task.margin, rr)
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.margin}, ${rr.eta0}, $sr")
        return sr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<CalcTask>): ReceiveChannel<CalcTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<SR>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<CalcTask>,
        calculate: (CalcTask) -> SR?,
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

    fun runAlphaMartWithMeanDifference(
        margin: Double,
        cvrs: List<Cvr>,
        d: Int = 100,
        reportedMeanDiff: Double,
        nrepeat: Int,
        silent: Boolean = true
    ): List<AlphaMartRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" N=${cvrs.size} margin=$margin withoutReplacement")
        val theta = margin2theta(margin)

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        if (!silent && showContests) {
            votes.forEach { key, cands ->
                println("contest ${key} ")
                cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
            }
        }

        // make contests from cvrs
        val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        if (!silent && showContests) {
            println("Contests")
            contests.forEach { println("  ${it}") }
        }

        // Polling Audit
        val audit = makePollingAudit(contests = contests)

        val results = mutableListOf<AlphaMartRepeatedResult>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach { assert ->
                if (!silent && showContests) println("  ${assert}")

                val assortValues = cvrs.map { cvr -> assert.assorter.assort(cvr) }
                val assortMean = assortValues.average()
                val reportedMean = theta + reportedMeanDiff // reportedMean != true mean
                // require(assortMean == theta)

                val cvrSampler = PollWithoutReplacement(cvrs, assert.assorter)

                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    theta = theta,
                    eta0 = reportedMean,       // use the reportedMean for the initial guess
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

    /* single threaded
    @Test
    fun testSampleSize() {
        val margins = listOf(.4, .3, .2, .15, .1, .08, .06, .04, .02, .01) // winning percent: 70, 65, 60, 57.5, 55, 54, 53, 52, 51, 50.5
        val Nlist = listOf(1000, 5000, 10000, 20000, 50000, 100000)
        val srs = mutableListOf<SR>()
        val show = false

        if (show) println("N, margin, eta0, nSample, pctVotes")

        Nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMargin(N, margin)
                val resultWithout = testSampleSize(margin, cvrs, silent = true).first()

                val pct = (100.0 * resultWithout.sampleCountAvg / N)
                if (show) println("${cvrs.size}, $margin, ${resultWithout.eta0}, ${resultWithout.sampleCountAvg}, ${pct}")
                srs.add(SR(N, margin, resultWithout.sampleCountAvg, pct))
            }
        }
        showSRSnVt(srs, margins)
        // showSRSbyNBallots(srs, Nlist, margins)
    }

     */
}

// 9/01/2024
// compares well with table 3 of ALPHA
// eta0 = theta, no divergence of sample from true. 1000 repetitions
//
// nvotes sampled vs theta = winning percent
//     N,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
//  1000,    897,    726,    569,    446,    340,    201,    128,     60,     36,
//  5000,   3447,   1948,   1223,    799,    527,    256,    145,     68,     38,
// 10000,   5665,   2737,   1430,    871,    549,    266,    152,     68,     38,
// 20000,   8456,   3306,   1546,    926,    590,    261,    154,     65,     38,
// 50000,  12225,   3688,   1686,    994,    617,    263,    155,     67,     37,
//
// stddev sampled vs theta = winning percent
//     N,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
//  1000, 119.444, 176.939, 195.837, 176.534, 153.460, 110.204, 78.946, 40.537, 24.501,
//  5000, 1008.455, 893.249, 669.987, 478.499, 347.139, 176.844, 101.661, 52.668, 28.712,
// 10000, 2056.201, 1425.911, 891.215, 583.694, 381.797, 199.165, 113.188, 52.029, 27.933,
// 20000, 3751.976, 2124.064, 1051.194, 656.632, 449.989, 190.791, 123.333, 47.084, 28.173,
// 50000, 6873.319, 2708.147, 1274.291, 740.712, 475.265, 194.538, 130.865, 51.086, 26.439,