package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.sampling.SimulateSampleSizeTask
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.util.Deciles
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.sampling.EstimationResult
import org.cryptobiotic.rlauxe.util.doubleIsClose
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.ceil
import kotlin.math.sqrt

// data class for capturing results from repeated audit trials.
data class SRT(val Nc: Int,
               val reportedMargin: Double,
               val reportedMeanDiff: Double,
               val testParameters: Map<String, Double>,
               val nsuccess: Int,
               val ntrials: Int,
               val totalSamplesNeeded: Int,
               val stddev: Double,
               val percentHist: Deciles?) {

    val reportedMean = margin2mean(reportedMargin)
    val theta = reportedMean + reportedMeanDiff // the true mean
    val successPct = 100.0 * nsuccess.toDouble() / (if (ntrials == 0) 1 else ntrials) // success ratio
    val failPct = 100.0 * (ntrials - nsuccess).toDouble() / (if (ntrials == 0) 1 else ntrials) // failure ratio
    val nsamples = totalSamplesNeeded.toDouble() / (if (nsuccess == 0) 1 else nsuccess) // avg number of samples for successes
    val wsamples = (successPct * nsamples + failPct * Nc) / 100 // nsamples weighted by success/failure
    val pctSamples = 100.0 * nsamples / (if (Nc == 0) 1 else Nc)
    val d : Int = testParameters["d"]?.toInt() ?: 0
    val eta0 = testParameters["eta0"] ?: 0.0
    val eta0Factor = testParameters["eta0Factor"] ?: 0.0
    val p2prior = testParameters["p2prior"] ?: 0.0
    val p2oracle = testParameters["p2oracle"] ?: 0.0
    val d2 : Int = testParameters["d2"]?.toInt() ?: 0
    val N : Int = testParameters["N"]?.toInt() ?: 0
    val fuzzPct : Double = (testParameters["fuzzPct"] ?: 0.0)
    val isPolling : Boolean = (testParameters["polling"] != null)
    val hasStyles : Boolean = (testParameters["hasStyles"] != null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SRT

        if (Nc != other.Nc) return false
        if (!doubleIsClose(reportedMargin, other.reportedMargin)) return false
        if (!doubleIsClose(reportedMeanDiff, other.reportedMeanDiff)) return false
        if (nsuccess != other.nsuccess) return false
        if (ntrials != other.ntrials) return false
        if (totalSamplesNeeded != other.totalSamplesNeeded) return false
        if (stddev != other.stddev) return false
        if (testParameters != other.testParameters) return false
        if (percentHist != other.percentHist) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Nc
        result = 31 * result + reportedMargin.hashCode()
        result = 31 * result + reportedMeanDiff.hashCode()
        result = 31 * result + nsuccess
        result = 31 * result + ntrials
        result = 31 * result + totalSamplesNeeded
        result = 31 * result + stddev.hashCode()
        result = 31 * result + testParameters.hashCode()
        result = 31 * result + (percentHist?.hashCode() ?: 0)
        return result
    }
}

// TODO reportedMean, reportedMeanDiff in parameters, or in RunTestRepeatedResult
fun RunTestRepeatedResult.makeSRT(reportedMean: Double, reportedMeanDiff: Double): SRT {
    return SRT(
        this.Nc,
        this.margin ?: mean2margin(reportedMean),
        reportedMeanDiff,
        this.testParameters,
        this.nsuccess, this.ntrials, this.totalSamplesNeeded,
        sqrt(this.variance), this.percentHist)
}

// class SimulateSampleSizeTask(
//        val auditConfig: AuditConfig,
//        val contestUA: ContestUnderAudit,
//        val assertion: Assertion,
//        val cvrs: List<Cvr>,
//        val maxSamples: Int,
//        val startingTestStatistic: Double,
//        val prevSampleSize: Int,
//        val moreParameters: Map<String, Double> = emptyMap(),
//    ) : EstimationTask {

