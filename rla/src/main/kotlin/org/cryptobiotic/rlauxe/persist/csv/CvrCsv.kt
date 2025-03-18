package org.cryptobiotic.rlauxe.persist.csv


import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter


// data class Cvr(
//    val id: String,
//    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
//    val phantom: Boolean = false,
//)
// CvrUnderAudit (val cvr: Cvr, val index: Int, val sampleNum: Long)

data class CvrCsv(
    val id: String,
    val phantom: Boolean,
    val index: Int,
    val sampleNumber: Long,
    val votes: List<VotesCsv>,
)
val header = "id, phantom, index, sampleNumber, contest : candidate, ...\n"

fun writeCSV(wr: CvrCsv) = buildString {
    val id = wr.id.replace(",", "") // nasty commas: could remove when reading
    append("$id, ${wr.phantom}, ${wr.index}, ${wr.sampleNumber}, ")
    wr.votes.forEach { v -> append("${v.contest}: ${v.candidate.joinToString(" ")}, ") }
    appendLine()
}

data class VotesCsv(
    val contest: Int,
    val candidate: IntArray,
)

fun makeVotesCsv(votes: Map<Int, IntArray>) : List<VotesCsv> {
    return votes.entries.map { (contestId, candArray) -> VotesCsv(contestId, candArray) }
}

/*
fun makeVotesCsv(votes: Map<Int, IntArray>) : List<VotesCsv> {
    val isForm1 = votes.values.map { it.size < 2 }.reduce(Boolean::and)
    return if (isForm1) {
        votes.entries.map { (contestId, candArray) ->
            val candidate = if (candArray.size == 0) null else candArray[0]
            VotesCsv(contestId, candidate)
        }
    } else {
        votes.entries.map { (contestId, candArray) ->
            IrvVotesCsv(contestId, candArray)
        }
    }
} */

fun CvrUnderAudit.publishCsv() : CvrCsv {
    return CvrCsv(
        this.id,
        this.cvr.phantom,
        this.index,
        this.sampleNum,
        makeVotesCsv(this.votes),
    )
}

fun CvrCsv.import() : CvrUnderAudit {
    val votes = this.votes.associate { it.contest to it.candidate }
    val cvr = Cvr(this.id, votes, this.phantom)
    return CvrUnderAudit(
        cvr,
        this.index,
        this.sampleNumber,
    )
}

///////////////////////////////////////////////////////////////

fun writeCvrsCsvFile(cvrs: List<CvrUnderAudit>, filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    writer.write(header)
    cvrs.forEach {
        writer.write(writeCSV(it.publishCsv()))
    }
    writer.close()
}

fun readCvrsCsvFile(filename: String): List<CvrUnderAudit> {
    val reader: BufferedReader = File(filename).bufferedReader()
    val header = reader.readLine() // get rid of header line

    val cvrs = mutableListOf<CvrUnderAudit>()
    while (true) {
        val line = reader.readLine() ?: break
        cvrs.add(readCvrCvs(line))
    }
    reader.close()
    return cvrs
}

fun readCvrCvs(line: String): CvrUnderAudit {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }
    var idx = 0
    val id = ttokens[idx++]
    val phantom = ttokens[idx++] == "true"
    val index = ttokens[idx++].toInt()
    val sampleNum = ttokens[idx++].toLong()
    val votes = mutableListOf<VotesCsv>()
    while (idx < ttokens.size) {
        val vtokens = ttokens[idx].split(":")
        val contestStr = vtokens[0].trim()
        if (contestStr.isEmpty()) break // trailing comma
        val contest = contestStr.toInt()
        val candStr = vtokens[1].trim()
        val candArray = if (candStr.isEmpty()) intArrayOf() else candStr.split(" ").map { it.trim().toInt() }.toIntArray()
        votes.add(VotesCsv(contest, candArray))
        idx++
    }
    val csv = CvrCsv(id, phantom, index, sampleNum, votes)
    return csv.import()
}
