package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.boulder.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.boulder.parseIrvContestName
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.auditcenter.munge
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.lang.StrictMath.sqrt
import kotlin.collections.set

// these are the data structures used for DominionCvrExportReader

private val logger = KotlinLogging.logger("DominionCvrExport")

// one for each county
data class DominionCvrCsvSummary(
    // val county: String,  TODO
    val electionName: String,
    val versionName: String,
    val filename: String,
    val schema: Schema,
    val cvrs: List<CastVoteRecord>, // includes both regular and IRV votes, but not redacted groups
    val redactedGroups: List<DominionRedactedGroup>,
    val exportCardStyles: List<ExportCardStyle>,
) {
    fun show() = buildString {
        appendLine("filename = $filename")
        appendLine(" electionName = $electionName")
        appendLine(" versionName = $versionName")
        appendLine("$schema")
        cvrs.forEach { appendLine(it.show()) }
        redactedGroups.forEach { appendLine(it.toString()) }
    }

    fun summary() = buildString {
        appendLine("filename = $filename")
        appendLine(" electionName = $electionName")
        appendLine(" versionName = $versionName")
        appendLine(" ncvrs = ${cvrs.size}")
        val ccount = mutableMapOf<Int, Int>()
        cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote ->
                ccount.getOrPut(contestVote.contestId, {0})
                ccount[contestVote.contestId] = ccount[contestVote.contestId]!! + 1
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
    val ballotType: String, // might have to generate this ourself
) {
    var precinctPortion: String? = null
    var contestVotes =  mutableListOf<ContestVotes>() // equivilent to Map<contestId, IntArray>

    fun addVotes(schema: Schema, line: CSVRecord, lineno: Int): CastVoteRecord {
        var colidx = schema.nheaders // skip over the first n columns
        while (colidx < schema.columns.size) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest: SchemaContestInfo = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // cvr.raw = makeRaw(line, useContest.startCol, useContest.ncols)
                    val candVotes = makeIrvVotes(schema, line, lineno, useContest)
                    contestVotes.add(ContestVotes(useContestIdx, candVotes))
                } else {
                    val candVotes  = makeRegularVotes(schema, line, lineno, useContest)
                    contestVotes.add( ContestVotes(useContestIdx, candVotes))
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        if (contestVotes.isEmpty())
            print("") // should remove cvr i think
        return this
    }

    // assume a vote is 0 or 1
    // return list of candidates voted for; the candidates are numbered jithin this contest
    fun makeRegularVotes(schema: Schema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val votes = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) { // so i in [0..ncands)
            val colno = exportContest.startCol + i
            if (!schema.writeIns.contains(colno)) { // dont record write-ins
                val colValueS = line.get(colno)
                try {
                    val colValue = colValueS.toInt()
                    if (colValue > 0) votes.add(i)
                } catch (e: NumberFormatException) {
                    logger.warn{ "Cant parse '$colValueS' at col $colno line $lineno; probably didnt catch the redacted line"}
                    return emptyList()
                }
            }
        }
        return votes
    }

    //                             exportContest.startCol, start
    //                            exportContest.ncols, count
    //                            exportContest.nchoices, ncands
    fun makeIrvVotes(schema: Schema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val raw = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) {
            raw.add( line.get(exportContest.startCol + i).toInt() )
        }
        val cands = mutableListOf<IntArray>()
        for (cand in 0 until exportContest.nchoices) {
            val candArray = IntArray(exportContest.nchoices) { i -> raw[cand + exportContest.nchoices*i] }
            cands.add(candArray)
        }
        val ranked = mutableListOf<Int>()
        for (rank in 0 until exportContest.nchoices) {
            for (cand in 0 until exportContest.nchoices) {
                if (cands[cand][rank] == 1) ranked.add(cand)
            }
        }
        return ranked
    }

    fun voteFor(contest: Int): ContestVotes? = contestVotes.find { it.contestId == contest}

    fun show() = buildString {
        append("$cvrNumber, ")
        append("$tabulatorNum, ")
        append("$batchId, ")
        append("$recordId, ")
        append("$imprintedId, ")
        append("$precinctPortion, ")
        append("$ballotType, ")
        contestVotes.forEach {
            append("${it.contestId}: ${it.candVotes.joinToString(",")}, ")
        }
    }

    // this assumes that you have a single schema you use for the ContestInfo....
    fun convertToCvr(): Cvr {
        val cvrb = CvrBuilder2(this.imprintedId, false)
        this.contestVotes.forEach{
            cvrb.replaceContestVotes(it.contestId, it.candVotes.toIntArray())
        }
        return cvrb.build()
    }

    companion object {
        val header = "cvrNumber, tabulatorNum, batchId, recordId, imprintedId, ballotType, contest:votes"
    }

}

// note that these contestIds are internal to this file, so must to cross reference with contest name in the header to get that right
// candVotes list the candidates voted for
data class ContestVotes(val contestId: Int, val candVotes: List<Int>)

/////////////////////////////////////////////////////////////////////////

private val showDontMatch = false
private val showBallotStyles = false
private val showRedactedGroups = false

data class ExportCardStyle(val name: String, val contests: Set<Int>, var countCards: Int = 0)

class BallotStyles {
    val ballotTypes = mutableMapOf<Set<Int>, ExportCardStyle>()
    val redactedGroups = mutableMapOf<String, DominionRedactedGroup>()

