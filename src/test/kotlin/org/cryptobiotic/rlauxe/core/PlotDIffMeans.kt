package org.cryptobiotic.rlauxe.core

import java.io.BufferedReader
import java.io.File
import kotlin.test.Test

import kotlin.collections.getOrPut
import kotlin.math.min
import kotlin.text.format

class PlotDiffMeans {

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

    @Test
    fun plotDiffMeansConcurrent() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)
        val nrepeat = 1000

        val reader = SRTreader("/home/stormy/temp/DiffMeans/SRT$nrepeat.csv")
        val allSrts = reader.readCalculations()
        println(" number of SRTs = ${allSrts.size}")

        val nThetaMap = makeNthetaMap(allSrts)

        /*
        val ntheta = Ntheta(5000,.51)
        val want = nThetaMap[ntheta]!!
        plotNTsample(want, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
        plotNTpct(want, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
        println()

         */

        nThetaMap.forEach { (ntheta, srts) ->
            plotNTsample(srts, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
            plotNTpct(srts, reportedMeanDiffs, dlist, "N=${ntheta.N} theta=${ntheta.theta} ")
            // plotNTsuccess(srts, reportedMeanDiffs, dlist, 30, nrepeat, "N=${ntheta.N} theta=${ntheta.theta} ")
            println()
        }

/*
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

    // construct new dlcalcs replacing pct with ratio = pct/pctMin
    fun creatSRpctRatio(dlcalcs: Map<Int, List<SRT>>, thetas: List<Double>, ns: List<Int>): Map<Int, List<SRT>> {
        val newdlc = mutableMapOf<Int, MutableList<SRT>>() // N, m -> fld
        // val newsrs = mutableListOf<SRT>()
        val dlmapPct = dlcalcs.mapValues { entry -> entry.key to makeMapFromSRTs(entry.value, thetas, ns) { it.pct } }
            .toMap() // dl -> N, m -> pct
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
}

class SRTreader(filename: String) {
    val reader: BufferedReader = File(filename).bufferedReader()

    init {
        println("firstLine = ${reader.readLine()}")
    }

    fun readCalculations(): List<SRT> {
        val srts = mutableListOf<SRT>()
        while (true) {
            val line = reader.readLine() ?: break
            srts.add(fromCSV(line))
        }
        reader.close()
        return srts
    }

    //         writer.write("N, theta, nsamples, stddev, reportedMeanDiff, d\n")
    // data class SRT(val N: Int, val theta: Double, val nsamples: Double, val pct: Double, val stddev: Double,
    // val hist: Histogram?, val reportedMeanDiff: Double, val d: Int)
    fun fromCSV(line: String): SRT {
        val tokens = line.split(",")
        require(tokens.size == 6) { "Expected 6 tokens but got ${tokens.size}" }
        val trim = tokens.map { it.trim() }
        val N = trim[0].toInt()
        val theta = trim[1].toDouble()
        val nsamples = trim[2].toDouble()
        val stddev = trim[3].toDouble()
        val reportedMeanDiff = trim[4].toDouble()
        val d = trim[5].toInt()
        return SRT(N, theta, nsamples, 0.0, stddev, null, reportedMeanDiff, d)
    }
}