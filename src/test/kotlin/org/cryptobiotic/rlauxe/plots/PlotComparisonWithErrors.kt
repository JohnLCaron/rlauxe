package org.cryptobiotic.rlauxe.plots

import kotlin.test.Test

import kotlin.collections.getOrPut

// read raw data and make csv plots of polling with theta != eta0
class PlotComparisonWithErrors {
    val nrepeat = 100
    // val reader = SRTreader("src/test/data/plots/CvrComparison/SRT$nrepeat.csv")
    val reader = SRTreader("/home/stormy/temp/CvrComparison/Full$nrepeat.csv")

    // These are N vs theta plots for various values of d and MeanDiff
    @Test
    fun plotNTheta() {
        val srts = reader.readCalculations()
        val title = "N=10000 d=100 eta0=noerror"
        plotMeanFailPct(srts, title)
        plotMeanSamples(srts, title)
        plotMeanPct(srts, title)
    }

    // These are N vs theta plots for various values of d and MeanDiff
    @Test
    fun plotNThetaForDD() {
        val allSrts = reader.readCalculations()
        val ddiffMap = makeDDiffMap(allSrts)
        println("-----------------------------------------------")

        ddiffMap.forEach { (ddiff, srts) ->
            println("d= ${ddiff.d} diffMean= ${ddiff.reportedMeanDiff} ")

            plotNTsuccessPct(srts, "d= ${ddiff.d} diffMean= ${ddiff.reportedMeanDiff} ")
            plotNTsamples(srts, "d= ${ddiff.d} diffMean= ${ddiff.reportedMeanDiff} ")
            plotNTpct(srts, "d= ${ddiff.d} diffMean= ${ddiff.reportedMeanDiff} ")
            println("-----------------------------------------------")
        }
    }

    data class DDiff(val d: Int, val reportedMeanDiff: Double)

    private val dDiffComparator = Comparator<DDiff> { o1, o2 ->
        when {
            (o1 == null && o2 == null) -> 0
            (o1 == null) -> -1
            else -> {
                val c = o1.d.compareTo(o2.d)
                when (c) {
                    -1, 1 -> c
                    else -> o1.reportedMeanDiff.compareTo(o2.reportedMeanDiff)
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
        val allSrts = reader.readCalculations()
        val nThetaMap = makeNthetaMap(allSrts)
        println("-----------------------------------------------")

        nThetaMap.forEach { (ntheta, srts) ->
            println("N=${ntheta.N} reportedMean=${ntheta.reportedMean} ")

            plotDDfailPct(srts, "N=${ntheta.N} reportedMean=${ntheta.reportedMean} ")
            plotDDsample(srts, "N=${ntheta.N} reportedMean=${ntheta.reportedMean} ")
            plotDDpct(srts, "N=${ntheta.N} reportedMean=${ntheta.reportedMean} ")
            println("-----------------------------------------------")
        }

    }

    data class Ntheta(val N: Int, val reportedMean: Double)

    private val nthetaComparator: Comparator<Ntheta> = Comparator<Ntheta> { o1, o2 ->
        when {
            (o1 == null && o2 == null) -> 0
            (o1 == null) -> -1
            else -> {
                val c = o1.reportedMean.compareTo(o2.reportedMean)
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
            val DD = Ntheta(it.N, it.reportedMean)
            val dmap : MutableList<SRT> = mmap.getOrPut(DD) { mutableListOf() }
            dmap.add(it)
        }
        return mmap.toSortedMap(nthetaComparator)
    }
}