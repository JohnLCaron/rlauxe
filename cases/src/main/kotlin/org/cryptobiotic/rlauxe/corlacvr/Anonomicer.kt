package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.auditcenter.makeRealContestInfo
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromCandVotes
import java.io.File
import kotlin.math.max
import kotlin.random.Random

class Anonomicer(
    val corlaCvrs: CorlaRawCvrsIF,
    val minBallots: Int = MIN_BALLOTS_DEFAULT,
    val removePrecinctCol: Boolean = false,
) {
    constructor(input: String) : this( readCorlaCvrs(input), MIN_BALLOTS_DEFAULT)

    val schema = corlaCvrs.schema
    val hasPrecinct = schema.headerIdx[CvrHeader.precinctportion] != null
    val infos: Map<Int, ContestInfo>

    val commonStyles: List<PrecinctUniqueStyle>
    val agg: Aggregate
    
    init {
        infos = corlaCvrs.makeRealContestInfo()

        val precinctStyles = findPrecinctStyles(corlaCvrs, infos)
        commonStyles = precinctStyles.map { it.uniques.values }.flatten().filter { it.ncards >= minBallots }

        val rareStyles = precinctStyles.map { it.uniques.values }.flatten().filter { it.ncards < minBallots }
        agg = Aggregate(rareStyles)
        agg.balanceContests()
        agg.finish()
    }

    fun redact(outputFile: String) {
        // Columns that carry no information in the output and should be omitted entirely.
        // CountingGroup is always removed.
        // PrecinctPortion is removed when not redacting on precinct, because every row has it blanked in that case.

        File(outputFile).bufferedWriter(Charsets.UTF_8).use { bwriter ->
            // write headers TODO remove headers
            corlaCvrs.headers().forEach {
                bwriter.write(it)
                bwriter.newLine()
            }

            corlaCvrs.cvrs().forEachIndexed { idx, row ->
                if (agg.isRedacted(row)) {
                    bwriter.write(writeRow(row, true))
                } else {
                    // write unredacted
                    bwriter.write(writeRow(row, false))
                }
            }

            if (agg.hasRedaction()) {
                val aggTabs = agg.redactedAggregation()
                val last = schema.nchoices
                val aggrow = buildString {
                    append("AGGREGATED,")
                    repeat(schema.nheaders - 2) { append(",") }
                    append("AGGREGATED,")
                    var count = 0
                    schema.contests.forEach { scontest ->
                        val tab = aggTabs[scontest.contestIdx]
                        repeat(scontest.ncols) {
                            count++
                            if (tab == null) {
                                if (count != last) append(",")
                            } else {
                                val vote = tab.votes[it] ?: 0
                                append("$vote")
                                if (count != last) append(",")
                            }
                        }
                    }
                    appendLine()
                }
                bwriter.write(aggrow)
            }
        }
    }

    fun writeRow(row: CvrRow, redacted: Boolean) = buildString {
        append(corlaCvrs.csvHeader(row, redactPrecinct = removePrecinctCol))
        val last = schema.nchoices
        val rowMap: Map<Int, List<Int>> = row.contestVotes.map { Pair(it.contestId, it.votedFor) }.toMap()
        var count = 0
        schema.contests.forEach { scontest ->
            val candVotes = rowMap[scontest.contestIdx]
            repeat(scontest.ncols) {
                count++
                if (candVotes == null) {
                    if (count != last) append(",")
                } else {
                    val vote = if (candVotes.contains(it)) 1 else 0
                    if (redacted) append("*") else append("$vote")
                    if (count != last) append(",")
                }
            }
        }
        appendLine()
    }

    /*
    Rule a: The aggregate must contain at least min_ballots ballots in total.

    Rule b: For each rare contest (one that appears on at least one rare
            ballot), the aggregate must contain at least min_ballots ballots
            that include that contest.

    Rule c: No contest in the aggregate may be near-unanimous.  "Near-
            unanimous" means all but NEAR_UNANIMOUS_THRESHOLD (default
            value = 2) votes go to a single choice.  If a contest is
            near-unanimous, contrasting ballots are borrowed from the
            common pool until at least MIN_CONTRASTING_VOTES (default
            value = 3) ballots vote for a non-leading choice.

    Rule d: A ballot may only be borrowed from a common style if that style
            will still have at least min_ballots ballots remaining after the
            borrow.
     */

    // rare styles have less than minBallots
    inner class Aggregate(val rareStyles: List<PrecinctUniqueStyle>) {
        val donorRows = mutableListOf<CvrRow>()

        var ncards = 0
        // rare contests appear on at least one rare ballot
        val rareContests = mutableMapOf<Int, ContestTabulation>() // contestId -> tabulation of rows in the Aggregate
        // which rare contests need more cards?
        val needContests: MutableMap<Int, Int> // contestId -> number of cards below minimum
        // which rare contests need more non-leading votes to not be near-unanimous?
        val needContestVotes = mutableMapOf<Int, Pair<Int, Int>>() // contest -> (leading cand id, number of non-leading votes needed)

        var redactedRowSet: Set<Int>? = null

        init {
            ncards = rareStyles.sumOf { it.ncards }
            rareStyles.forEach { rareStyle ->
                rareContests.sumContestTabulations(rareStyle.contestTabs)
            }
            needContests = rareContests.mapValues {
                max(0, minBallots - it.value.ncards())
            }.toMutableMap()

            rareContests.values.forEach { tab: ContestTabulation ->
                if (tab.votes.isNotEmpty()) {
                    val leadingVotes: Map.Entry<Int, Int> = tab.votes.maxBy { it.value }
                    val nonleadingVotes = tab.nvotes() - leadingVotes.value
                    if (nonleadingVotes < MIN_CONTRASTING_VOTES)
                        needContestVotes[tab.contestId] = Pair(leadingVotes.key, MIN_CONTRASTING_VOTES - nonleadingVotes)
                }
            }
        }

        fun finish() {
            val donorIds = donorRows.map { it.cvrNumber }
            val rareIds = rareStyles.map { it.rows }.flatten().map { it.cvrNumber }
            println("Rare Ballots = ${rareIds.size}")
            println("Donor Ballots = ${donorIds.size}")

            redactedRowSet = (donorIds + rareIds).toSet()

            ////// validate results
            val redactedRows = donorRows + rareStyles.map { it.rows }.flatten()
            val redactTabs = tabulateCvrRows(redactedRows, infos)

            println("ruleA: redactedRowSet.size >= minBallots = ${redactedRowSet!!.size >= minBallots}")
            val ruleB = rareContests.keys.all { contestId ->
                val redactTab = redactTabs[contestId]!!
                if (redactTab.ncards() < minBallots)
                    print("")
                redactTab.ncards() >= minBallots
            }
            println("ruleB: rareContests.redactedCards >= minBallots = ${ruleB}")
            val ruleC = rareContests.keys.all { contestId ->
                val redactTab = redactTabs[contestId]!!
                val info = infos[redactTab.contestId]!!
                if (info.candidateIds.size > 1) {
                    val leadingVotes: Map.Entry<Int, Int> = redactTab.votes.maxBy { it.value }
                    val nonleadingVotes = redactTab.nvotes() - leadingVotes.value
                    if (nonleadingVotes <= NEAR_UNANIMOUS_THRESHOLD)
                        print("")
                    nonleadingVotes > NEAR_UNANIMOUS_THRESHOLD
                } else true
            }
            println("ruleC: all rareContests are not close to unanimous >= minBallots = ${ruleC}")
        }

        fun isRedacted(row: CvrRow) = redactedRowSet?.contains(row.cvrNumber) ?: false

        fun hasRedaction() = redactedRowSet != null && redactedRowSet!!.size > 0

        // only one aggregation for now
        fun redactedAggregation() : Map<Int, ContestTabulation> {
            val result = mutableMapOf<Int, ContestTabulation>()
            rareStyles.forEach { rareStyle ->
                result.sumContestTabulations(rareStyle.contestTabs)
            }
            donorRows.forEach { row ->
                row.contestVotes.forEach {
                    result.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
                }
            }
            return result
        }

        fun balanceContests() {
            var needsMore = true
            while (needsMore) {
                needsMore = ruleb()
            }
            needsMore = true
            while (needsMore) {
                needsMore = rulec(commonStyles)
            }
            rulea()
        }

        fun addDonorRow(row: CvrRow) {
            donorRows.add(row)
            // adjust needed
            row.contestVotes.forEach { contestVote ->
                if (contestVote.contestId == 30)
                    print("")
                val needContest = needContests[contestVote.contestId]
                if (needContest != null && needContest > 0)
                    needContests[contestVote.contestId] = needContest - 1
                val needContestVote = needContestVotes[contestVote.contestId]
                if (needContestVote != null && needContestVote.second > 0 && contestVote.votedFor.isNotEmpty() &&
                        !contestVote.votedFor.contains(needContestVote.first)) {
                    needContestVotes[contestVote.contestId] = Pair(needContestVote.first, needContestVote.second - 1)
                }
            }
        }

        // total ballots > minBallots
        fun rulea() {
            val rowCount = rareStyles.sumOf { it.rows.size } + donorRows.size
            if (rowCount < minBallots) {
                repeat(minBallots - rowCount) {
                    chooseRandomRow(commonStyles)
                }
            }
        }

        // return keepGoing
        // choose a donor that maximizes contests needed delta
        fun ruleb(): Boolean {
            val needContestSet = needContests.filter { it.value > 0 }.map { it.key }.toSet()
            if (needContestSet.size == 0)
                return false

            // choose styles that maximize filling needContests, and have enough to donate
            val haveNeeded = mutableListOf<Pair<PrecinctUniqueStyle, Set<Int>>>() // or just set.size ?
            commonStyles.forEach { // .filter { it.ncards > minBallots + DONOR_SURPLUS_THRESHOLD}.forEach {
                haveNeeded.add(Pair(it, it.uniqueStyle.intersect(needContestSet)))
            }
            val maxFill = haveNeeded.maxOf { it.second.size }
            if (maxFill == 0) return false

            // any one these styles (that have enough cards, rule d) will do
            val wtf = haveNeeded.filter { it.second.size == maxFill && it.first.ncards >= minBallots + DONOR_SURPLUS_THRESHOLD}
            val maxCommonStyles = wtf.map { it.first }

            if (!rulec(maxCommonStyles)) {
                val gotone = chooseRandomRow(maxCommonStyles)
            }
            return true
        }

        fun rulec(commonStyles: List<PrecinctUniqueStyle> ): Boolean {
            if (!needContestVotes.any { it.value.second > 0 }) return false

            // choose a card from any commonStyles that satisfies the most needContestVotes
            var maxScore = 0
            var maxRow: CvrRow? = null
            var maxStyle: PrecinctUniqueStyle? = null
            commonStyles.forEach { commonStyle ->
                commonStyle.rows.forEach { row ->
                    var score = 0
                    row.contestVotes.forEach { contestVote ->
                        val needContestVote = needContestVotes[contestVote.contestId]
                        if (needContestVote != null && needContestVote.second > 0 && contestVote.votedFor.isNotEmpty() &&
                            !contestVote.votedFor.contains(needContestVote.first)) {
                            score++
                        }
                    }
                    if (score > maxScore) {
                        maxScore = score
                        maxRow = row
                        maxStyle = commonStyle
                    }
                }
            }
            if (maxRow != null) {
                addDonorRow(maxRow)
                maxStyle!!.rows.remove(maxRow)
                return true
            }
            return false
        }

        // return keepGoing
        fun chooseRandomRow(fromStyles: List<PrecinctUniqueStyle>): Boolean {
            val fromStylesMutable = fromStyles.toMutableList()
            while (true) {
                if (fromStylesMutable.isEmpty())
                    return false
                val chooseStyle = fromStylesMutable.get(Random.nextInt(fromStylesMutable.size))
                if (chooseStyle.rows.isNotEmpty() && chooseStyle.ncards >= minBallots + DONOR_SURPLUS_THRESHOLD) {
                    val chooseRow = chooseStyle.rows.get(Random.nextInt(chooseStyle.rows.size))
                    addDonorRow(chooseRow)
                    chooseStyle.rows.remove(chooseRow)
                    chooseStyle.ncards--
                    return true
                }
                fromStylesMutable.remove(chooseStyle)
            }
        }
    }

    companion object {
        const val NEAR_UNANIMOUS_THRESHOLD = 2  // "all but N votes" triggers balancing (Rule c)
        const val MIN_CONTRASTING_VOTES = 3  // contrasting votes needed per contest after balancing
        const val DONOR_SURPLUS_THRESHOLD = 3  // minimum surplus above min_ballots for a style/precinct to donate freely
    }
}

