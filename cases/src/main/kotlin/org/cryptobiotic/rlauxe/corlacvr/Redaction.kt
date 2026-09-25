package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.corlacvr.Redaction.Companion.GroupWithLines
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.emptyList
import kotlin.math.max
import kotlin.text.lowercase
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

interface RedactionIF {
    fun groups(): List<RedactedGroup>  // Aggregated redactions: make into pools
    fun redactedRows(): List<CvrRow>  // row redactions given in the CVR file
    fun nredactedCvrs(): Int // number of redacted cvrs given in CVR file
}

class EmptyRedaction: RedactionIF {
    override fun groups() = emptyList<RedactedGroup>()
    override fun redactedRows() = emptyList<CvrRow>()
    override fun nredactedCvrs() = 0
}

data class RedactionStrategy(val redactedRowsAlsoAggregated: Boolean = false,)

// just gather the raw info
open class Redaction(val strategy: RedactionStrategy = RedactionStrategy(), val show: Boolean = false): RedactionIF {
    var nRedactedRows = 0
    val redactedGroups = mutableMapOf<String, RedactedGroup>()
    val redactedGroupsSet = mutableMapOf<Set<Int>, RedactedGroup>()
    val redactedRows = mutableListOf<CvrRow>()

    private var redactionExtensions = 1
    private val showDontMatch = true

    override fun groups(): List<RedactedGroup> {
        val result = mutableListOf<RedactedGroup>()
        result.addAll(redactedGroups.values)
        result.addAll(redactedGroupsSet.values)
        return result
    }

    override fun redactedRows() = redactedRows

    // number of redacted cvrs given in CVR file
    // here you have to know if the redactedRows are also in an aggregation
    override fun nredactedCvrs(): Int {
        if (strategy.redactedRowsAlsoAggregated) return redactedRows().size
        return redactedRows().size + groups().sumOf{ it.ncards()}
    }

    fun addRedactedLine(line: CSVRecord, corlaRawCvrs: CorlaRawCvrs) {
        val row = corlaRawCvrs.parseHeader(line)
        // TODO you could look at which fields are non-null
       redactedRows.add(row)
    }

    fun addGroup(redacted:RedactedGroup) {
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

    open fun isRedaction(line: CSVRecord, corlaRawCvrs: CorlaRawCvrs): Boolean {
        val ballotType = corlaRawCvrs.getBallotType(line)

        if (line.get(0).startsWith("AGGREGATED")) {
            if (show) println("  ** redact: $line")
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs.schema)
            addGroup(redactedGroup)
            nRedactedRows++
            return true

        } else if (line.get(0).isEmpty()) { // (2020) Dolores, Phillips: these are subtotals, not redactions
            if (show) println("  ** discard (subtotal): $line")
            // Dolores
            //   ** discard: isEmpty CSVRecord [comment='null', recordNumber=1505, values=[, , , , , , , 341, 1089, 5, 1, 1, 0, 5, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 3, 0, 0, 0, 0, 338, 1064, 4, 4, 17, 0, 1, 0, 0, 1063, 319, 16, 16, 316, 1057, 316, 1067, 1077, 1117, 787, 578, 645, 542, 647, 537, 636, 534, 621, 547, 633, 733, 554, 808, 1147, 264, 631, 712, 588, 822, 407, 1005, 302, 1119, 826, 568, 960, 438, 751, 583, 505, 877, 189, 39, 144, 87, 340, 576]]
            // Phillips
            //   ** discard: isEmpty CSVRecord [comment='null', recordNumber=2566, values=[, , , , , , , 486, 1958, 3, 1, 7, 0, 24, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 463, 1980, 6, 0, 22, 0, 0, 0, 0, 402, 1965, 46, 12, 2006, 1964, 2056, 2039, 340, 312, 369, 247, 203, 558, 791, 383, 492, 1343, 644, 1279, 676, 1310, 641, 1300, 649, 1575, 425, 2002, 312, 1051, 1292, 1212, 1113, 2096, 322, 1103, 1233, 1166, 1247, 537, 1873, 541, 1853, 1665, 742, 1759, 635, 1398, 888, 764, 1608, 428, 135, 800, 206, 794, 746, 1032, 1190]]
            return true

        } else if (line.get(corlaRawCvrs.schema.nheaders).startsWith("*")) { // El Paso
            if (show) println("  ** redact *: $line")
            // ballot ids and ballot style, no vote info
            // El Paso
            //  [42220, 30, 69, 90, 30-69-90, 5091421250 - 45 (5091421250 - 45), 45, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *]]
            addRedactedLine(line, corlaRawCvrs)
            nRedactedRows++
            return true
        }

        val values = line.toList().subList(corlaRawCvrs.schema.nheaders, line.size())
        val hasRedacted = values.any { it.lowercase().startsWith("redacted") || it.lowercase().startsWith("redaction") }
        if (hasRedacted) {
            if (show) println("  ** hasRedacted: $line")
            // ballot ids and ballot style, no vote info
            // Eagle
            //   [9220, 2, 197, 34, 2-197-34, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
            // Jefferson
            //   [5415, 5, 23, 52, 5/23/1952, , 1222230030 - 35 (1222230030 - 35), 35, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , Redacted, Redacted, Redacted, , , , , , , , , , , , , , , , , Redacted, Redacted, , , Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , , , , , , , , ]]
            // Larimer
            //   [8470, 6, 9, 79, 6/9/1979, 2234935805 - 21 (2234935805 - 21), 21, REDACTED, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
            addRedactedLine(line, corlaRawCvrs)
            nRedactedRows++
            return true
        }

        val hasanX = values.any { it.startsWith("X") }
        if (hasanX) { // Douglas, Pitkin
            if (show) println("  ** redact X: $line")
            // ballot ids and ballot style, no vote info
            // Douglas
            //   [11377, 1, GEN-0130, 10, 1-GEN-0130-10, 74, 359 [05], X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, , , , , , , , , X, X, X, X, X, , , , , , X, X, X, X, X, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , ]]
            // Pitkin
            //   [3, 201, 1, 13, 201-1-13, 3056149010 - CFPD (3056149010 - CFPD), CFPD, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , , X, X, X, X]]
            addRedactedLine(line, corlaRawCvrs)
            nRedactedRows++
            return true
        }

        val hasanAsterisk = values.any { it.startsWith("*") }
        if (hasanAsterisk) { // anonymize_cvr
            if (show) println("  ** redact *: $line")
            // ballot ids and ballot style, no vote info
            // Douglas
            //   [11377, 1, GEN-0130, 10, 1-GEN-0130-10, 74, 359 [05], X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, , , , , , , , , X, X, X, X, X, , , , , , X, X, X, X, X, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , ]]
            // Pitkin
            //   [3, 201, 1, 13, 201-1-13, 3056149010 - CFPD (3056149010 - CFPD), CFPD, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , , X, X, X, X]]
            addRedactedLine(line, corlaRawCvrs)
            nRedactedRows++
            return true
        }

        val novotes = values.all { it.isEmpty() }
        if (novotes) { // Logan, Morgan, Pueblo, Rio Blanco, Summit, Weld
            if (show) println("  ** redact novotes: $line")
            // ballot ids and ballot style, no vote info
            addRedactedLine(line, corlaRawCvrs)
            nRedactedRows++
            return true
        }
        return false
    }

