package org.cryptobiotic.rlauxe.persist.csv


import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.*


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
) {
    companion object {
        val header = "id, phantom, index, sampleNumber, contest : candidate, ...\n"
    }
}

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
    writer.write(CvrCsv.header)
    cvrs.forEach {
        writer.write(writeCSV(it.publishCsv()))
    }
    writer.close()
}

class CvrsCsvWriter(filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    var countCvrs = 0
    init {
        writer.write(CvrCsv.header)
    }

    fun write(cvrs: List<CvrUnderAudit>) {
        cvrs.forEach {
            writer.write(writeCSV(it.publishCsv()))
        }
        countCvrs += cvrs.size
    }

    fun close() {
        println("wrote $countCvrs cvrs")
        writer.close()
    }
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

fun readCvrsCsvFile(input: InputStream): List<CvrUnderAudit> {
    val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1"))
    val header = reader.readLine() // get rid of header line

    val cvrs = mutableListOf<CvrUnderAudit>()
    while (true) {
        val line = reader.readLine() ?: break
        cvrs.add(readCvrCvs(line))
    }
    reader.close()
    return cvrs
}

fun readCvrsCsvIterator(filename: String): Iterator<CvrUnderAudit> {
    return if (filename.endsWith("zip")) {
        val reader = ZipReader(filename)
        val input = reader.inputStream()
        IteratorCvrsCsvStream(input)
    } else {
        IteratorCvrsCsvFile(filename)
    }
}

class IteratorCvrsCsvStream(input: InputStream): Iterator<CvrUnderAudit> {
    val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1"))
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): CvrUnderAudit {
        return readCvrCvs(nextLine!!)
    }

    fun close() {
        println("read $countLines lines")
        reader.close()
    }
}

class IteratorCvrsCsvFile(filename: String): Iterator<CvrUnderAudit> {
    val reader: BufferedReader = File(filename).bufferedReader()
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): CvrUnderAudit {
        return readCvrCvs(nextLine!!)
    }

    fun close() {
        println("read $countLines lines")
        reader.close()
    }
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
