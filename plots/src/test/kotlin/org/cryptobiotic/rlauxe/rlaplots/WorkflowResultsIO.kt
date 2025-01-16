package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.sampling.SimulateSampleSizeTask
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.util.Deciles
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.sampling.EstimationResult
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.ceil
import kotlin.math.sqrt

// data class WorkflowResult(val N: Int, val margin: Double, val nrounds: Int,
//                           val samplesUsed: Int, val samplesNeeded: Int,
//                           val parameters: Map<String, Double>,
//)

// simple serialization to csv files
class WorkflowResultsIO(val filename: String) {

    fun writeResults(wrs: List<WorkflowResult>) {
        val writer: OutputStreamWriter = FileOutputStream(filename).writer()
        writer.write("parameters, N, margin, nrounds, samplesUsed, samplesNeeded\n")
        wrs.forEach {
            writer.write(toCSV(it))
        }
        writer.close()
    }

    fun toCSV(wr: WorkflowResult) = buildString {
        append("${writeParameters(wr.parameters)}, ${wr.N}, ${wr.margin}, ${wr.nrounds}, ")
        append("${wr.samplesUsed}, ${wr.samplesNeeded}")
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
        require(tokens.size == 6) { "Expected 6 tokens but got ${tokens.size}" }
        val ttokens = tokens.map { it.trim() }
        var idx = 0
        val parameters = ttokens[idx++]
        val N = ttokens[idx++].toInt()
        val margin = ttokens[idx++].toDouble()
        val nrounds = ttokens[idx++].toDouble()
        val samplesUsed = ttokens[idx++].toDouble()
        val samplesNeeded = ttokens[idx++].toDouble()

        return WorkflowResult(N, margin, nrounds, samplesUsed, samplesNeeded, readParameters(parameters))
    }
}