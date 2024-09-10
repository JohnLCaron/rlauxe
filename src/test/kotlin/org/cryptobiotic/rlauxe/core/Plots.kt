package org.cryptobiotic.rlauxe.core

import kotlin.math.sqrt

data class SRT(val N: Int, val theta: Double, val nsamples: Double, val pct: Double, val stddev: Double, val hist: Histogram?,
               val reportedMeanDiff: Double, val d: Int, val eta0: Double)

fun plotSRTsamples(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "number votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, true) { it.nsamples }
}

fun plotSRTpct(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "", isInt:Boolean=true) {
    val utitle = "pct votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, isInt) { it.pct }
}

fun plotSRTstdev(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "stddev votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, true) { it.stddev }
}

fun plotSRTsuccess(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, sampleMaxPct: Int, nrepeat: Int, title: String = "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, thetas, ns, utitle, true) {
        val cumul = it.hist!!.cumul(sampleMaxPct)
        (100.0 * cumul) / nrepeat
    }
}

fun plotSRS(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String, isInt: Boolean, extract: (SRT) -> Double) {
    println()
    println(title)
    print("     N, ")
    val theta = thetas.sorted().map { .5 + it * .5 }
    theta.forEach { print("${"%6.3f".format(it)}, ") }
    println()

    val mmap = makeMapFromSRTs(srs, thetas, ns, extract)

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

fun makeMapFromSRTs(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, extract: (SRT) -> Double): Map<Int, Map<Double, Double>> {
    val mmap = mutableMapOf<Int, MutableMap<Double, Double>>() // N, m -> fld

    // fill with all the maps initialized to -1
    ns.forEach { N ->
        mmap[N] = mutableMapOf()
        val nmap = mmap[N]!!
        thetas.forEach { margin ->
            nmap[margin] = -1.0
        }
    }

    srs.forEach {
        val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
        dmap[it.theta] = extract(it)
    }

    return mmap.toSortedMap()
}

fun makeSRT(N: Int, theta: Double, rr: AlphaMartRepeatedResult, reportedMeanDiff: Double, d: Int): SRT {
    val (sampleCountAvg, sampleCountVar, _) = rr.nsamplesNeeded.result()
    val pct = (100.0 * sampleCountAvg / N)
    return SRT(N, theta, sampleCountAvg, pct, sqrt(sampleCountVar), rr.hist, reportedMeanDiff, d, rr.eta0)
}

fun plotSRS(srs: List<SRT>, title: String, isInt: Boolean, colf: String = "%6.0f", rowf: String = "%6.3f", ff: String = "%6.2f",
            colFld: (SRT) -> Double, rowFld: (SRT) -> Double, fld: (SRT) -> Double) {
    println()
    println(title)
    print("      , ")
    val df = "%6d"

    val cols = findValuesFromSRT(srs, colFld)
    cols.forEach { print("${colf.format(it)}, ") }
    println()

    val mmap = makeMapFromSRTs(srs, colFld, rowFld, fld)

    mmap.forEach { dkey, dmap ->
        print("${rowf.format(dkey)}, ")
        dmap.toSortedMap().forEach { nkey, fld ->
            if (isInt)
                print("${df.format(fld.toInt())}, ")
            else
                print("${ff.format(fld)}, ")
        }
        println()
    }
}

fun makeMapFromSRTs(srs: List<SRT>, colFld: (SRT) -> Double, rowFld: (SRT) -> Double, fld: (SRT) -> Double): Map<Double, Map<Double, Double>> {
    val mmap = mutableMapOf<Double, MutableMap<Double, Double>>() // N, m -> fld

    val cols = findValuesFromSRT(srs, colFld)
    val rows = findValuesFromSRT(srs, rowFld)

    // fill with all the maps initialized to -1
    rows.forEach { rowFld ->
        val innerMap = mutableMapOf<Double, Double>()
        mmap[rowFld] = innerMap
        cols.forEach { colField ->
            innerMap[colField] = -1.0 // or null ?
        }
    }

    srs.forEach {
        val colFld = colFld(it)
        val rowFld = rowFld(it)
        val dmap = mmap.getOrPut(rowFld) { mutableMapOf() }
        dmap[colFld] = fld(it)
    }

    return mmap.toSortedMap()
}

fun findValuesFromSRT(srs: List<SRT>, extract: (SRT) -> Double): List<Double> {
    val mmap = mutableSetOf<Double>()

    srs.forEach {
        mmap.add(extract(it))
    }

    return mmap.sorted().toList()
}

