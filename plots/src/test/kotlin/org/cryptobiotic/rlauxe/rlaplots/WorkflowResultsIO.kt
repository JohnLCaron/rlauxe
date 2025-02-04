package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

// data class WorkflowResult(val N: Int,
//                          val margin: Double,
//                          val status: TestH0Status,
//                          val nrounds: Double,
//                          val samplesUsed: Double,
//                          val samplesNeeded: Double,
//                          val parameters: Map<String, Double>,
//                          val failPct: Double = 0.0, // from avgWorkflowResult()
//)

// simple serialization to csv files
class WorkflowResultsIO(val filename: String) {

    fun writeResults(wrs: List<WorkflowResult>) {
        val writer: OutputStreamWriter = FileOutputStream(filename).writer()
        writer.write("parameters, N, margin, status, nrounds, samplesUsed, samplesNeeded, nmvrs, failPct, neededStddev\n")
        // "auditType=3.0 nruns=10.0 fuzzPct=0.02 ", 50000, 0.04002, StatRejectNull, 2.0, 293.5, 261.9, 0.0
        wrs.forEach {
            writer.write(toCSV(it))
        }
        writer.close()
    }

    fun toCSV(wr: WorkflowResult) = buildString {
        append("${writeParameters(wr.parameters)}, ${wr.Nc}, ${wr.margin}, ${wr.status.name}, ${wr.nrounds}, ")
        append("${wr.samplesUsed}, ${wr.samplesNeeded}, ${wr.nmvrs}, ${wr.failPct} ${wr.neededStddev}")
        appendLine()
    }

    fun writeParameters(params: Map<String, Any> ) = buildString {
        append("\"")
        params.forEach { key, value ->
            append("$key=$value ")
        }
        append("\"")
    }

    fun readResults(): List<WorkflowResult> {
        val reader: BufferedReader = File(filename).bufferedReader()
        val header = reader.readLine() // get rid of header line

        val srts = mutableListOf<WorkflowResult>()
        while (true) {
            val line = reader.readLine() ?: break
            srts.add(fromCSV(line))
        }
        reader.close()
        return srts
    }

    fun fromCSV(line: String): WorkflowResult {
        val tokens = line.split(",")
        require(tokens.size >= 9) { "Expected 9 or 10 tokens but got ${tokens.size}" }
        val ttokens = tokens.map { it.trim() }
        var idx = 0
        val parameters = ttokens[idx++]
        val N = ttokens[idx++].toInt()
        val margin = ttokens[idx++].toDouble()
        val statusS = ttokens[idx++]
        val nrounds = ttokens[idx++].toDouble()
        val samplesUsed = ttokens[idx++].toDouble()
        val samplesNeeded = ttokens[idx++].toDouble()
        val nmvrs = ttokens[idx++].toDouble()
        val failPct = ttokens[idx++].toDouble()
        val stddev = if (tokens.size > 9) ttokens[idx++].toDouble() else 0.0

        val status = safeEnumValueOf(statusS) ?: TestH0Status.InProgress
        return WorkflowResult(N, margin, status, nrounds, samplesUsed, samplesNeeded, nmvrs, readParameters(parameters), failPct, stddev)
    }

    fun readParameters(s: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val tokens = s.split(" ", "\"")
        val ftokens = tokens.filter { it.isNotEmpty() }
        val ttokens = ftokens.map { it.trim() }
        ttokens.forEach {
            val kv = it.split("=")
            result[kv[0]] = kv[1]
        }
        return result
    }
}