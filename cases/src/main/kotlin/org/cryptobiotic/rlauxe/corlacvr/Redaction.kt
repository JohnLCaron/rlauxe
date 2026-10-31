package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import kotlin.collections.emptyList
import kotlin.text.lowercase
import kotlin.text.startsWith

interface RedactionIF {
    fun groups(): List<RedactedGroup>  // Aggregated redactions: make into pools
    fun redactedRows(): List<CvrRow>  // row redactions given in the CVR file
    fun nredactedCvrs(): Int // number of redacted cvrs given in CVR file
    fun cardCountByContest() : Map<Int, Int> // contestId -> ncards in the redaction
}

class EmptyRedaction: RedactionIF {
    override fun groups() = emptyList<RedactedGroup>()
    override fun redactedRows() = emptyList<CvrRow>()
    override fun nredactedCvrs() = 0
    override fun cardCountByContest() = emptyMap<Int, Int> ()
}

// default is what our anonymizer needs
data class RedactionStrategy(
    val justGroups: Boolean = false, // for count redacted, just count the group(s) nsize, else just count the rows
    val hasStyle: Boolean?,  // override style detection, null mean we dont know a-priori
)
// 1. one aggregated group and redacted rows with ids and styles (our redactor)
// 1.1 older version: rows have unique ballotType but missing unredacted example, no style (LaPlata 2026p)
// 1.2 older version: rows have unique ballotType but missing unredacted example, no style, no precincts (Morgan 2026p)
// -> if we have one group and rows with styles, the group is the agg pool; the rows use their own style
// -> if we also have rows without styles, put them in the missing pool with subtotal missing votes - agg pool
// we have no examples (yet) of previous redaction having row styles, and also needed further redaction

// multiple redaction groups
// 2.1 aggregated groups by ballotType (derive style), no rows (Boulder 2020)
//     ncards in group is unknown.
// 2.2 aggregated groups by ballotType (has style), no rows (Boulder 2023)
//     ncards in group is unknown.
// 2.3 aggregated groups by ballotType A/B (has style), no rows (Boulder 2024) (TODO use BallotStyles)
//     ncards in group is unknown.
// 2.4 aggregated groups by unique ballotType, rows with no ids with BallotStyle (Boulder 2025/6)
//     the rows are aggregated by ballotType into groups, not kept as rows. ncards in group = nlines
// -> if we have one or more groups and no rows, get the rows from the manifest missing cvrs
// since we dont know which rows belong to which group, we combine the groups into the missing pool.
// the group sum should equals the missing votes

// 3. redacted rows with ids with ballotType
// 3.1 no style (2020: Adams, Arapahoe, Eagle, El Paso, Larimer, Logan, Morgan, Pueblo, Rio Blanco, Summit, Weld, )
// 3.1.1 Broomfield 2025 redacted all of type 1, no unredacted rows with that type
// 3.1.2 Larimer 2025 type 20 split A/B but redacted row only shows type 20
// 3.1.3 Weld 2026p redacted all of several types, no style
// 3.2 has style (2020: Douglas, Jefferson, Pitkin)
// -> if we have rows but no groups, all rows go into the missing pool using missing votes
// use the style for the individual rows, else use the missing pool style

// -> use row style if exists, then try ballotStyle
// -> if ballotTypes are unique, then we know the row style if it references one or hasStyle, otherwise not
// -> aggregated group is not exact unless it contains a single style

// Questions:
//   do rows have style information: yes if votes != emptyMap
//   do ballot types have unique styles: check BallotStyles.ballotTypes list.size == 1

open class Redaction(val strategy: RedactionStrategy = RedactionStrategy(false, null), val show: Boolean = false): RedactionIF {
    var linesIgnored = 0
    val redactedGroups = mutableMapOf<String, RedactedGroup>()
    val redactedGroupsSet = mutableMapOf<Set<Int>, RedactedGroup>()
    val redacted = mutableListOf<CvrRow>() // the rows must have imprintedId to match an mvr
    var hasStyle = strategy.hasStyle

    private var redactionExtensions = 1
    private val showDontMatch = true

    override fun groups(): List<RedactedGroup> {
        val result = mutableListOf<RedactedGroup>()
        result.addAll(redactedGroups.values)
        result.addAll(redactedGroupsSet.values)
        return result
    }

    override fun redactedRows() = redacted

