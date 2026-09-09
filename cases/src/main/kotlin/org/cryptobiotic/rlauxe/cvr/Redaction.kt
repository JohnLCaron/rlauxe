package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.plus
import kotlin.collections.toList
import kotlin.math.max
import kotlin.text.lowercase
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

interface RedactionIF {
    val nlines: Int
    fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean
    fun redactedGroups(): List<RedactedGroup>
}

open class Redaction(val show: Boolean = false) : RedactionIF {
    override var nlines = 0
    val redactedGroups = mutableMapOf<String, RedactedGroup>()
    val redactedGroupsSet = mutableMapOf<Set<Int>, RedactedGroup>()

    private var redactionExtensions = 1
    private val showDontMatch = true

    override fun redactedGroups() =  (redactedGroups.values + redactedGroupsSet.values).toList()

    fun addToGroups(redacted:RedactedGroup) {
        val rname =  redacted.groupName
        val existingGroup = redactedGroups[rname]
        if (existingGroup == null) {
            redactedGroups[rname] = redacted
        } else {
            if (existingGroup.contests() == redacted.contests()) {
                existingGroup.merge(redacted)
            } else {
                val existingGroup = redactedGroupsSet[redacted.contests()]
                if (existingGroup == null) {
                    val rname =  "${redacted.groupName}+$redactionExtensions"
                    redactionExtensions++
                    redactedGroupsSet[redacted.contests()] = redacted.rename( rname)
                } else {
                    existingGroup.merge(redacted)
                }
            }
        }
    }

    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotType = corlaCvrs.getBallotType(line)

        if (line.get(0).startsWith("AGGREGATED")) {
            if (show) println("  ** redact: $line")
            val redactedGroup = RedactedGroup(ballotType, line, corlaCvrs.schema)
            addToGroups(redactedGroup)
            nlines++
            return true

        } else if (line.get(0).isEmpty()) { // (2020) Dolores, Phillips: these are subtotals, not redactions
            if (show) println("  ** discard: isEmpty $line")
            nlines++
            return true

        } else if (line.get(corlaCvrs.schema.nheaders).startsWith("*")) { // El Paso
            if (show) println("  ** redact *: $line")
            nlines++
            return true
        }

        val values = line.toList().subList(corlaCvrs.schema.nheaders, line.size())
        val hasRedacted = values.any { it.lowercase().startsWith("redacted") || it.lowercase().startsWith("redaction") }
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

// these are using local contest ids, candidate ids
data class RedactedGroup(val groupName: String, val firstCsv: CSVRecord, val schema: CvrSchema) {
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes

    var nlines = 0  // used by the accumulating group
    var fixedNcards: Int? = null  // when we are told what ncards is in the cvr file
    var style : CvrCardStyle? = null
    var singleCards = true
    var setNcards: Int? = null

    init {
        if (groupName.isEmpty())
            logger.warn{"RedactedGroup $groupName: ballotType.isEmpty()"}
        addVotes(firstCsv, schema)
    }

    // used externally to override
    fun setNcards(ncards: Int) {
        if (fixedNcards != null) { throw RuntimeException("Cant change ncards of a fixed group") }
        setNcards = ncards
    }

    fun contests() = contestVotes.keys.toSet()

    fun addVotes(line: CSVRecord, schema: CvrSchema): RedactedGroup {
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
                        if (nvotes > 1)
                            singleCards = false
                    }
                    if (useContestIdx == 31 && candidateVotes.values.sum() == 1)
                        logger.debug{"*** contestIdx == 31 votes = ${candidateVotes.values.sum()}"}
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
        if (fixedNcards != null || other.fixedNcards != null) { throw RuntimeException("Cant merge fixed group") }

        // require (this.ballotType == other.ballotType)
        other.contestVotes.forEach { (contestId, otherCands) ->
            val mycands = contestVotes.getOrPut(contestId, { mutableMapOf() })
            otherCands.forEach { (cand, otherVote) ->
                val myvotes = mycands[cand] ?: 0
                mycands[cand] = myvotes + otherVote
            }
        }
        this.nlines += other.nlines // other.minCards(voteForNmap)
        this.singleCards = this.singleCards && other.singleCards
        // TODO can you merged fixed groups ?? this.fixedNcards += other.fixedNcards ?: 0

        return this
    }

    fun minCards(): Int {
        var minCards = 0
        contestVotes.forEach { (contestId, cands) ->
            val voteForN = schema.voteForNs[contestId]!!
            val minCardsForContest = roundUp(cands.values.sum() / voteForN.toDouble())
            minCards = max(minCards, minCardsForContest)
        }
        return minCards
    }

    fun totalVotes() = contestVotes.values.map{ it.values }.flatten().sum()

    fun ncards() = fixedNcards?: setNcards ?:max(nlines, minCards())

    override fun toString() = buildString {
        val contests = contestVotes.map { it.key }.sorted()
        append("RedactedGroup('$groupName', ncards=${ncards()}, nlines=$nlines, minCards= ${minCards()} totalVotes=${totalVotes()} singleCards = $singleCards, contests=${contests} )")
        // appendLine(csvRecord.toString())
    }

    fun rename(rename: String): RedactedGroup {
        val renamed = RedactedGroup(rename, this.firstCsv, this.schema)
        renamed.contestVotes.putAll(this.contestVotes)

        renamed.nlines = this.nlines // other.minCards(voteForNmap)
        renamed.singleCards = this.singleCards
        renamed.fixedNcards = this.fixedNcards

        return renamed
    }

    companion object {
        // method #2: specific to Boulder25,26; "Redacted and Consolidated 10 Ballots"
        fun parseNCards(line:String): Int? {
            if (!line.contains("Redacted and Consolidated")) return null

            val tokens = line.split(" ")
            if (tokens.size < 4) return null
            val ncards = tokens[3].toInt() // TODO check for parse error
            return ncards
        }
    }
}