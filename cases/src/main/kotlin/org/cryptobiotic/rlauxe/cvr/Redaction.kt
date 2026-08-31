package org.cryptobiotic.rlauxe.cvr

import com.github.michaelbull.result.valuesOf
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.max
import kotlin.text.lowercase
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

interface RedactionIF {
    val nlines: Int
    fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean
}

// standard Redactor, eg for votedatabase
class Redaction(val show: Boolean = false) : RedactionIF {
    override var nlines = 0

    // "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotStyle = corlaCvrs.readColumn(line, "BallotType") ?: "noBallotType"

        if (line.get(0).startsWith("AGGREGATED")) {
            if (show) println("  ** redact: $line")
            val redactedGroup = RedactedGroup("AGGREGATED", corlaCvrs.schema.voteForNs).addVotes(corlaCvrs.schema, line)
            corlaCvrs.ballotStyles.add(redactedGroup)
            nlines++
            return true

        } else if (line.get(0).isEmpty()) { // (2020) Boulder, Dolores; has votes, presumably the sum of the redactions
            if (show) println("  ** redact: isEmpty $line")

            val redactedGroup = RedactedGroup("redacted$nlines", corlaCvrs.schema.voteForNs).addVotes(corlaCvrs.schema, line)
            corlaCvrs.ballotStyles.add(redactedGroup)
            nlines++
            return true

        } else if (line.get(corlaCvrs.schema.nheaders).startsWith("*")) { // El Paso
            if (show) println("  ** redact *: $line")
            nlines++
            return true
        }

        val values = line.toList().subList(corlaCvrs.schema.nheaders, line.size())
        val hasRedacted = values.any { it.lowercase().startsWith("redacted") }
        if (hasRedacted) {
            if (show) println("  ** hasRedacted: $line")
            nlines++
            return true
        }

        val hasanX = values.any { it.startsWith("X") }
        if (hasanX) { // Douglas, Pitkin
            if (show) println("  ** redact X: $line")
            nlines++
            return true
        }
        return false
    }
}

class RedactedGroup(val ballotType: String, val voteForNs: Map<Int, Int>) {
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes
    private var exampleCsv : CSVRecord? = null // debugging
    private var nlines: Int = 1  // used by the accumulating group
    var style : CvrCardStyle? = null
    var singleCards = true

    init {
        if (ballotType.isEmpty())
            println("RedactedGroup $ballotType: ballotType.isEmpty()")
    }

    fun contests() = contestVotes.keys.toSet()

    fun addVotes(schema: CvrSchema, line: CSVRecord): RedactedGroup {
        var colidx = schema.nheaders // skip over the first 6 or 7 columns
        while (colidx < line.size()) {
            val valueAtIdx = line.get(colidx)
            if (valueAtIdx.isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx  // same as contestID?
                val useContest = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // I think these are just regular Cvrs but the IRV contest was made seperate for privacy reasons
                    // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,1,0,0,0,0,1,1,0,0,0,0,1,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
                    logger.warn{"*** IRV RedactedVotes shouldnt get here!"}
                } else {
                    val candidateVotes = contestVotes.getOrPut(useContestIdx, { mutableMapOf() })
                    for (candIdx in 0 until useContest.ncols) {
                        val nvotes = line.get(useContest.startCol + candIdx).toInt()
                        val prev = candidateVotes[candIdx] ?: 0
                        candidateVotes[candIdx] = prev + nvotes
                        if (nvotes > 1) singleCards = false
                    }
                    if (useContestIdx == 31 && candidateVotes.values.sum() == 1)
                        logger.debug{"*** contestIdx == 31 votes = ${candidateVotes.values.sum()}"}
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        exampleCsv = line
        return this
    }

    // merge two RedactedGroups together
    fun merge(other: RedactedGroup): RedactedGroup  {
        // require (this.ballotType == other.ballotType)
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

    fun ncards() = max(nlines, minCards())

    override fun toString() = buildString {
        val contests = contestVotes.map { it.key }.sorted()
        append("RedactedGroup('$ballotType', contests=${contests} nlines=$nlines, minCards= ${minCards()} totalVotes=${totalVotes()} singleCards = $singleCards)")
        // appendLine(csvRecord.toString())
    }

    companion object {

        // method #2: specific to Boulder24
        fun makeAccumulator(starting: RedactedGroup, accumName:String): RedactedGroup {
            val accum = RedactedGroup(accumName, starting.voteForNs)
            accum.merge(starting)

            // override with method #2
            if (starting.exampleCsv != null) {
                accum.nlines = parseNCards(starting.exampleCsv!!.values()[0])
            }
            return accum
        }

        // method #2: specific to Boulder25,26; "Redacted and Consolidated 10 Ballots"
        fun parseNCards(line:String): Int {
            if (!line.contains("Redacted and Consolidated")) return 1

            val tokens = line.split(" ")
            require(tokens.size > 3) { "unexpected redacted line $line" }
            val ncards = tokens[3].toInt()
            return ncards
        }
    }
}