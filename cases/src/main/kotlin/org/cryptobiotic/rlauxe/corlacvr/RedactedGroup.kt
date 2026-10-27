package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.max

// TODO just keep by (ballotType, contests). can rename at end if needed
// these are using local contest ids, candidate ids
// comes from a subtotal line in the redacted cvrs
data class RedactedGroup(val groupName: String, val firstCsv: CSVRecord, val corlaCvrs: CorlaCountyCvrsIF) {
    // why not ContestTabulation ?? because we dont have info ??
    val candVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes

    var nlines = 0  // used by the accumulating group
    var fixedNcards: Int? = null  // when we are told how many cards are in the group
    // var style : CvrCardStyle? = null TODO
    var singleCards = true
    var setNcards = 0

    init {
        if (groupName.isEmpty())
            logger.warn{"RedactedGroup $groupName: ballotType.isEmpty()"}
        if (groupName != Redaction.GroupWithLines)
            addVotes(firstCsv)
    }

    fun contests() = candVotes.keys.toSet()

    // TODO
    fun ncards():Int {
        return fixedNcards?: max(max(nlines, minCards()), setNcards)
    }

    fun addVotes(line: CSVRecord): RedactedGroup {
        val schema = corlaCvrs.schema
        var colidx = schema.nheaders // start where the votes begin
        while (colidx < line.size()) {
            val valueAtIdx = line.get(colidx)
            if (valueAtIdx.isNotEmpty()) {
                val useContest = schema.getContestAt(colidx)

                if (useContest.isIRV) {
                    // I think these are just regular Cvrs but the IRV contest was made seperate for privacy reasons
                    // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,1,0,0,0,0,1,1,0,0,0,0,1,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
                    logger.warn{"*** IRV RedactedVotes shouldnt get here!"}
                } else {
                    val candidateVotes = candVotes.getOrPut(useContest.contestIdx, { mutableMapOf() })
                    for (candIdx in 0 until useContest.ncols) {
                        val nvotestr = line.get(useContest.startCol + candIdx)
                        if (nvotestr.startsWith("AGG"))
                            print("")
                        val nvotes = nvotestr.toInt()
                        val prev = candidateVotes[candIdx] ?: 0
                        candidateVotes[candIdx] = prev + nvotes
                        if (nvotes > 1)
                            singleCards = false
                    }
                    //if (useContestIdx == 31 && candidateVotes.values.sum() == 1)
                    //    logger.debug{"*** contestIdx == 31 votes = ${candidateVotes.values.sum()}"}
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }

        fixedNcards = parseNCards(line.values()[0])
        nlines++
        return this
    }

    // merge two RedactedGroups together
    fun merge(other: RedactedGroup): RedactedGroup  {

        // require (this.ballotType == other.ballotType)
        other.candVotes.forEach { (contestId, otherCands) ->
            val mycands = candVotes.getOrPut(contestId, { mutableMapOf() })
            otherCands.forEach { (cand, otherVote) ->
                val myvotes = mycands[cand] ?: 0
                mycands[cand] = myvotes + otherVote
            }
        }
        this.nlines += other.nlines // other.minCards(voteForNmap)
        this.singleCards = this.singleCards && other.singleCards

        return this
    }

    fun minCards(): Int {
        var minCards = 0
        candVotes.forEach { (contestId, cands) ->
            val voteForN = corlaCvrs.schema.voteForNs[contestId]!!
            val minCardsForContest = roundUp(cands.values.sum() / voteForN.toDouble())
            minCards = max(minCards, minCardsForContest)
        }
        return minCards
    }

    fun totalVotes() = candVotes.values.map{ it.values }.flatten().sum()

    override fun toString() = buildString {
        val contests = candVotes.map { it.key }.sorted()
        append("RedactedGroup('$groupName', ncards=${ncards()}, nlines=$nlines, minCards= ${minCards()} totalVotes=${totalVotes()} singleCards = $singleCards, contests=${contests} )")
        // appendLine(csvRecord.toString())
    }

    // if we wait until all cards are done, we can us same name as the final CardStyles
    fun rename(rename: String): RedactedGroup {
        val renamed = RedactedGroup(rename, this.firstCsv, this.corlaCvrs)
        renamed.candVotes.putAll(this.candVotes)

        renamed.nlines = this.nlines // other.minCards(voteForNmap)
        renamed.singleCards = this.singleCards
        renamed.fixedNcards = this.fixedNcards

        return renamed
    }

    companion object {
        val logger = KotlinLogging.logger("RedactedGroup")

        // method #2: specific to Boulder25,26; "Redacted and Consolidated 10 Ballots"
        // TODO move to RedactionBoulder?
        fun parseNCards(line:String): Int? {
            if (!line.contains("Redacted and Consolidated")) return null

            val tokens = line.split(" ")
            if (tokens.size < 4) return null
            val ncards = tokens[3].toInt() // TODO check for parse error
            return ncards
        }
    }
}