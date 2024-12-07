package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.rlaplots.SRT
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

fun plotRatio(results: Map<Double, List<SRT>>) {

    // assume all sets have same N/theta values
    val fistSet: List<SRT> = results.values.first()
    val cvrMeans = findValuesFromSRT(fistSet) { it.reportedMean }
    val ns = findValuesFromSRT(fistSet) { it.Nc.toDouble() }
    val nsi = ns.map { it.toInt() }

    // get maps for all results
    val mmaps: Map<Double, Map<Int, Map<Double, Double>>> =
        results.map { entry ->
            val mmap: Map<Int, Map<Double, Double>> =
                makeMapFromSRTs(entry.value, cvrMeans, nsi) { it.pctSamples }
            Pair(entry.key, mmap)
        }.toMap()

    /* Now find the smallest value
    val nsamplesMinMapOld = mutableMapOf<Int, MutableMap<Double, Double>>() // N, m -> fld
    nsi.forEach { N ->
        cvrMeans.forEach { theta ->
            val dmap = nsamplesMinMapOld.getOrPut(N) { mutableMapOf() }
            dmap[theta] = findSmallestNotZero(mmaps, N, theta)
        }
    }

     */

    // Now make the ratio with the smallest value
    val nsamplesRatio = mutableMapOf<Double, MutableMap<Int, MutableMap<Double, Double>>>() // N, theta -> fld
    mmaps.forEach { entry ->
        nsamplesRatio[entry.key] = mutableMapOf()
        val kmap = nsamplesRatio[entry.key]!!
        nsi.forEach { N ->
            cvrMeans.forEach { theta ->
                val nmap = kmap.getOrPut(N) { mutableMapOf() }
                val thisValue = entry.value[N]!![theta]!!
                var wtf = thisValue / findSmallestNotZero(mmaps, N, theta)
                if (wtf.isNaN() || wtf.isInfinite()) {
                    findSmallestNotZero(mmaps, N, theta)
                    wtf = -1.0
                }
                nmap[theta] = wtf
            }
        }
    }

    // print it

    val rowf = "%6.0f"
    val ff = "%6.3f"
    val sf = "%6s"

    println()
    nsamplesRatio.forEach { (eta0Factor, kmap) ->
        println("ratio eta0Factor=$eta0Factor,  theta(col) vs N(row)")
        colHeader(cvrMeans, "cvrMean", colf = "%6.3f")

        val allValues = mutableListOf<Double>()
        kmap.forEach { nkey, nmap ->
            print("${sf.format(nkey)}, ")
            nmap.toSortedMap().forEach { nkey, fld ->
                print("${ff.format(fld)}, ")
                allValues.add(fld)
            }
            println()
        }
        println("geometric mean = ${geometricMean(allValues)}")
        println()
    }
}

fun findSmallestNotZero(mmaps: Map<Double, Map<Int, Map<Double, Double>>>, N: Int, theta: Double): Double {
    val llist = mmaps.map { entry -> entry.value[N]?.get(theta) ?: 0.0 }
    val llistf = llist.filter{ it != 0.0 }
    val result =  if (llistf.isEmpty()) -1.0 else llistf.min()
    return result
}

// construct new dlcalcs replacing pct with ratio = pct/pctMin
fun createPctRatio(dlcalcs: Map<Int, List<SRT>>, thetas: List<Double>, ns: List<Int>): Map<Int, List<SRT>> {
    val newdlc = mutableMapOf<Int, MutableList<SRT>>() // N, m -> fld
    // val newsrs = mutableListOf<SRT>()
    val dlmapPct = dlcalcs.mapValues { entry -> entry.key to makeMapFromSRTs(entry.value, thetas, ns) { 100.0 * it.nsamples / it.Nc } }.toMap() // dl -> N, m -> pct
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
                // data class SRT(val N: Int, val theta: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double,
                //               val failPct: Double, val nsamples: Double, val stddev: Double)
                val sr = SRT(
                    N, margin, 0.0, emptyMap(), 0, 0, 0,
                    stddev = TODO(),
                    percentHist = null
                ) // TODO
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

// older verrsion
fun plotPctRatio(newdlc: Map<Int, List<SRT>>, thetas: List<Double>, nlist: List<Int>, reportedMeanDiff: Double) {
    newdlc.forEach { dl, sps ->
        plotSRTpct(
            sps,
            thetas,
            nlist,
            "pct/pctMin d=$dl, reportedMeanDiff=$reportedMeanDiff",
            isInt = false
        )
        val x = sps.map { it.failPct } // TODO
        println("geometric mean = ${geometricMean(x)}")
    }
    println("===============================================")
}

fun geometricMean(x: List<Double>): Double {
    if (x.size == 0) return 0.0
    val lnsum = x.filter{it > 0}.map{ ln(it) }.sum()
    return exp( lnsum / x.size )
}