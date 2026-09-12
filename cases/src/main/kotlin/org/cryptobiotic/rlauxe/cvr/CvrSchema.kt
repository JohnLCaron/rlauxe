package org.cryptobiotic.rlauxe.cvr

import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.corlaInput.munge
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.lang.StrictMath.sqrt
import kotlin.text.get

class CvrSchema(val inputSource: String,
                val headerMap: Map<String, Int>, // column name -> column index
                val columns: List<SchemaColumnInfo>,
                val nheaders: Int,
                val contests: List<SchemaContestInfo>,
                val voteForNs: Map<Int, Int>) {
    val writeIns : Set<Int> = columns.filter{ it.choice.lowercase().contains("write-in") }.map { it.colno }.toSet()

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

    // given a choice name, what is its index in the contest, aka id ?
    fun choiceIdx(choiceName: String): Int {
        val column = columns.find { it.choice == choiceName }
        if (column == null)
            throw RuntimeException("cant find choice $choiceName")
        // find contest it belongs to
        var startCol = 0
        contests.forEach {
            if (it.startCol <= column.colno) startCol = it.startCol
        }
        return column.colno - startCol
    }

    fun voteFor(contestId: Int, cvr: CvrRow): List<String> {
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

private val contestWidth = 60
private val choiceWidth = 40
data class SchemaColumnInfo(val colno:Int, val contest: String, val choice: String, val headerName: String) {
    var contestIdx: Int = -1
    fun showHeader(): String {
        return "${nfn(colno, 3)}, ${trunc(headerName, 20)}"
    }
    fun showColumn(): String {
        return "${nfn(colno, 3)}, ${trunc(contest, contestWidth)}, ${nfn(contestIdx, 3)}, " +
                "${trunc(choice, choiceWidth)}, ${trunc(headerName, 5)}"
    }
    companion object {
        val header = "colno,          header name"
        val colHeader = "colno, ${trunc("contest", contestWidth)}, idx, ${trunc("choice", choiceWidth)}, party"
    }
}

data class SchemaContestInfo(val contestIdx: Int, val orgName: String, val startCol: Int, val ncols: Int) {
    val isIRV: Boolean
    val nchoices: Int
    val voteForN: Int
    val contestName: String

    init {
        isIRV = orgName.contains("Number of ranks=") // Boulder 2023 IRV
        nchoices = if (!isIRV) ncols else {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt() // WTF sqrt?
        }

        // replace the contest name here
        val (parsedName, nwinners) = if (isIRV) parseIrvContestName(orgName) else parseContestNameAndVoteFor(orgName)
        contestName = parsedName
        voteForN = nwinners
    }

    fun show(): String {
        return "${nfn(contestIdx, 5)}, ${trunc(contestName, contestWidth)},      ${nfn(startCol, 3)},   ${nfn(ncols, 3)}, $isIRV"
    }

    companion object {
        val header = "contestIdx, ${trunc("contestName", contestWidth-3)}, startCol, ncols, isIRV, voteForN"
    }
}

// firstRow for debugging
fun makeCvrSchema(inputSource: String,  contests: CSVRecord, choices: CSVRecord, headers: CSVRecord): CvrSchema {
    require(contests.size() <= choices.size())
    require(headers.size() == choices.size())

    val columns = mutableListOf<SchemaColumnInfo>()
    for (idx in 0 until contests.size()) {
        val choiceName = cleanChoiceName(choices.get(idx))
        val partyName = if (idx < headers.size()) headers.get(idx).trim() else ""
        columns.add( SchemaColumnInfo(idx, contests.get(idx).trim(), choiceName, partyName))
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colno

    // the header for the first columns, then (sometimes) the party affiliation of the candidates
    val headerMap = mutableMapOf<String, Int>()
    repeat(nheaders) { idx ->
        headerMap[headers.get(idx).trim().lowercase()] = idx
    }

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<SchemaContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        // running through munge to try to detect minor typos in the contest names
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
    return CvrSchema(inputSource, headerMap, columns, nheaders, ccontests, voteForNs)
}

// TODO maybe have to pass this function in ??
//   is it ok not to clean anything here??
fun cleanChoiceName(choiceName: String) : String {
    var work = choiceName
    // Garfield may have the party name appended to the choice name, eg "Donald J. Trump / Michael R. Pence:Republican"
    val colonPos = work.indexOf(":")
    if (colonPos > 0)
        work = work.substring(0, colonPos)

    // TODO Boulder may have the rank in parenthesis, eg Aaron Brockett(1)
    //   but some canonical names have parentheses, eg "Jaclyn (Gabbel) Hurst"
    //   only remove if parenthesis contains a number
    val leftParen = work.indexOf("(")
    val rightParen = work.indexOf(")")
    if (leftParen > 0 && rightParen > 0 && leftParen < rightParen) {
        val inside = work.substring(leftParen+1, rightParen)
        if (inside.all { it.isDigit() }) {
            work = work.substring(0, leftParen) // truncate
        }
    }
    return work.trim()
}