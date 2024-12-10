package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.rlaplots.SRT

// CANDIDATE FOR REMOVAL

//// plots for crtMean by meanDiff
fun plotMeanFailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed cvrMean (row) vs meanDiff (col): " + title
    plotSRS(srs, utitle, true, ff = "%6.3f", rowf = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMeanDiff },
        rowFld = { srt: SRT -> srt.reportedMean },
        fld = { srt: SRT -> srt.failPct }
    )
}

fun plotMeanSamples(srs: List<SRT>, title: String) {
    val utitle = "nsamples, cvrMean (row) vs meanDiff (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f", rowf = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMeanDiff },
        rowFld = { srt: SRT -> srt.reportedMean },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotMeanPct(srs: List<SRT>, title: String) {
    val utitle = "pct samples, cvrMean (row) vs meanDiff (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", rowf = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMeanDiff },
        rowFld = { srt: SRT -> srt.reportedMean },
        fld = { srt: SRT -> srt.pctSamples}
    )
}


//// plots for N vs theta
fun plotNTsuccess(srs: List<SRT>, title: String, colTitle: String= "") {
    val utitle = "nsuccess cvrMean (col) vs N (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.Nc.toDouble() },
        fld = { srt: SRT -> srt.nsuccess.toDouble() }
    )
}

fun plotNTsuccessPct(srs: List<SRT>, title: String, colTitle: String= "") {
    val utitle = "successPct cvrMean (col) vs N (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.Nc.toDouble() },
        fld = { srt: SRT -> srt.successPct }
    )
}

fun plotNTsamples(srs: List<SRT>, title: String, colTitle: String = "") {
    val utitle = "nsamples, cvrMean (col) vs N (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.Nc.toDouble() },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotNTsamplesPct(srs: List<SRT>, title: String, colTitle: String= "") {
    val utitle = "pct samples, cvrMean (col) vs N (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.Nc.toDouble() },
        fld = { srt: SRT -> srt.pctSamples }
    )
}

fun plotNTsuccessDecile(srs: List<SRT>, title: String, sampleMaxPct: Int, colTitle: String= "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.Nc.toDouble() },
        fld = { srt: SRT -> srt.percentHist?.cumul(sampleMaxPct) ?: -1.0  }
    )
}

//// plots for d vs meanDiff
fun plotDDfailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed, theta (col) vs d (row): " + title
    plotSRS(srs, utitle, true,
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> srt.failPct }
    )
}

fun plotDDsample(srs: List<SRT>, title: String) {
    val utitle = "nsamples, theta (col) vs d (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f", colf = "%6.3f",
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotDDpct(srs: List<SRT>, title: String) {
    val utitle = "pct samples, theta (col) vs d (row): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f",
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> 100.0 * srt.nsamples / srt.Nc }
    )
}

fun plotDDsuccessDecile(srs: List<SRT>, title: String, sampleMaxPct: Int, colTitle: String= "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, utitle, false, ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> srt.percentHist?.cumul(sampleMaxPct) ?: -1.0 }
    )
}

//// plots for theta vs eta0Factor
fun plotTFsuccessDecile(srs: List<SRT>, title: String, sampleMaxPct: Int, colTitle: String= "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, utitle, false, rowf = "%6.2f", ff = "%6.1f", colTitle = colTitle,
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.eta0Factor },
        fld = { srt: SRT -> extractDecile(srt, sampleMaxPct) }
    )
}

fun extractDecile(srt: SRT, sampleMaxPct: Int): Double {
    return if (srt.percentHist == null || srt.percentHist.cumul(sampleMaxPct) == 0.0) 0.0 else {
        srt.percentHist.cumul(sampleMaxPct)
    }
}

fun plotTFdiffSuccessDecile(pollSrs: List<SRT>, compareSrs: List<SRT>, sampleMaxPct: Int, title: String = "", colTitle: String= "") {
    val utitle = "Comparison - Polling: % successRLA, for sampleMaxPct=$sampleMaxPct: " + title

    val srs = pollSrs.mapIndexed { idx, it ->
        val pollDecile = extractDecile(it, sampleMaxPct)
        val compSrt = compareSrs[idx]
        val compDecile = extractDecile(compSrt, sampleMaxPct)
        it.copy(stddev = compDecile - pollDecile) // hijack stddev
    }

    plotSRS(srs, utitle, false, ff = "%6.2f", rowf = "%6.2f", colTitle = "theta",
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.eta0Factor },
        fld = { srt: SRT -> srt.stddev }
    )
}

////
// general
val df = "%6d"
val sf = "%8s"

fun colHeader(srs: List<SRT>, colTitle: String, colf: String = "%6.3f", colFld: (SRT) -> Double) {
    print(sf.format(colTitle+":"))
    val cols = findValuesFromSRT(srs, colFld)
    cols.forEach { print("${colf.format(it)}, ") }
    println()
}

fun plotSRS(srs: List<SRT>, title: String?, isInt: Boolean, colf: String = "%6.3f", rowf: String = "%6.0f", ff: String = "%6.2f", colTitle: String="",
            colFld: (SRT) -> Double, rowFld: (SRT) -> Double, fld: (SRT) -> Double) {
    if (title != null) println(title)

    colHeader(srs, colTitle, colf, colFld)

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
    println()
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

////
// Old ways

fun plotSRTsamples(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "number votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, true) { it.nsamples }
}

fun plotSRTpct(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "", isInt:Boolean=true) {
    val utitle = "pct votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, isInt) { 100.0 * it.nsamples / it.N }
}

fun plotSRTstdev(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, title: String = "") {
    val utitle = "stddev votes sampled: " + title
    plotSRS(srs, thetas, ns, utitle, true) { it.stddev }
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
        dmap[it.reportedMean] = extract(it)
    }

    return mmap.toSortedMap()
}