    override fun cardCountByContest() : Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        redacted.forEach { row ->
            row.contestVotes.forEach { contestVote ->
                val accum = result.getOrDefault(contestVote.contestId, 0)
                result[contestVote.contestId] = accum + 1
            }
        }
        return result
    }

    // number of redacted cvrs given in CVR file
    // here you have to know if the redactedRows are also in an aggregation
    override fun nredactedCvrs(): Int {
        val groupRows = groups().sumOf{ it.ncards() }
        return if (strategy.justGroups) groupRows else redactedRows().size
    }

    fun addRedactedLine(line: CSVRecord, corlaCvrs: CorlaCountyCvrsIF) {
        val cvr = corlaCvrs.parseHeader(line)
        if (hasStyle != false) {
            cvr.findNonNull(corlaCvrs.schema, line)
        }
        redacted.add(cvr) // now the redacted rows know if they have styles or not, except for the ambiguous case of "all"

        // ballotStyles include redacted rows when they have style info
        if (cvr.contestVotes.isNotEmpty())
            corlaCvrs.ballotStyles().add(cvr)
    }

    // if you have a group, you expect that the redactedRows have contest info in them
    fun addGroup(redacted:RedactedGroup) {
        val rname =  redacted.groupName
        val existingGroup = redactedGroups[rname]
        if (existingGroup == null) {
            redactedGroups[rname] = redacted
        } else {
            // consider sending redacted rows into BallotStyles...
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

    open fun isRedaction(line: CSVRecord, corlaRawCvrs: CorlaCountyCvrsIF): Boolean {
        val ballotType = corlaRawCvrs.getBallotType(line)

        if (line.get(0).startsWith("AGGREGATED") || line.get(0).startsWith("AGGREGATION")) { // our aggregations
            if (show) println("  ** AGGREGATED: $line")
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs)
            addGroup(redactedGroup)
            linesIgnored++
            return true
        }

        if (line.get(0).isEmpty()) { // (2020) Dolores, Phillips: these are subtotals, not redactions
            if (show) println("  ** discard (subtotal): $line")
            // Dolores
            //   ** discard: isEmpty CSVRecord [comment='null', recordNumber=1505, values=[, , , , , , , 341, 1089, 5, 1, 1, 0, 5, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 3, 0, 0, 0, 0, 338, 1064, 4, 4, 17, 0, 1, 0, 0, 1063, 319, 16, 16, 316, 1057, 316, 1067, 1077, 1117, 787, 578, 645, 542, 647, 537, 636, 534, 621, 547, 633, 733, 554, 808, 1147, 264, 631, 712, 588, 822, 407, 1005, 302, 1119, 826, 568, 960, 438, 751, 583, 505, 877, 189, 39, 144, 87, 340, 576]]
            // Phillips
            //   ** discard: isEmpty CSVRecord [comment='null', recordNumber=2566, values=[, , , , , , , 486, 1958, 3, 1, 7, 0, 24, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 463, 1980, 6, 0, 22, 0, 0, 0, 0, 402, 1965, 46, 12, 2006, 1964, 2056, 2039, 340, 312, 369, 247, 203, 558, 791, 383, 492, 1343, 644, 1279, 676, 1310, 641, 1300, 649, 1575, 425, 2002, 312, 1051, 1292, 1212, 1113, 2096, 322, 1103, 1233, 1166, 1247, 537, 1873, 541, 1853, 1665, 742, 1759, 635, 1398, 888, 764, 1608, 428, 135, 800, 206, 794, 746, 1032, 1190]]
            return true
        }

        val values = line.toList().subList(corlaRawCvrs.schema.nheaders, line.size())
        val hasRedacted = values.any { it.lowercase().startsWith("redacted") || it.lowercase().startsWith("redaction") }
        if (hasRedacted) {
            if (show) println("  ** hasRedacted: $line")
            // Eagle no style
            //   [9220, 2, 197, 34, 2-197-34, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
            // Jefferson has Style
            //   [5415, 5, 23, 52, 5/23/1952, , 1222230030 - 35 (1222230030 - 35), 35, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , Redacted, Redacted, Redacted, , , , , , , , , , , , , , , , , Redacted, Redacted, , , Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, Redacted, , , , , , , , , , , , , , , , , , ]]
            // Larimer no style
            //   [8470, 6, 9, 79, 6/9/1979, 2234935805 - 21 (2234935805 - 21), 21, REDACTED, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
            addRedactedLine(line, corlaRawCvrs)
            linesIgnored++
            return true
        }

        val hasanX = values.any { it.startsWith("X") }
        if (hasanX) { // Douglas, Pitkin
            if (show) println("  ** redact X: $line")
            // Douglas has style
            //   [11377, 1, GEN-0130, 10, 1-GEN-0130-10, 74, 359 [05], X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, , , , , , , , , X, X, X, X, X, , , , , , X, X, X, X, X, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , ]]
            // Pitkin has style
            //   [3, 201, 1, 13, 201-1-13, 3056149010 - CFPD (3056149010 - CFPD), CFPD, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, , , , , , , , , X, X, X, X]]
            addRedactedLine(line, corlaRawCvrs)
            linesIgnored++
            return true
        }

        val hasanAsterisk = values.any { it.startsWith("*") }
        if (hasanAsterisk) { // El Paso, our anonymizer
            if (show) println("  ** redact *: $line")
            // El Paso has * in every column
            //  [42220, 30, 69, 90, 30-69-90, 5091421250 - 45 (5091421250 - 45), 45, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *]]
            addRedactedLine(line, corlaRawCvrs)
            linesIgnored++
            return true
        }

        val novotes = values.all { it.isEmpty() }
        if (novotes) { // Logan, Morgan, Pueblo, Rio Blanco, Summit, Weld
            if (show) println("  ** redact novotes: $line")
            // ballot ids and ballot style, no vote info
            addRedactedLine(line, corlaRawCvrs)
            linesIgnored++
            return true
        }
        return false
    }

    override fun toString() = buildString {
        appendLine("${this@Redaction.javaClass.simpleName}: linesIgnored=${redacted.size}, nredactedGroups=${redactedGroups.size}, nredactedCvrs=${nredactedCvrs()}")
        redactedGroups.forEach { appendLine("    $it")}
    }

    companion object {
        val GroupWithLines = "GroupWithLines"
        val logger = KotlinLogging.logger("Redaction")
    }
}

