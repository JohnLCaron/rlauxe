package org.cryptobiotic.rlauxe.corlacvr

import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.corlaInput.munge
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.lang.StrictMath.sqrt

enum class CvrHeader { cvrnumber, tabulatornum, batchid, recordid, imprintedid, countinggroup, precinctportion, ballottype }

class CvrSchema(val inputSource: String,
                val headers: List<String>, // header names, lowercase
                private val columns: List<SchemaColumnInfo>, // choice columns only
                val contests: List<SchemaContestInfo>,
                val voteForNs: Map<Int, Int>) {

    val nheaders = headers.size
    val ncolumns = columns.size
    val writeIns : Set<Int> = columns.filter{ it.choiceName.lowercase().contains("write-in") }.map { it.colIdx }.toSet()

    val headerAt = mutableMapOf<Int, CvrHeader>() // idx -> header
    val headerIdx = mutableMapOf<CvrHeader, Int>()  // header -> idx

    init {
        CvrHeader.entries.forEach { fld ->
            val idx = headers.indexOf(fld.name)
            if (idx >= 0) {
                headerAt[idx] = fld
                headerIdx[fld] = idx
            }
        }

        //if (nheaders != headerIdx[CvrHeader.ballottype]!! + 1)
        //    println("HEY HEY different number of headers for $inputSource")
    }

    fun choices(contestId: Int): List<String> {
        val contest = contests.find{ it.contestIdx == contestId }
        val result = mutableListOf<String>()
        if (contest != null) {
            for (colIdx in contest.startCol until contest.startCol + contest.nchoices) {
                result.add( columns[colIdx].choiceName )
            }
        }
        return result
    }

    fun getContestAt(colIdx: Int): SchemaContestInfo {
        val col = columns.find { it.colIdx == colIdx }!!
        return contests[col.contestIdx]
    }

    fun columnsFor(contest: SchemaContestInfo): List<SchemaColumnInfo> {
        return columns.subList(contest.startCol - nheaders, contest.startCol + contest.ncols - nheaders)
    }

    /* given a choice name, what is its index in the contest, aka id ?
    fun choiceIdx(choiceName: String): Int {
        val column = columns.find { it.choiceName == choiceName }
        if (column == null)
            throw RuntimeException("cant find choice $choiceName")
        // find contest it belongs to
        var startCol = 0
        contests.forEach {
            if (it.startCol <= column.colno) startCol = it.startCol
        }
        return column.colno - startCol
    } */

    fun voteFor(contestId: Int, cvr: CvrRow): List<String> {
        val choices = choices(contestId)
        val contestVotes = cvr.voteFor(contestId)
        val result = mutableListOf<String>()
        contestVotes?.votedFor?.forEach { result.add( choices[it]) } // could barf if malformed
        return result.sorted()
    }

    fun showColumns() = buildString {
        println(SchemaColumnInfo.header)
        headers.forEach { println("  ${it}") }
        println()
        println(SchemaColumnInfo.colHeader)
        columns.forEach { println("  ${ it.showColumn() }") }
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
data class SchemaColumnInfo(val contestIdx: Int, val colIdx:Int, val contest: String, val choiceName: String, val partyName: String) {
    fun showHeader(): String {
        return "${nfn(colIdx, 3)}, ${trunc(partyName, 20)}"
    }
    fun showColumn(): String {
        return "${nfn(colIdx, 3)}, ${trunc(contest, contestWidth)}, ${nfn(contestIdx, 3)}, " +
                "${trunc(choiceName, choiceWidth)}, ${trunc(partyName, 5)}"
    }

    companion object {
        val header = "colIdx,          header name"
        val colHeader = "colIdx, ${trunc("contest", contestWidth)}, idx, ${trunc("choice", choiceWidth)}, party"
    }
}

data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int,
    val voteForN: Int, val isIRV: Boolean) {
    val nchoices: Int

    init {
        nchoices = if (!isIRV) ncols else {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt() // WTF sqrt? Idiot Dominion format for IRV
        }
    }

    fun show(): String {
        return "${nfn(contestIdx, 5)}, ${trunc(contestName, contestWidth)},      ${nfn(startCol, 3)},   ${nfn(ncols, 3)}, $isIRV"
    }

    companion object {
        val header = "contestIdx, ${trunc("contestName", contestWidth-3)}, startCol, ncols, isIRV, voteForN"

        fun fromOrgName(contestIdx: Int, orgName: String, startCol: Int, ncols: Int,): SchemaContestInfo  {
            val isIRV = orgName.contains("Number of ranks=") // Boulder 2023 IRV
            val (parsedName, nwinners) = if (isIRV) parseIrvContestName(orgName) else parseContestNameAndVoteFor(orgName)
            return SchemaContestInfo(contestIdx, parsedName, startCol, ncols, nwinners, isIRV)
        }
    }
}

fun makeSchemaFromDominionHeaders(inputSource: String, contests: CSVRecord, choices: CSVRecord, headerLine: CSVRecord): CvrSchema {
    require(contests.size() <= choices.size())
    require(headerLine.size() == choices.size())

    val nheaders = contests.toList().indexOfFirst { !it.trim().isEmpty() }
    val headers = headerLine.values().take(nheaders).map { it.trim().lowercase() }

    var currContestName = ""
    var currContestIdx = 0
    var currContestStart = nheaders

    val ccontests = mutableListOf<SchemaContestInfo>()
    val columns = mutableListOf<SchemaColumnInfo>()

    for (colidx in nheaders until contests.size()) {
        val contestName = contests.get(colidx).trim()
        val choiceName = cleanChoiceName(choices.get(colidx))
        val partyName = headerLine.get(colidx).trim()

        if (currContestName.isEmpty()) currContestName = contestName

        // running through munge to try to detect minor typos in the contest names
        if (munge(contestName) != munge(currContestName)) {
            // save the old contest
            ccontests.add( SchemaContestInfo.fromOrgName(currContestIdx, currContestName, currContestStart, colidx-currContestStart) )
            // start the new contest
            currContestName = contestName
            currContestStart = colidx
            currContestIdx++
        }

        val colInfo = SchemaColumnInfo(currContestIdx, colidx, contestName, choiceName, partyName)
        columns.add(colInfo)
    }
    // save the last contest
    ccontests.add( SchemaContestInfo.fromOrgName(currContestIdx, currContestName, currContestStart, headerLine.size()-currContestStart) )

    val voteForNs = ccontests.associate { it.contestIdx to it.voteForN }
    return CvrSchema(inputSource, headers, columns, ccontests, voteForNs)
}

/* firstRow for debugging
fun makeCvrSchema(inputSource: String, contests: CSVRecord, choices: CSVRecord, headerLine: CSVRecord): CvrSchema {
    require(contests.size() <= choices.size())
    require(headerLine.size() == choices.size())

    val columns = mutableListOf<SchemaColumnInfo>()
    for (idx in 0 until contests.size()) {
        val choiceName = cleanChoiceName(choices.get(idx))
        val partyName = if (idx < headerLine.size()) headerLine.get(idx).trim() else ""
        columns.add( SchemaColumnInfo(idx, contests.get(idx).trim(), choiceName, partyName))
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colIdx
    val headers = headerLine.values().take(nheaders).map { it.lowercase() }

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<SchemaContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        // running through munge to try to detect minor typos in the contest names
        if (colidx != skipIdx && munge(col.contest) != munge(currContestName)) {
            ccontests.add( SchemaContestInfo.fromOrgName(currContestIdx, currContestName, startIdx, colidx-startIdx))
            startIdx = colidx
            currContestIdx++
        }
        col.contestIdx = currContestIdx
        currContestName = col.contest
    }
    ccontests.add( SchemaContestInfo.fromOrgName(currContestIdx, currContestName, startIdx, columns.size-startIdx))

    val voteForNs = ccontests.associate { it.contestIdx to it.voteForN }
    return CvrSchema(inputSource, headers, columns, ccontests, voteForNs)
} */

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


// heres where we remove the (Vote For=N)
fun parseContestNameAndVoteFor(name: String) : Pair<String, Int> {
    if (name.contains("(Vote For1")) {
        val clean = name.substringBefore("(")
        return Pair(clean.trim(), 1)
    }
    if (!name.contains("(Vote For=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Vote For=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(")").toInt()
    return Pair(namet, ncand)
}

// City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)
fun parseIrvContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Number of positions=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Number of positions=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(",").toInt()
    return Pair(namet, ncand)
}
