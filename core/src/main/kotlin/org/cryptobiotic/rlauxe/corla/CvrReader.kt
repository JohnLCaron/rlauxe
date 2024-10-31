package org.cryptobiotic.rlauxe.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.Reader
import java.lang.StrictMath.sqrt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// rewrite of us.freeandfair.corla.csv.DominionCVRExportParser

data class CvrExport(
    val countyId: String,
    val filename: String,
    val header: String,
    val cvrs: List<CastVoteRecord>,
) {
    fun show() = buildString {
        appendLine("countyId = $countyId")
        appendLine("filename = $filename")
        appendLine("$header")
        cvrs.forEach { appendLine(it.show()) }
    }
    fun summary() = buildString {
        appendLine("filename = $filename")
        appendLine("countyId = $countyId")
        appendLine("$header")
        appendLine("ncvrs = ${cvrs.size}")
        val ccount = mutableMapOf<Int, Int>()
        cvrs.forEach { cvr ->
            cvr.votes.forEach {
                ccount.getOrPut(it.contestId, {0})
                ccount[it.contestId] = ccount[it.contestId]!! + 1
            }
        }
        ccount.toSortedMap().forEach { (k, v) ->
            appendLine("  contest ${k} has $v votes")
        }
    }
}

// CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,PrecinctPortion,BallotType
data class CastVoteRecord(
    val cvrNumber: Int,
    val tabulatorNum: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,
    val ballotType: String,
) {
    var precinctPortion: String? = null
    var votes =  mutableListOf<ContestVotes>()

    fun show() = buildString {
        append("$cvrNumber, ")
        append("$tabulatorNum, ")
        append("$batchId, ")
        append("$recordId, ")
        append("$imprintedId, ")
        append("$precinctPortion, ")
        appendLine("$ballotType")
        votes.forEach {
            appendLine("    ${it.contestId} ${it.votes.joinToString(",")}")
        }
    }
}

data class ContestVotes(val contestId: Int, val votes: List<Int>)

fun readDominionCvrExport(filename: String, countyId: String): CvrExport {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)

    var my_record_count = 0
    val records: Iterator<CSVRecord> = parser.iterator()
    var lineNum = 1

    // 1) we expect the first line to be the election name
    val electionName = records.next()
    showLine("electionName", electionName)
    lineNum++

    // 2) the contest name for that column
    val contestLine = records.next()
    showLine("contestLine", contestLine)

    var my_first_contest_column = 0
    while ("" == contestLine[my_first_contest_column]) {
        my_first_contest_column++
    }
    lineNum++

    // 3) the choice name for that column
    val choiceLine = records.next()
    showLine("choiceLine", choiceLine)
    lineNum++

    // 4) seems the be the header for the first 6 columns
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println("header = $header")

    // make the column structure out of those 3 lines
    val schema = makeSchema(contestLine, choiceLine, headerRecord)
    println(schema.show())
    val ballotTypeIdx = if (schema.nheaders == 6) 5 else 6 // TODO

    val cvrs = mutableListOf<CastVoteRecord>()

    while (records.hasNext()) {
        val line = records.next()
        // showLine("line", line)

        val cvr = CastVoteRecord(
            line.get(0).toInt(),
            line.get(1).toInt(),
            line.get(2),
            line.get(3).toInt(),
            line.get(4),
            line.get(ballotTypeIdx),
        )
        // the  non-blank column tells which contest it belongs to
        var colidx = schema.nheaders
        while (colidx < line.size()) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // cvr.raw = makeRaw(line, useContest.startCol, useContest.ncols)
                    val irvVotes = makeIrvVotes(line, useContest.startCol, useContest.ncols, useContest.nchoices)
                    cvr.votes.add( ContestVotes(useContestIdx, irvVotes))
                } else {
                    val votes = makeRegularVotes(line, useContest.startCol, useContest.ncols)
                    cvr.votes.add( ContestVotes(useContestIdx, votes))
                }
                colidx += useContest.ncols
            } else
                colidx++
        }
        cvrs.add(cvr)
    }

    return CvrExport(countyId, filename, header, cvrs)
}

val d3f = "%3d"
fun showLine(what: String, line: CSVRecord) {
    println(what)
    val elems: List<String> = line.toList()
    elems.forEachIndexed { idx, it ->
        if (it.isNotEmpty()) println("  ${d3f.format(idx)}: $it")
    }
}


fun makeRaw(line: CSVRecord, start: Int, count: Int): List<Int> {
    val raw = mutableListOf<Int>()
    for (i in 0 until count) {
        raw.add( line.get(start + i).toInt() )
    }
    return raw
}

fun makeIrvVotes(line: CSVRecord, start: Int, count: Int, ncands: Int): List<Int> {
    val raw = mutableListOf<Int>()
    for (i in 0 until count) {
        raw.add( line.get(start + i).toInt() )
    }
    val cands = mutableListOf<IntArray>()
    for (cand in 0 until ncands) {
        val candArray = IntArray(ncands) { i -> raw[cand + ncands*i] }
        //if (candArray.sum() > 1)
        //    println(" LOOK raw $raw")
        cands.add(candArray)
    }

    val ranked = mutableListOf<Int>()
    for (rank in 0 until ncands) {
        for (cand in 0 until ncands) {
            if (cands[cand][rank] == 1) ranked.add(cand)
        }
    }
    return ranked
}

fun makeRegularVotes(line: CSVRecord, start: Int, count: Int): List<Int> {
    val votes = mutableListOf<Int>()
    for (i in 0 until count) {
        val col = line.get(start + i).toInt()
        if (col > 0) votes.add( i)
    }
    return votes
}

/////////////////////////////////////////////////////////////////////////

class Schema(val columns: List<ColumnInfo>, val nheaders: Int, val contests: List<ContestInfo> ) {

    fun show() = buildString {
        contests.forEach {
            with (it) {
                appendLine("Contest $contestIdx $contestName $startCol $ncols isIRV=$isIRV nchoices=$nchoices")
                for (colIdx in startCol until startCol+ncols) {
                    val col = columns[colIdx]
                    appendLine("    ${col.choice}")
                }
            }
        }
    }
}

class ColumnInfo(val colno:Int, val contest: String, val choice: String, val header: String) {
    var contestIdx: Int = -1
}

class ContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    val isIRV: Boolean
    val nchoices: Int

    init {
        isIRV = contestName.contains("Number of ranks=")
        nchoices = if (isIRV) {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt()
        } else ncols
    }

}

fun makeSchema(contests: CSVRecord, choices: CSVRecord, headers: CSVRecord): Schema {
    require(contests.size() == choices.size())
    require(headers.size() == choices.size())

    val columns = mutableListOf<ColumnInfo>()
    for (idx in 0 until contests.size()) {
        columns.add( ColumnInfo(idx, contests.get(idx).trim(), choices.get(idx).trim(), headers.get(idx).trim() ) )
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colno

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<ContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        if (colidx != skipIdx && col.contest != currContestName) {
            ccontests.add( ContestInfo(currContestIdx, currContestName, startIdx,colidx-startIdx) )
            startIdx = colidx
            currContestIdx++
        }
        col.contestIdx = currContestIdx
        currContestName = col.contest
    }
    ccontests.add( ContestInfo(currContestIdx, currContestName, startIdx, columns.size-startIdx) )

    return Schema(columns, nheaders, ccontests)
}

