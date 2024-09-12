package org.cryptobiotic.rlauxe.plots

import kotlin.test.Test

import kotlin.collections.getOrPut
import kotlin.text.format

// read raw data and make csv plots of polling with theta != eta0
class PlotCvrComparison {
    val nrepeat = 10
    val reader = SRTreader("/home/stormy/temp/CvrComparison/SRT$nrepeat.csv")

    // These are N vs theta plots for various values of d and MeanDiff
    @Test
    fun plotNTheta() {
        val cvrMeans = listOf(.51) // listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(10000) // listOf(50000, 20000, 10000, 5000, 1000)

        val allSrts = reader.readCalculations()
        println(" number of SRTs = ${allSrts.size}")

        val ddiffMap = makeDDiffMap(allSrts)
        println(" number of ddiffs = ${ddiffMap.size}")

        ddiffMap.forEach { (ddiff, srts) ->
            plotSRTsamples(srts, cvrMeans, nlist, "d=${ddiff.d} diffMean=${ddiff.diffMean} ")
            plotSRTpct(srts, cvrMeans, nlist, "d=${ddiff.d} diffMean=${ddiff.diffMean} ")
            // plotSRTsuccess(srts, reportedMeanDiffs, dlist, 30, nrepeat, "d=${ddiff.d} diffMean=${ddiff.diffMean} ")
            println()
        }
    }

    data class DDiff(val d: Int, val diffMean: Double)

    private val dDiffComparator = Comparator<DDiff> { o1, o2 ->
        when {
            (o1 == null && o2 == null) -> 0
            (o1 == null) -> -1
            else -> {
                val c = o1.d.compareTo(o2.d)
                when (c) {
                    -1, 1 -> c
                    else -> o1.diffMean.compareTo(o2.diffMean)
                }
            }
        }
    }

    fun makeDDiffMap(srs: List<SRT>): Map<DDiff, List<SRT>> {
        val mmap = mutableMapOf<DDiff, MutableList<SRT>>()
        srs.forEach {
            val DD = DDiff(it.d, it.reportedMeanDiff)
            val dmap : MutableList<SRT> = mmap.getOrPut(DD) { mutableListOf() }
            dmap.add(it)
        }
        return mmap.toSortedMap(dDiffComparator)
    }

    ////////////////////////////////////////////////////
    // These are d vs MeanDiff plots for various values of N, theta
    @Test
    fun plotDvsMeanDiff() {
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)

        val allSrts = reader.readCalculations()
        println(" number of SRTs = ${allSrts.size}")
        val nThetaMap = makeNthetaMap(allSrts)
        println(" number of nthetas = ${nThetaMap.size}")

        nThetaMap.forEach { (ntheta, srts) ->
            plotNTsample(srts, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
            plotNTpct(srts, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
            // plotNTsuccess(srts, reportedMeanDiffs, dlist, 30, nrepeat, "N=${ntheta.N} theta=${ntheta.theta} ")
            println()
        }

    }

    data class Ntheta(val N: Int, val theta: Double)

    private val nthetaComparator: Comparator<Ntheta> = Comparator<Ntheta> { o1, o2 ->
        when {
            (o1 == null && o2 == null) -> 0
            (o1 == null) -> -1
            else -> {
                val c = o1.theta.compareTo(o2.theta)
                when (c) {
                    -1, 1 -> c
                    else -> o1.N.compareTo(o2.N)
                }
            }
        }
    }

    fun makeNthetaMap(srs: List<SRT>): Map<Ntheta, List<SRT>> {
        val mmap = mutableMapOf<Ntheta, MutableList<SRT>>()
        srs.forEach {
            val DD = Ntheta(it.N, it.theta)
            val dmap : MutableList<SRT> = mmap.getOrPut(DD) { mutableListOf() }
            dmap.add(it)
        }
        return mmap.toSortedMap(nthetaComparator)
    }

    // TODO all below replace with Plots

    fun plotNTsample(srs: List<SRT>, meanDiffs: List<Double>, ds: List<Int>, title: String = "") {
        val utitle = "votes sampled: " + title
        plotNT(srs, meanDiffs, ds, utitle, true) { it.nsamples }
    }

    fun plotNTpct(srs: List<SRT>, meanDiffs: List<Double>, ds: List<Int>, title: String = "") {
        val utitle = "pct votes sampled: " + title
        plotNT(srs, meanDiffs, ds, utitle, false) { 100.0 * it.nsamples / it.N }
    }

    fun plotNTsuccess(srs: List<SRT>, meanDiffs: List<Double>, ds: List<Int>, sampleMaxPct: Int, nrepeat: Int, title: String = "") {
        val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
        plotSRS(srs, meanDiffs, ds, utitle, true) {
            val cumul = it.hist!!.cumul(sampleMaxPct)
            (100.0 * cumul) / nrepeat
        }
    }

    fun plotNT(srs: List<SRT>, meanDiffs: List<Double>, ds: List<Int>, title: String, isInt: Boolean, extract: (SRT) -> Double) {
        println()
        println(title)
        print("     d, ")
        val meanDiff = meanDiffs.sorted()
        meanDiff.forEach { print("${"%6.3f".format(it)}, ") }
        println()

        val mmap = makeMapFromNTs(srs, extract)

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

    fun makeMapFromNTs(srs: List<SRT>, extract: (SRT) -> Double): Map<Int, Map<Double, Double>> {
        val mmap = mutableMapOf<Int, MutableMap<Double, Double>>() // d, meanDiff -> fld

        srs.forEach {
            val dmap = mmap.getOrPut(it.d) { mutableMapOf() }
            dmap[it.reportedMeanDiff] = extract(it)
        }

        return mmap.toSortedMap()
    }
}