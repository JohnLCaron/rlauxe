package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.cvr.Redaction.Companion.GroupWithLines
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.max
import kotlin.text.lowercase
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

open class Redaction(val show: Boolean = false) {
    var nRedactedRows = 0
    val redactedGroups = mutableMapOf<String, RedactedGroup>()
    val redactedGroupsSet = mutableMapOf<Set<Int>, RedactedGroup>()
    var groupWithLines: RedactedGroup? = null

    private var redactionExtensions = 1
    private val showDontMatch = true

    fun redactedGroups(): List<RedactedGroup> {
        val result = mutableListOf<RedactedGroup>()
        result.addAll(redactedGroups.values)
        result.addAll(redactedGroupsSet.values)
        // if (groupWithLines != null) result.add(groupWithLines!!)
        return result
    }

    fun addRedactedLine(line: CSVRecord, corlaCvrs: CorlaCvrs) {
        if (groupWithLines == null)
            groupWithLines = RedactedGroup(GroupWithLines, line, corlaCvrs.schema)
        val row = corlaCvrs.parseHeader(line)
        // TODO you could look at which fields are non-null
        groupWithLines!!.redactedRows.add(row)
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

    open fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotType = corlaCvrs.getBallotType(line)

        if (line.get(0).startsWith("AGGREGATED")) {
            if (show) println("  ** redact: $line")
            val redactedGroup = RedactedGroup(ballotType, line, corlaCvrs.schema)
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

        } else if (line.get(corlaCvrs.schema.nheaders).startsWith("*")) { // El Paso
            if (show) println("  ** redact *: $line")
            // ballot ids and ballot style, no vote info
            // El Paso
            //  [42220, 30, 69, 90, 30-69-90, 5091421250 - 45 (5091421250 - 45), 45, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *]]
            addRedactedLine(line, corlaCvrs)
            nRedactedRows++
            return true
        }

        val values = line.toList().subList(corlaCvrs.schema.nheaders, line.size())
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
            addRedactedLine(line, corlaCvrs)
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
            addRedactedLine(line, corlaCvrs)
            nRedactedRows++
            return true
        }

        val novotes = values.all { it.isEmpty() }
        if (novotes) { // Logan, Morgan, Pueblo, Rio Blanco, Summit, Weld
            if (show) println("  ** redact novotes: $line")
            // ballot ids and ballot style, no vote info
            addRedactedLine(line, corlaCvrs)
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
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes
    val redactedRows = mutableListOf<CvrRow>()

    var nlines = 0  // used by the accumulating group
    var fixedNcards: Int? = null  // when we are told what ncards is in the cvr file
    // var style : CvrCardStyle? = null TODO
    var singleCards = true
    var setNcards: Int? = null

    init {
        if (groupName.isEmpty())
            logger.warn{"RedactedGroup $groupName: ballotType.isEmpty()"}
        if (groupName != GroupWithLines)
            addVotes(firstCsv)
    }

    // used externally to override
    fun setNcards(ncards: Int) {
        if (fixedNcards != null) { throw RuntimeException("Cant change ncards of a fixed group") }
        setNcards = ncards
    }

    fun contests() = contestVotes.keys.toSet()

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

    fun ncards():Int {
        if (redactedRows.isNotEmpty()) return redactedRows.size
        return fixedNcards?: setNcards ?:max(nlines, minCards())
    }

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