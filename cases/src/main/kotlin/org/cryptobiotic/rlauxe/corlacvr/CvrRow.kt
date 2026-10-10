package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle

// votedFor - list of candidate ids voted for, means candidate got 1 vote
// note that these contestIds and candIds are internal to this file, and must be cross referenced
// with canonical contest/candidate name
// they will match the internal CardStyles and Redacted Groups
data class ContestVotes(val contestId: Int, val votedFor: List<Int>) {
    // we want map of candidate ids to votes;  cant use for IRV
    fun candVotes(): Map<Int, Int> =
        votedFor.map { Pair(it, 1) }.toMap()
}

// CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,PrecinctPortion,BallotType
data class CvrRow(
    val headerValues: List<String>, // first schema.nheaders
    val cvrNumber: Int,
    val tabulatorNum: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,      // format strings with ="value"
    val ballotType: String,       // might have to generate this ourselves?
    val precinctPortion: String?, // optional
) {
    // equivilent to Map<contestId, IntArray>, where candId =
    var contestVotes = mutableListOf<ContestVotes>() // equivilent to Map<contestId, IntArray>

    fun contests() = contestVotes.map { it.contestId }.toSet()

    fun contestVotesFor(contestId: Int): ContestVotes? {
        return contestVotes.find{ it.contestId == contestId}
    }

    fun candVote(contestId: Int, candId: Int): Int? {
        val contestVote = contestVotes.find{ it.contestId == contestId}
        if (contestVote == null) return null
        return if (contestVote.candVotes().contains(candId)) 1 else 0
    }

    fun addVotes(schema: CvrSchema, line: CSVRecord, lineno: Int): CvrRow {
        var colidx = schema.nheaders // skip over the first n columns
        while (colidx < schema.columns.size && colidx < line.size()) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest: SchemaContestInfo = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // cvr.raw = makeRaw(line, useContest.startCol, useContest.ncols)
                    val candVotes = makeIrvVotes(schema, line, lineno, useContest)
                    contestVotes.add(ContestVotes(useContestIdx, candVotes))
                } else {
                    val candVotes = makeRegularVotes(schema, line, lineno, useContest)
                    contestVotes.add(ContestVotes(useContestIdx, candVotes))
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        return this
    }

    fun findNonNull(schema: CvrSchema, line: CSVRecord): CvrRow {
        var colidx = schema.nheaders // skip over the first n columns
        while (colidx < schema.columns.size && colidx < line.size()) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest: SchemaContestInfo = schema.contests[useContestIdx]
                // record that the contest was on this redacted row
                contestVotes.add(ContestVotes(useContestIdx, emptyList()))
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        return this
    }

    // assume a vote is 0 or 1
    // return list of candidates voted for; the candidates are numbered within this contest
    fun makeRegularVotes(schema: CvrSchema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val votes = mutableListOf<Int>()
        for (idx in 0 until exportContest.ncols) { // so i in [0..ncands)
            val colno = exportContest.startCol + idx
            if (!schema.writeIns.contains(colno)) { // dont record write-ins
                val colValueS = line.get(colno)
                try {
                    val colValue = colValueS.toInt()
                    if (colValue > 0) votes.add(idx)
                } catch (e: NumberFormatException) {
                    logger.warn {
                        "Cant parse '$colValueS' at col $colno line $lineno; probably didnt catch the redacted line; " +
                                "\nfilename=${schema.inputSource}\n       $line" }
                    return emptyList()
                }
            }
        }
        return votes
    }

    //                             exportContest.startCol, start
    //                            exportContest.ncols, count
    //                            exportContest.nchoices, ncands
    fun makeIrvVotes(schema: CvrSchema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val raw = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) {
            raw.add(line.get(exportContest.startCol + i).toInt())
        }
        val cands = mutableListOf<IntArray>()
        for (cand in 0 until exportContest.nchoices) {
            val candArray = IntArray(exportContest.nchoices) { i -> raw[cand + exportContest.nchoices * i] }
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

    fun voteFor(contest: Int): ContestVotes? = contestVotes.find { it.contestId == contest }

    fun show() = buildString {
        append("$cvrNumber, ")
        append("$tabulatorNum, ")
        append("$batchId, ")
        append("$recordId, ")
        append("$imprintedId, ")
        append("$precinctPortion, ")
        append("$ballotType, ")
        contestVotes.forEach {
            append("${it.contestId}: ${it.votedFor.joinToString(",")}, ")
        }
    }

    fun testImprintedIdFormat(): Boolean {
        return imprintedId == "${tabulatorNum}-${batchId}-${recordId}"
    }

    //// use CorlaCvrConverter when mapping to corla canonical contests
    fun convertToCard(): AuditableCard {
        val votes = this.contestVotes.map { cv -> Pair(cv.contestId, cv.votedFor.toIntArray()) }.toMap()

        // TODO location
        return AuditableCard.fromVotes(this.imprintedId, null, 0, 0L, false, styleId = CardStyle.fromCvrStyle.id(),
            votes=votes, poolId=null).setStyle(CardStyle.fromCvrStyle)
    }

    companion object {
        val header = "cvrNumber, tabulatorNum, batchId, recordId, imprintedId, ballotType, contest:votes"
        private val logger = KotlinLogging.logger("CvrRow")
    }
}