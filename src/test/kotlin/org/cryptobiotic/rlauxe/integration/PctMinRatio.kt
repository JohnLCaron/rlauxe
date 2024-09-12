package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.plots.SRT
import org.cryptobiotic.rlauxe.plots.makeMapFromSRTs
import org.cryptobiotic.rlauxe.plots.plotSRTpct
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

// construct new dlcalcs replacing pct with ratio = pct/pctMin
fun createPctRatio(dlcalcs: Map<Int, List<SRT>>, thetas: List<Double>, ns: List<Int>): Map<Int, List<SRT>> {
    val newdlc = mutableMapOf<Int, MutableList<SRT>>() // N, m -> fld
    // val newsrs = mutableListOf<SRT>()
    val dlmapPct = dlcalcs.mapValues { entry -> entry.key to makeMapFromSRTs(entry.value, thetas, ns) { it.pct } }.toMap() // dl -> N, m -> pct
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
                // (val N: Int, val theta: Double, val nsamples: Double, val pct: Double, val stddev: Double,
                //               val reportedMeanDiff: Double, val d: Int, val eta0: Double, val hist: Histogram?)
                val sr = SRT(N, margin, 0.0, ratio, 0.0, 0.0, dMin, 0.0, null)
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

fun plotPctRatio(newdlc: Map<Int, List<SRT>>, thetas: List<Double>, nlist: List<Int>, reportedMeanDiff: Double) {
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

fun geometricMean(x: List<Double>): Double {
    val lnsum = x.filter{it > 0}.map{ ln(it) }.sum()
    return exp( lnsum / x.size )
}