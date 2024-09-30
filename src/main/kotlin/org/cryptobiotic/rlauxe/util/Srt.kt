package org.cryptobiotic.rlauxe.util

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

data class SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?) {

    val theta = reportedMean + reportedMeanDiff // the true mean
    val successPct = 100.0 * nsuccess.toDouble() / (if (ntrials == 0) 1 else ntrials) // failure ratio
    val failPct = 100.0 * (ntrials - nsuccess).toDouble() / (if (ntrials == 0) 1 else ntrials) // failure ratio
    val nsamples = totalSamplesNeeded.toDouble() / (if (nsuccess == 0) 1 else nsuccess) // avg number of samples for successes
    val pctSamples = 100.0 * nsamples / (if (N == 0) 1 else N)
}

// simple serialization to csv files
class SRTcsvWriter(val filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()

    init {
        writer.write("N, reportedMean, reportedMeanDiff, d, eta0, eta0Factor, nsuccess, ntrials, totalSamples, stddev, percentHist\n")
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
        if (srt.percentHist != null) {
            append(", \"${srt.percentHist}\"")
        }
        appendLine()
    }

    fun close() {
        writer.close()
    }
}

class SRTcsvReader(filename: String) {
    val reader: BufferedReader = File(filename).bufferedReader()
    val header = reader.readLine() // get rid of header line

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

        val percentHist = if (idx < tokens.size) org.cryptobiotic.rlauxe.util.Deciles.Companion.fromString(ttokens[idx++]) else null

        return SRT(N, reportedMean, reportedMeanDiff, d, eta0, eta0Factor, nsuccess, ntrials, nsamples, stddev, percentHist)
    }
}