    fun add(cvr:CastVoteRecord) {
        val cvrContests = cvr.contestVotes.map { it.contestId }.toSet()
        val ballotType = ballotTypes.getOrPut(cvrContests) { ExportCardStyle(cvr.ballotType, cvrContests) }
        ballotType.countCards++
    }

    // TODO we might want to remove contests with vote count == 0 ??
    fun add(redacted:DominionRedactedGroup) {
        // this is Boulder specific: val rname = if (redacted.contestVotes.contains(31)) "r${redacted.ballotType}+31"
        val rname = "${redacted.ballotType}"
        val group = redactedGroups[rname]
        if (group == null) {
            redactedGroups[rname] = DominionRedactedGroup.makeAccumulator(redacted, rname)
        } else {
            if (group.contests() == redacted.contests())
                group.merge(redacted)
            else if (showDontMatch)
                println("redacted $redacted doesnt match $group; c31 = ${redacted.contestVotes[31]}")
        }
    }
}

/////////////////////////////////////////////////////////////////////////

class Schema(val columns: List<SchemaColumnInfo>, val nheaders: Int, val contests: List<SchemaContestInfo>, val voteForNs: Map<Int, Int>) {
    val writeIns : Set<Int> = columns.filter{ it.choice == "Write-in" }.map { it.colno }.toSet()

    fun choices(contestId: Int): List<String> {
        val contest = contests.find{ it.contestIdx == contestId }
        val result = mutableListOf<String>()
        if (contest != null) {
            for (colIdx in contest.startCol until contest.startCol + contest.nchoices) {
                result.add( columns[colIdx].choice )
            }
        }
        return result
    }

    fun voteFor(contestId: Int, cvr: CastVoteRecord): List<String> {
        val choices = choices(contestId)
        val contestVotes = cvr.voteFor(contestId)
        val result = mutableListOf<String>()
        contestVotes?.candVotes?.forEach { result.add( choices[it]) } // could barf if malformed
        return result
    }

    fun showColumns() = buildString {
        println(SchemaColumnInfo.header)
        for (colidx in 0 until nheaders) { println("  ${ columns[colidx].showHeader() }") }
        println()
        println(SchemaColumnInfo.colHeader)
        for (colidx in nheaders until columns.size ) { println("  ${ columns[colidx].showColumn() }") }
    }

    fun showContests() = buildString {
        println(SchemaContestInfo.header)
        contests.forEach { println("  ${ it.show() }") }
    }

    fun show() = buildString {
        showColumns()
        showContests()
    }
}

private val contestWidth = 50
private val choiceWidth = 50
data class SchemaColumnInfo(val colno:Int, val contest: String, val choice: String, val header: String, val firstRow: String? = null) {
    var contestIdx: Int = -1
    fun showHeader(): String {
        return "${nfn(colno, 3)}, ${trunc(header, 20)}, $firstRow"
    }
    fun showColumn(): String {
        return "${nfn(colno, 3)}, ${trunc(contest, contestWidth)}, ${trunc(choice, choiceWidth)}, $contestIdx"
    }
    companion object {
        val header = "colno,          header name, firstRow"
        val colHeader = "colno, ${trunc("contest", contestWidth)}, ${trunc("choice", choiceWidth)}, contestIdx"
    }
}

data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    val isIRV: Boolean
    val nchoices: Int
    val voteForN: Int

    init {
        isIRV = contestName.contains("Number of ranks=")
        nchoices = if (isIRV) {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt() // WTF sqrt?
        } else ncols
        val (name, nwinners) = if (isIRV) parseIrvContestName(contestName) else parseContestNameAndVoteFor(contestName)
        voteForN = nwinners
    }

    fun show(): String {
        return "${nfn(contestIdx, 5)}, ${trunc(contestName, contestWidth)},      ${nfn(startCol, 3)},   ${nfn(ncols, 3)}, $isIRV"
    }
    companion object {
        val header = "contestIdx, ${trunc("contestName", contestWidth-3)}, startCol, ncols, isIRV"
    }
}

// firstRow for debugging
fun makeSchema(contests: CSVRecord, choices: CSVRecord, headers: CSVRecord, firstRow: CSVRecord? = null): Schema {
    require(contests.size() <= choices.size())
    require(headers.size() == choices.size())

    val columns = mutableListOf<SchemaColumnInfo>()
    for (idx in 0 until contests.size()) {
        // Garfield may have the party name appended to the choice name, eg "Donald J. Trump / Michael R. Pence:Republican"
        var choiceName = choices.get(idx).trim()
        val colonPos = choiceName.indexOf(":")
        if (colonPos > 0) choiceName = choiceName.substring(0, colonPos).trim()
        columns.add( SchemaColumnInfo(idx, contests.get(idx).trim(), choiceName, headers.get(idx).trim(), firstRow?.get(idx)))
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colno

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<SchemaContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        if (colidx != skipIdx && munge(col.contest) != munge(currContestName)) {
            ccontests.add( SchemaContestInfo(currContestIdx, currContestName, startIdx,colidx-startIdx) )
            startIdx = colidx
            currContestIdx++
        }
        col.contestIdx = currContestIdx
        currContestName = col.contest
    }
    ccontests.add( SchemaContestInfo(currContestIdx, currContestName, startIdx, columns.size-startIdx) )

    val voteForNs = ccontests.associate { it.contestIdx to it.voteForN }
    return Schema(columns, nheaders, ccontests, voteForNs)
}

fun cleanContestName(col: String) : String {
    val pos = col.indexOf("(Vote")
    return if (pos > 0) col.substring(0, pos).trim() else col.trim()
}

