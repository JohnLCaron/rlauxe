package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.integration.AlphaMartRepeatedResult
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.sqrt

// TODO Histogram of successes
data class SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double) {

    val theta = reportedMean + reportedMeanDiff // the true mean
    val successPct = nsuccess.toDouble() / (if (ntrials == 0) 1 else ntrials) // failure ratio
    val failPct = (ntrials - nsuccess).toDouble() / (if (ntrials == 0) 1 else ntrials) // failure ratio
    val nsamples = totalSamplesNeeded.toDouble() / (if (nsuccess == 0) 1 else nsuccess) // avg number of samples for successes
    val pctSamples = 100.0 * nsamples / (if (N == 0) 1 else N)
}

// val eta0: Double,            // initial estimate of the population mean, eg reported vote ratio
//                                   val N: Int,                  // population size (eg number of ballots)
//                                   val totalSamplesNeeded: Double, // total number of samples needed in nsuccess trials
//                                   val nsuccess: Int,           // number of successful trials
//                                   val ntrials: Int,            // total number of trials
//                                   val nsamplesNeeded: Welford, // avg, variance over ntrials of samples needed
//                                   val percentHist: Histogram? = null, // histogram of successful sample size as percentage of N, count trials in 10% bins
//                                   val status: Map<TestH0Status,
fun makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0, rr: AlphaMartRepeatedResult): SRT {
    val (sampleCountAvg, sampleCountVar, _) = rr.nsamplesNeeded.result()
    return SRT(N, reportedMean, reportedMeanDiff, d, rr.eta0, eta0Factor, rr.nsuccess, rr.ntrials, rr.totalSamplesNeeded, sqrt(sampleCountVar))
}

// simple serialization to csv files
class SRTwriter(filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()

    init {
        writer.write("N, reportedMean, reportedMeanDiff, d, eta0, eta0Factor, nsuccess, ntrials, totalSamples, stddev\n")
    }

    fun writeCalculations(calculations: List<SRT>) {
        calculations.forEach {
            writer.write(toCSV(it))
        }
    }

    // data class SRT(val N: Int, val theta: Double, val nsamples: Double, val pct: Double, val stddev: Double,
    // val hist: Histogram?, val reportedMeanDiff: Double, val d: Int)
    fun toCSV(srt: SRT) = buildString {
        append(
            "${srt.N}, ${srt.reportedMean}, ${srt.reportedMeanDiff}, ${srt.d}, ${srt.eta0}, ${srt.eta0Factor}, " +
                "${srt.nsuccess}, ${srt.ntrials}, ${srt.totalSamplesNeeded}, ${srt.stddev} "
        )
        // append(" ${srt.hist}")
        appendLine()
    }

    fun close() {
        writer.close()
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

    // writer.write("N, theta, reportedMeanDiff, d, eta0, failPct, nsamples, stddev\n")
    // SRT(val N: Int, val theta: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double,
    //               val failPct: Double, val nsamples: Double, val stddev: Double)
    fun fromCSV(line: String): SRT {
        val tokens = line.split(",")
        require(tokens.size >= 7) { "Expected >= 7 tokens but got ${tokens.size}" }
        val ttokens = tokens.map { it.trim() }
        var idx = 0
        val N = ttokens[idx++].toInt()
        val reportedMean = ttokens[idx++].toDouble()
        val reportedMeanDiff = ttokens[idx++].toDouble()
        val d = ttokens[idx++].toInt()
        val eta0 = ttokens[idx++].toDouble()
        val eta0Factor = ttokens[idx++].toDouble()
        val nsuccess = ttokens[idx++].toInt()
        val ntrials = ttokens[idx++].toInt()
        val nsamples = ttokens[idx++].toInt()
        val stddev = ttokens[idx++].toDouble()
        return SRT(N, reportedMean, reportedMeanDiff, d, eta0, eta0Factor, nsuccess, ntrials, nsamples, stddev)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////

/*
fun plotSRTfail(srs: List<SRT>, title: String = "") {
    val utitle = "number votes sampled: " + title
    plotSRS(srs, utitle, false, thetas, ns) { it.failPct }
}

fun plotSRTsamples(srs: List<SRT>, title: String = "") {
    val utitle = "number votes sampled: " + title
    plotSRS(srs, utitle, true, thetas, ns) { it.nsamples }
}

fun plotSRTpct(srs: List<SRT>, title: String = "", isInt:Boolean=true) {
    val utitle = "pct votes sampled: " + title
    plotSRS(srs, utitle, isInt, thetas, ns, utitle, isInt) { 100.0 * it.nsamples / it.N }
}

fun plotSRTstdev(srs: List<SRT>, title: String) {
    val utitle = "stddev votes sampled: " + title
    plotSRS(srs, utitle, true, thetas, ns, utitle, true) { it.stddev }
}

 */

//// plots for crtMean by meanDiff
fun plotMeanFailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed cvrMean (row) vs meanDiff (col): " + title
    plotSRS(srs, utitle, true, ff = "%6.3f", rowf = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMeanDiff },
        rowFld = { srt: SRT -> srt.reportedMean },
        fld = { srt: SRT -> 100.0 * srt.failPct }
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
        fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
    )
}


//// plots for N vs theta
fun plotNTfailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, true, ff = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> 100.0 * srt.failPct }
    )
}

fun plotNTsamples(srs: List<SRT>, title: String) {
    val utitle = "nsamples, N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotNTpct(srs: List<SRT>, title: String) {
    val utitle = "pct samples, N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
    )
}

// plot for d vs meanDiff
fun plotDDfailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed, d (row) vs theta (col): " + title
    plotSRS(srs, utitle, true,
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> 100.0 * srt.failPct }
    )
}

fun plotDDsample(srs: List<SRT>, title: String) {
    val utitle = "nsamples, d (row) vs theta (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f", colf = "%6.3f",
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotDDpct(srs: List<SRT>, title: String) {
    val utitle = "pct samples, d (row) vs theta (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f",
        colFld = { srt: SRT -> srt.theta },
        rowFld = { srt: SRT -> srt.d.toDouble() },
        fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
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

fun colHeader(cols: List<Double>, colTitle: String, colf: String = "%6.3f") {
    print(sf.format(colTitle+":"))
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

/*
fun plotSRTsuccess(srs: List<SRT>, thetas: List<Double>, ns: List<Int>, sampleMaxPct: Int, nrepeat: Int, title: String = "") {
    val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
    plotSRS(srs, thetas, ns, utitle, true) {
        val cumul = it.hist!!.cumul(sampleMaxPct)
        (100.0 * cumul) / nrepeat
    }
}

 */

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