// data class SRT(val Nc: Int,
//               val reportedMargin: Double,
//               val reportedMeanDiff: Double,
//               val testParameters: Map<String, Double>,
//               val nsuccess: Int,
//               val ntrials: Int,
//               val totalSamplesNeeded: Int,
//               val stddev: Double,
//               val percentHist: Deciles?)

// TODO Nc in parameters, or in EstimationResult
fun EstimationResult.makeSRTnostyle(Nc: Int): SRT {
    val task = this.task as SimulateSampleSizeTask
    val parameters = task.moreParameters
    val N = parameters["N"]!! // double
    return SRT(
        Nc=Nc,
        reportedMargin=parameters["reportedMargin"]?: 0.0,
        reportedMeanDiff=parameters["reportedMeanDiff"]?: 0.0,
        testParameters=task.moreParameters,
        this.repeatedResult.nsuccess,
        task.auditConfig.ntrials,
        ceil(this.repeatedResult.totalSamplesNeeded * N / Nc).toInt(),
        stddev=0.0,
        percentHist=null)
}

// simple serialization to csv files
class SRTcsvWriter(val filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()

    init {
        writer.write("parameters, N, reportedMean, reportedMeanDiff, nsuccess, ntrials, totalSamples, stddev, percentHist\n")
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
            "${writeParameters(srt.testParameters)}, ${srt.Nc}, ${srt.reportedMean}, ${srt.reportedMeanDiff}, " +
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

fun writeParameters(params: Map<String, Double> ) = buildString {
    append("\"")
    params.forEach { key, value ->
        append("$key=$value ")
    }
    append("\"")
}

fun readParameters(s: String): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val tokens = s.split(" ", "\"")
    val ftokens = tokens.filter { it.isNotEmpty() }
    val ttokens = ftokens.map { it.trim() }
    ttokens.forEach {
        val kv = it.split("=")
        result[kv[0]] = kv[1].toDouble()
    }
    return result
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

    fun fromCSV(line: String): SRT {
        val tokens = line.split(",")
        require(tokens.size >= 7) { "Expected >= 7 tokens but got ${tokens.size}" }
        val ttokens = tokens.map { it.trim() }
        var idx = 0
        val testParameters = ttokens[idx++]
        val N = ttokens[idx++].toInt()
        val reportedMean = ttokens[idx++].toDouble()
        val reportedMeanDiff = ttokens[idx++].toDouble()
        val nsuccess = ttokens[idx++].toInt()
        val ntrials = ttokens[idx++].toInt()
        val nsamples = ttokens[idx++].toInt()
        val stddev = ttokens[idx++].toDouble()
        val percentHist = if (idx < tokens.size) Deciles.Companion.fromString(ttokens[idx++]) else null

        return SRT(N, mean2margin(reportedMean), reportedMeanDiff,
            readParameters(testParameters),
            nsuccess, ntrials, nsamples, stddev, percentHist)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////
// older version

// simple serialization to csv files
class SRTcsvWriterVersion1(val filename: String) {
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
            "${srt.Nc}, ${srt.reportedMargin}, ${srt.reportedMeanDiff}, ${srt.d}, ${srt.eta0}, ${srt.eta0Factor}, " +
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

class SRTcsvReaderVersion1(filename: String) {
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
        val reportedMargin = ttokens[idx++].toDouble()
        val reportedMeanDiff = ttokens[idx++].toDouble()
        val d = ttokens[idx++].toInt()
        val eta0 = ttokens[idx++].toDouble()
        val eta0Factor = ttokens[idx++].toDouble()
        val nsuccess = ttokens[idx++].toInt()
        val ntrials = ttokens[idx++].toInt()
        val nsamples = ttokens[idx++].toInt()
        val stddev = ttokens[idx++].toDouble()

        val percentHist = if (idx < tokens.size) Deciles.Companion.fromString(ttokens[idx++]) else null

        return SRT(N, reportedMargin, reportedMeanDiff,
            mapOf("d" to d.toDouble(), "eta0" to eta0, "eta0Factor" to eta0Factor),
            nsuccess, ntrials, nsamples, stddev, percentHist)
    }
}
