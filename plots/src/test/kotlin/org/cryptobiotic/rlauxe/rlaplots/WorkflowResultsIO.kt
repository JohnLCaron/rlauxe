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
        writer.write("parameters, N, margin, status, nrounds, samplesUsed, samplesNeeded, failPct\n")
        // "auditType=3.0 nruns=10.0 fuzzPct=0.02 ", 50000, 0.04002, StatRejectNull, 2.0, 293.5, 261.9, 0.0
        wrs.forEach {
            writer.write(toCSV(it))
        }
        writer.close()
    }

    fun toCSV(wr: WorkflowResult) = buildString {
        append("${writeParameters(wr.parameters)}, ${wr.N}, ${wr.margin}, ${wr.status.name}, ${wr.nrounds}, ")
        append("${wr.samplesUsed}, ${wr.samplesNeeded}, ${wr.failPct}")
        appendLine()
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
        require(tokens.size == 8) { "Expected 8 tokens but got ${tokens.size}" }
        val ttokens = tokens.map { it.trim() }
        var idx = 0
        val parameters = ttokens[idx++]
        val N = ttokens[idx++].toInt()
        val margin = ttokens[idx++].toDouble()
        val statusS = ttokens[idx++]
        val nrounds = ttokens[idx++].toDouble()
        val samplesUsed = ttokens[idx++].toDouble()
        val samplesNeeded = ttokens[idx++].toDouble()
        val failPct = ttokens[idx++].toDouble()

        val status = safeEnumValueOf(statusS) ?: TestH0Status.InProgress
        return WorkflowResult(N, margin, status, nrounds, samplesUsed, samplesNeeded, readParameters(parameters), failPct)
    }
}