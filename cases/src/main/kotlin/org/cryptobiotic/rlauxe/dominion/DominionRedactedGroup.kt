package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max

private val logger = KotlinLogging.logger("DominionRedactedGroup")

class DominionRedactedGroup(val ballotType: String, val voteForNs: Map<Int, Int>) {
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes
    private var exampleCsv : CSVRecord? = null // debugging

    var nlines: Int = 1  //used by the accumulating group
    var style : ExportCardStyle? = null

    fun contests() = contestVotes.keys.toSet()

    fun addVotes(schema: Schema, line: CSVRecord): DominionRedactedGroup {
        var colidx = schema.nheaders // skip over the first 6 or 7 columns
        while (colidx < line.size()) {
            val valueAtIdx = line.get(colidx)
            if (valueAtIdx.isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx  // same as contestID?
                val useContest = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // I think these are just regular Cvrs but the IRV contest was made seperate for privacy reasons
                    // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,1,0,0,0,0,1,1,0,0,0,0,1,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
                    println("*** IRV RedactedVotes shouldnt get here!")
                } else {
                    val candidateVotes = contestVotes.getOrPut(useContestIdx, { mutableMapOf() })
                    for (candIdx in 0 until useContest.ncols) {
                        val nvotes = line.get(useContest.startCol + candIdx).toInt()
                        val prev = candidateVotes[candIdx] ?: 0
                        candidateVotes[candIdx] = prev + nvotes
                    }
                    if (useContest.contestIdx == 31 && candidateVotes.values.sum() == 1)
                        print("")
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        exampleCsv = line
        return this
    }

    fun merge(other: DominionRedactedGroup): DominionRedactedGroup {
        require(this.ballotType == other.ballotType)
        other.contestVotes.forEach { (contestId, otherCands) ->
            val mycands = contestVotes.getOrPut(contestId, { mutableMapOf() })

            otherCands.forEach { (cand, otherVote) ->
                val myvotes = mycands[cand] ?: 0
                mycands[cand] = myvotes + otherVote
            }
        }
        this.nlines += other.nlines // other.minCards(voteForNmap)

        return this
    }

    // method #1
    // based on votes and voteForN, calculates the minimum number of cards that are in this redacted line
    fun minCards(): Int {
        var minCards = 0
        contestVotes.forEach { (contestId, cands) ->
            val voteForN = voteForNs[contestId]!!
            val minCardsForContest = roundUp(cands.values.sum() / voteForN.toDouble())
            minCards = max(minCards, minCardsForContest)
        }
        return minCards
    }

    fun totalVotes() = contestVotes.values.map{ it.values }.flatten().sum()

    override fun toString() = buildString {
        val contests = contestVotes.map { it.key }.sorted()
        append("RedactedGroup('$ballotType', ncontests=${contests.size} nlines=$nlines, totalVotes=${totalVotes()})")
        // appendLine(csvRecord.toString())
    }

    companion object {

        // method #2: specific to Boulder24
        fun makeAccumulator(starting: DominionRedactedGroup, accumName:String): DominionRedactedGroup {
            val accum = DominionRedactedGroup(accumName, starting.voteForNs)
            accum.merge(starting)

            // override with method #2
            if (starting.exampleCsv != null) {
                accum.nlines = parseNCards(starting.exampleCsv!!.values()[0])
            }
            return accum
        }

        // method #2: specific to Boulder25; "Redacted and Consolidated 10 Ballots"
        fun parseNCards(line:String): Int {
            if (!line.contains("Redacted and Consolidated")) return 1

            val tokens = line.split(" ")
            require(tokens.size > 3) { "unexpected redacted line $line" }
            val ncards = tokens[3].toInt()
            return ncards
        }
    }
}

// see TestCvrExportRedaction
fun assignStylesToRedactedGroups(export: DominionCvrCsvSummary) {
    val voteForNs = export.schema.contests.associate { it.contestIdx to it.voteForN }

    val styles: Map<String, ExportCardStyle> = export.exportCardStyles.associateBy { it.name }
    var totalLines = 0
    var totalVotes = 0
    var totalMinCards = 0
    export.redactedGroups.forEach { group ->
        val minCards = group.minCards()
        totalMinCards += minCards
        print("  $group, minCards=$minCards")
        totalLines += group.nlines
        totalVotes += group.totalVotes()

        val groupIds = group.contestVotes.filter { (key, cands) -> cands.any { it.value > 0 } }.map { it.key }.toSet()
        val match = styles[group.ballotType]
        if (match != null) {
            // filter out where contests where no votes were seen
            val missingInStyle = groupIds - match.contests
            val missingInRedaction = match.contests - groupIds
            if (missingInStyle.isNotEmpty() || missingInRedaction.isNotEmpty()) {
                logger.warn{" matching style has=${match.contests.size} contest" }
            }
            group.style = match
        } else {
            logger.warn{" *** no match for ${group.ballotType} with ${groupIds.size} non-zero contests "}
        }
        println()
    }
    println("ngroups = ${export.redactedGroups.size}")
    println("totalLines = $totalLines")
    println("sum votes = $totalVotes")
    println("sum minCards = $totalMinCards")

    // all redactions have 49 contests, probably put in 0 instead of blank
    // but can match DS name, and use that as the card style. test if any contests are non-zero
    // then add minCards cards for each style to the county pool
}