class PrecinctUniqueStyle(val ballotType: String, val uniqueStyle: Set<Int>, val infos: Map<Int, ContestInfo>) {
    // will contain all rows for rare styles, and enough rows for common style donations
    val rows = mutableListOf<CvrRow>()
    val contestTabs = mutableMapOf<Int, ContestTabulation>()
    var ncards = 0

    fun addCard(row: CvrRow) {
        ncards++
        row.contestVotes.forEach {
            contestTabs.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
        }
        if (rows.size < 10 * MIN_BALLOTS_DEFAULT) rows.add(row)
    }

    override fun toString(): String {
        return "PrecinctUniqueStyle(nrows=${rows.size} ncards=$ncards uniqueStyle=$uniqueStyle)"
    }
}

// unique precinct/ballotType. But we cant rely on ballotType to have unique contestSet
class PrecinctCardStyle(val ballotType: String, val precinctPortion: String, val infos: Map<Int, ContestInfo>) {
    val uniques = mutableMapOf<Set<Int>, PrecinctUniqueStyle>() // track tabulation for unique contests within the precinct
    var ncards = 0
    var styleMap = emptyMap<String, PrecinctUniqueStyle>()

    fun addCard(cvr: CvrRow) {
        ncards++
        val ustyle = uniques.getOrPut(cvr.contests()) { PrecinctUniqueStyle(ballotType, cvr.contests(), infos) }
        ustyle.addCard(cvr)
    }