    companion object {
        val GroupWithLines = "GroupWithLines"
    }
}

// these are using local contest ids, candidate ids
data class RedactedGroup(val groupName: String, val firstCsv: CSVRecord, val schema: CvrSchema) {
    val candVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes

    var nlines = 0  // used by the accumulating group
    var fixedNcards: Int? = null  // when we are told how many cards are in the group
    // var style : CvrCardStyle? = null TODO
    var singleCards = true
    // var setNcards: Int? = null

    init {
        if (groupName.isEmpty())
            logger.warn{"RedactedGroup $groupName: ballotType.isEmpty()"}
        if (groupName != GroupWithLines)
            addVotes(firstCsv)
    }

    /* used externally to override
    fun setNcards(ncards: Int) {
        if (fixedNcards != null) { throw RuntimeException("Cant change ncards of a fixed group") }
        setNcards = ncards
    } */

    fun contests() = candVotes.keys.toSet()

    fun addVotes(line: CSVRecord): RedactedGroup {
        var colidx = schema.nheaders // start where the votes begin
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
                    val candidateVotes = candVotes.getOrPut(useContestIdx, { mutableMapOf() })
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
        other.candVotes.forEach { (contestId, otherCands) ->
            val mycands = candVotes.getOrPut(contestId, { mutableMapOf() })
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
        candVotes.forEach { (contestId, cands) ->
            val voteForN = schema.voteForNs[contestId]!!
            val minCardsForContest = roundUp(cands.values.sum() / voteForN.toDouble())
            minCards = max(minCards, minCardsForContest)
        }
        return minCards
    }

    fun totalVotes() = candVotes.values.map{ it.values }.flatten().sum()

    // TODO
    fun ncards():Int {
        return fixedNcards?: max(nlines, minCards())
    }

    override fun toString() = buildString {
        val contests = candVotes.map { it.key }.sorted()
        append("RedactedGroup('$groupName', nlines=$nlines, minCards= ${minCards()} totalVotes=${totalVotes()} singleCards = $singleCards, contests=${contests} )")
        // appendLine(csvRecord.toString())
    }

    fun rename(rename: String): RedactedGroup {
        val renamed = RedactedGroup(rename, this.firstCsv, this.schema)
        renamed.candVotes.putAll(this.candVotes)

        renamed.nlines = this.nlines // other.minCards(voteForNmap)
        renamed.singleCards = this.singleCards
        renamed.fixedNcards = this.fixedNcards

        return renamed
    }

    companion object {
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