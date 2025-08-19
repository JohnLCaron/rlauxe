package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvReader
import kotlin.test.Test

import kotlin.collections.getOrPut

// CANDIDATE FOR REFACTOR

// read raw data and make csv plots of polling with theta != eta0
class PlotPollingDiffMeans {
    val showAllPlots = true

    // These are N vs theta plots for various values of d and MeanDiff
    @Test
    fun plotNTheta() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val nrepeat = 100

        val reader = SRTcsvReader("/home/stormy/rla/PollingDiffMeans/SRT$nrepeat.csv")
        val allSrts = reader.readCalculations()
        println(" number of SRTs = ${allSrts.size}")

        val ddiffMap = makeDDiffMap(allSrts)
        println(" number of ddiffs = ${ddiffMap.size}")

        ddiffMap.forEach { (ddiff, srts) ->
            plotSRTsamples(srts, thetas, nlist, "d=${ddiff.d} diffMean=${ddiff.diffMean} ")
            plotSRTpct(srts, thetas, nlist, "d=${ddiff.d} diffMean=${ddiff.diffMean} ")
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
        val nrepeat = 100

        val reader = SRTcsvReader("/home/stormy/rla/PollingDiffMeans/SRT$nrepeat.csv")
        val allSrts = reader.readCalculations()
        println(" number of SRTs = ${allSrts.size}")
        val nThetaMap = makeNthetaMap(allSrts)
        println(" number of nthetas = ${nThetaMap.size}")

        nThetaMap.forEach { (ntheta, srts) ->
            plotDDpct(srts, "N=${ntheta.N} theta=${ntheta.theta} ")
            plotDDfailPct(srts, "N=${ntheta.N} theta=${ntheta.theta} ")
            plotDDsuccessDecile(srts, "N=${ntheta.N} theta=${ntheta.theta} ", 30)
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
            val DD = Ntheta(it.Nc, it.reportedMean)
            val dmap : MutableList<SRT> = mmap.getOrPut(DD) { mutableListOf() }
            dmap.add(it)
        }
        return mmap.toSortedMap(nthetaComparator)
    }
}