    fun convert(cardStyleMap: Map<CardStyleId, CvrCardStyle>) {
        styleMap = uniques.mapKeys { cardStyleMap[CardStyleId(ballotType, it.key)]?.ballotType ?: "unknown" }
    }

    override fun toString() = buildString {
        append("PrecinctStyles(ballotType='$ballotType', precinctPortion='$precinctPortion', nunique=${uniques.size}, ncards=$ncards, [")
        styleMap.forEach { append("${it.key}=${it.value.ncards},") }
        append("]")
    }
}

fun findPrecinctStyles(corlaCvrs: CorlaRawCvrsIF, infos: Map<Int, ContestInfo>): List<PrecinctCardStyle> {
    val styleCounters = mutableMapOf<Pair<String, String>, PrecinctCardStyle>()
    corlaCvrs.cvrs().forEach { cvr ->
        // val cvrStyleId = CardStyleId(cvr.ballotType, cvr.contests())
        // val cardStyle = cardStyleMap.getOrPut(cvrStyleId) { CvrCardStyle(cvr.ballotType, cvr.contests()) }
        val id = Pair(cvr.ballotType, cvr.precinctPortion ?: "none")
        val pcStyle = styleCounters.getOrPut(id) { PrecinctCardStyle(id.first, id.second, infos) }
        pcStyle.addCard(cvr)
    }
    styleCounters.values.forEach { it.convert(corlaCvrs.cardStyleMap()) }
    return styleCounters.values.toList()
}