package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.auditcenter.makeContestInfo
import org.cryptobiotic.rlauxe.auditcenter.makeRealContestInfo
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromCandVotes
import java.io.File
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.io.path.writeLines
import kotlin.math.max
import kotlin.random.Random

class Anonomicer(
    val corlaCvrs: CorlaCountyCvrsIF,  // should be the unredacted cvr file, but we allow a previous redaction
    val minBallots: Int = MIN_BALLOTS_DEFAULT,
    val styleOnly: Boolean = false,  // use style only, else use precinct/style
    val removePrecinctCol: Boolean = false,
    val removeBallotTypeCol: Boolean = false,
) {
    constructor(input: String) : this( readCountyCvrs(input), MIN_BALLOTS_DEFAULT)

    val schema = corlaCvrs.schema
    val infos: Map<Int, ContestInfo>
    val removeCols = mutableSetOf<CvrHeader>()
    val agg: Aggregate
    val rpt = Report(true)

    init {
        removeCols.add(CvrHeader.countinggroup)
        if (removePrecinctCol || styleOnly) removeCols.add(CvrHeader.precinctportion)
        if (removeBallotTypeCol) removeCols.add(CvrHeader.ballottype)

        infos = corlaCvrs.makeRealContestInfo()

        val precinctStyles: List<UniqueStyle> = if (styleOnly) findUniqueStyles(corlaCvrs, infos)
                             else findPrecinctStyles(corlaCvrs, infos).map { it.uniques.values }.flatten()

        val commonStyles = precinctStyles.filter { it.ncards >= minBallots }
        val rareStyles = precinctStyles.filter { it.ncards < minBallots }

        if (rareStyles.isNotEmpty()) {
            rpt.add("Rare ballot style/precinct combinations (${rareStyles.size} of ${precinctStyles.size} total)")
            rareStyles.forEach {
                val rare = if (it.ncards < minBallots) "**" else "  "
                rpt.add(" $rare ${it.ncards} ballot(s)  [${it.id()}]")
            }
            val countRare = rareStyles.sumOf { it.ncards }
            rpt.add("Rare Ballots = ${countRare}")
            rpt.add("Redaction is needed.\n")
        }
        else rpt.add("No redaction is needed.\n")

        agg = Aggregate(commonStyles, rareStyles)
        if (agg.ncards > 0) {
            agg.balanceContests()
            agg.finish()
        }
    }

    fun writeRedactedFile(outputFile: String): Anonomicer {
        // Columns that carry no information in the output and should be omitted entirely.
        // CountingGroup is always removed.
        // PrecinctPortion is removed when not redacting on precinct, because every row has it blanked in that case.

        File(outputFile).bufferedWriter(Charsets.UTF_8).use { bwriter ->
            // leaving the original headers TODO remove some headers??
            corlaCvrs.headers().forEach {
                bwriter.write(it)
                bwriter.newLine()
            }

            corlaCvrs.cvrs().forEach { row ->
                if (agg.isRedacted(row)) {
                    bwriter.write(writeRow(row, true))
                } else {
                    // write unredacted
                    bwriter.write(writeRow(row, false))
                }
            }

            // previously redacted are all at the end
            corlaCvrs.redaction().redactedRows().forEach { row ->
                bwriter.write(writeRow(row, true))
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
        return this
    }

    fun writeRow(row: CvrRow, redacted: Boolean) = buildString {
        append(corlaCvrs.rowHeaderCols(row, removeCols))
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
            value = 3) ballots vote for a non-leading choice. Only contests
            on rare ballots are checked; near-unanimity in contests that
            belong only to common styles is not a concern.

    Rule d: A ballot may only be borrowed from a common style if that style
            will still have at least min_ballots ballots remaining after the
            borrow.
     */

    // for one contest
    class NonLeadingVotes(votes: Map<Int, Int>) {
        val mvotes = votes.toMutableMap()
        var leadingCandidate: Int
        var nonleadingVotes: Int
        var needVotes: Int

        init {
            leadingCandidate = leadingCandidate()
            nonleadingVotes = nonleadingVotes()
            needVotes = needVotes()
        }

        private fun leadingCandidate(): Int {
            return if (mvotes.isEmpty()) 0 else
                mvotes.maxBy { it.value }.key
        }

        private fun nonleadingVotes(): Int {
            return if (mvotes.isEmpty()) 0 else {
                val leadingCandidate = leadingCandidate()
                val totalVotes = mvotes.values.sum()
                totalVotes - mvotes[leadingCandidate]!!
            }
        }

        // leading candidate, number of non leading votes needed
        private fun needVotes(): Int {
            return max(0, MIN_CONTRASTING_VOTES - nonleadingVotes())
        }

        // could be that adjust recals the above quantites
        fun adjust(votedFor: List<Int>) {
            // ContestVotes(val contestId: Int, val votedFor: List<Int>)
            votedFor.forEach { cand ->
                val accum = mvotes[cand] ?: 0
                mvotes[cand] = accum + 1
            }
            leadingCandidate = leadingCandidate()
            nonleadingVotes = nonleadingVotes()
            needVotes = needVotes()
        }
    }

    // rare styles have less than minBallots
    inner class Aggregate(val commonStyles: List<UniqueStyle>, val rareStyles: List<UniqueStyle>) {
        // rare contests appear on at least one rare ballot
        val rareContests: Map<Int, ContestTabulation> // contestId -> tabulation of rows in the Aggregate
        // which rare contests need more cards?
        val needContests: MutableMap<Int, Int> // contestId -> number of ballots needed
        // which rare contests need more non-leading votes to not be near-unanimous?
        val needVoteTrackers = mutableMapOf<Int, NonLeadingVotes>() // contest -> NonLeadingVotes tracker

        var ncards = 0
        val donorRows = mutableListOf<CvrRow>()
        var redactedRowSet: Set<Int>? = null

        init {
            ncards = rareStyles.sumOf { it.ncards }

            val rareContestsLocal = mutableMapOf<Int, ContestTabulation>() // contestId -> tabulation of rows in the Aggregate
            rareStyles.forEach { rareStyle ->
                rareContestsLocal.sumContestTabulations(rareStyle.contestTabs)
            }

            // remove any contests that have only a single candidate
            this.rareContests = rareContestsLocal.filter { it.value.candidateIds.size > 1 }

            needContests = rareContests.mapValues {
                max(0, minBallots - it.value.ncards())
            }.filter{ it.value > 0 }.toMutableMap()

            rareContests.values.forEach { tab: ContestTabulation ->
                if (tab.votes.isNotEmpty()) {
                    val nonleadingVotes = NonLeadingVotes(tab.votes)
                    if (nonleadingVotes.needVotes > 0)
                        needVoteTrackers[tab.contestId] = nonleadingVotes
                }
            }

            if (rareContests.isNotEmpty()) {
                rpt.add("Rare Contests:")
                rareContests.forEach { (contest, need) ->
                    val needCards = needContests[contest]
                    val what1 = if (needCards == null) "" else "needs $needCards more ballot(s); "
                    val needVotes = needVoteTrackers[contest]
                    val what2 = if (needVotes == null) "" else "needs ${needVotes.needVotes} more non-leading vote(s)"

                    rpt.add("  $contest '${infos[contest]?.name}': $what1 $what2")
                }
                rpt.add("")
            }
            /*

            if (needContests.isNotEmpty()) {
                println("Contests below minimum:")
                needContests.forEach { (contest, need) ->
                    println("  $contest '${infos[contest]?.name}': needs $need more ballot(s)")
                }
            }

            if (needVoteTrackers.isNotEmpty()) {
                println("Near-unanimous rare contests:")
                needVoteTrackers.forEach { (contest, need) ->
                    println("  $contest '${infos[contest]?.name}': needs ${need.needVotes} more non-leading votes, leading= ${need.leadingCandidate}")
                }
            } else if (ncards > 0){
                println("  There are no near-unanimous contests.")
            } */
        }

        fun ourPrecintRedaction() = redactedRowSet?.size ?: 0
        fun isRedacted(row: CvrRow) = redactedRowSet?.contains(row.cvrNumber) ?: false
        fun hasRedaction() = redactedRowSet != null && redactedRowSet!!.size > 0

        // get the contest tabulation from the aggregated set
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
                val needContest = needContests[contestVote.contestId]
                if (needContest != null && needContest > 0)
                    needContests[contestVote.contestId] = needContest - 1
                val needVoteTracker = needVoteTrackers[contestVote.contestId]
                if (needVoteTracker != null) needVoteTracker.adjust(contestVote.votedFor)
                /* if (needContestVote != null && needContestVote.second > 0 && contestVote.votedFor.isNotEmpty() &&
                        !contestVote.votedFor.contains(needContestVote.first)) {
                    needContestVotes[contestVote.contestId] = Pair(needContestVote.first, needContestVote.second - 1)
                } */
            }
        }

        // total ballots > minBallots
        fun rulea() {
            if (!hasRedaction())
                return
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
            val haveNeeded = mutableListOf<Pair<UniqueStyle, Set<Int>>>() // or just set.size ?
            commonStyles.forEach { // .filter { it.ncards > minBallots + DONOR_SURPLUS_THRESHOLD}.forEach {
                haveNeeded.add(Pair(it, it.contests.intersect(needContestSet)))
            }
            val maxFill = haveNeeded.maxOf { it.second.size }
            if (maxFill == 0)
                return false // nobody can help

            // any one these styles (that have enough cards, rule d) will do
            val wtf = haveNeeded.filter { it.second.size == maxFill && it.first.ncards >= minBallots + DONOR_SURPLUS_THRESHOLD}
            val maxCommonStyles = wtf.map { it.first }

            if (maxCommonStyles.isEmpty()) { // nobody with enough cards can help (this is a bit down in the weeds)
                val possibleStyles = haveNeeded.filter{ it.second.size > 0 }.sortedByDescending { it.second.size }
                possibleStyles.forEach { (style, intersect) ->
                    // if we added all the cards of this style to the redacted set, would it be enough?
                    val allgood = needContests.filter { it.value > 0 }.all { (contestId, need) ->
                        intersect.contains(contestId) && (style.rows.size > need)
                    }
                    if (allgood) {
                        style.rows.forEach { addDonorRow(it) }
                        style.rows.clear()
                        style.ncards = 0
                        return true // test reuturn
                    }
                }
            }

            if (!rulec(maxCommonStyles)) {
                chooseRandomRow(maxCommonStyles)
            }
            return true
        }

        fun rulec(commonStyles: List<UniqueStyle> ): Boolean {
            if (!needVoteTrackers.any { it.value.needVotes > 0 })
                return false
            val totalVotesNeeded = needVoteTrackers.values.sumOf { it.needVotes} // debugging

            // choose a card from any commonStyles that satisfies the most needContestVotes
            var maxScore = 0
            var maxRow: CvrRow? = null
            var maxStyle: UniqueStyle? = null
            commonStyles.forEach { commonStyle ->
                commonStyle.rows.forEach { row ->
                    var score = 0
                    row.contestVotes.forEach { contestVote ->
                        val needVoteTracker = needVoteTrackers[contestVote.contestId]
                        if (needVoteTracker != null && needVoteTracker.needVotes > 0 && contestVote.votedFor.isNotEmpty() &&
                            !contestVote.votedFor.contains(needVoteTracker.leadingCandidate)) {
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
                val totalAfter = needVoteTrackers.values.sumOf { it.needVotes} // debugging
                return true
            }
            return false
        }

        // return keepGoing
        fun chooseRandomRow(fromStyles: List<UniqueStyle>): Boolean {
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

        fun finish() {
            val donorIds = donorRows.map { it.cvrNumber }
            val rareIds = rareStyles.map { it.rows }.flatten().map { it.cvrNumber }
            rpt.add("Ballots borrowed from common styles = ${donorIds.size}")
            rpt.add("")
            redactedRowSet = (donorIds + rareIds).toSet()

            ////// validate results
            val redactedRows = donorRows + rareStyles.map { it.rows }.flatten()
            val redactTabs = tabulateCvrRows(redactedRows.iterator(), infos)
            rpt.add("Validation")
            rpt.add("  ruleA: redactedRowSet.size (${redactedRowSet!!.size} >= minBallots = ${redactedRowSet!!.size >= minBallots}")

            val ruleB = rareContests.keys.all { contestId ->
                val redactTab = redactTabs[contestId]!!
                if (redactTab.ncards() < minBallots)
                    print("     contest=${contestId} redactTab.ncards()=${redactTab.ncards()}")
                redactTab.ncards() >= minBallots
            }
            rpt.add("  ruleB: rareContests.redactedCards >= minBallots = ${ruleB}")

            val ruleC = rareContests.keys.all { contestId ->
                val redactTab = redactTabs[contestId]!!
                val info = infos[redactTab.contestId]!!
                if (info.candidateIds.size > 1) {
                    val leadingVotes: Map.Entry<Int, Int> = redactTab.votes.maxBy { it.value }
                    val nonleadingVotes = redactTab.nvotes() - leadingVotes.value
                    if (nonleadingVotes <= NEAR_UNANIMOUS_THRESHOLD)
                        rpt.add("    $contestId: nonleadingVotes=$nonleadingVotes")
                    nonleadingVotes > NEAR_UNANIMOUS_THRESHOLD
                } else true
            }
            rpt.add("  ruleC: all rareContests are not close to unanimous = ${ruleC}")
        }
    }

    fun verify(redactedCvrs: CorlaCountyCvrsIF) {
        val unredactedTabs = tabulate(corlaCvrs)
        val redactedTabs = tabulate(redactedCvrs)
        val usum = unredactedTabs.sum()
        val rsum = redactedTabs.sum()
        var fail = false
        usum.forEach { (id, utab) ->
            val rtab = rsum[id]
            if (rtab != null && !checkEquivilentVotes(utab.votes, rtab.votes)) {
                rpt.add(utab.toString())
                rpt.add(rtab.toString())
                rpt.add("")
                fail = true
            }
        }
        if (fail) rpt.add("**** Contest tabulations are not the same before and after redaction")
        else rpt.add("Contest tabulations are the same before and after redaction")
    }

    companion object {
        const val MIN_BALLOTS_DEFAULT = 10
        const val NEAR_UNANIMOUS_THRESHOLD = 2  // "all but N votes" triggers balancing (Rule c)
        const val MIN_CONTRASTING_VOTES = 3  // contrasting votes needed per contest after balancing
        const val DONOR_SURPLUS_THRESHOLD = 3  // minimum surplus above min_ballots for a style/precinct to donate freely
    }
}

open class UniqueStyle(val contests: Set<Int>, val infos: Map<Int, ContestInfo>) {
    // will contain all rows for rare styles, and enough rows for common style donations
    val rows = mutableListOf<CvrRow>()
    val contestTabs = mutableMapOf<Int, ContestTabulation>()
    var ncards = 0

    fun addCard(row: CvrRow) {
        ncards++
        row.contestVotes.forEach {
            contestTabs.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
        }
        if (rows.size < 10 * Anonomicer.MIN_BALLOTS_DEFAULT) rows.add(row)
    }

    open fun id() = contests.toString()

    override fun toString(): String {
        return "UniqueStyle(nrows=${rows.size} ncards=$ncards contests=$contests)"
    }
}

// class PrecinctUniqueStyle(val precinct: String, val ballotType: String, val uniqueStyle: Set<Int>, val infos: Map<Int, ContestInfo>) {
class PrecinctUniqueStyle(val precinct: String, contests: Set<Int>, infos: Map<Int, ContestInfo>): UniqueStyle(contests, infos) {

    override fun id() = "$precinct / $contests"

    override fun toString(): String {
        return "PrecinctUniqueStyle(nrows=${rows.size} ncards=$ncards uniqueStyle=$contests precinct='$precinct')"
    }
}

// unique precinct/ballotType. But we cant rely on ballotType to have unique contestSet
class PrecinctCardStyle(val precinctPortion: String, val infos: Map<Int, ContestInfo>) {
    val uniques = mutableMapOf<Set<Int>, PrecinctUniqueStyle>() // track tabulation for unique contests within the precinct
    var ncards = 0
    // var styleMap = emptyMap<String, PrecinctUniqueStyle>()

    fun addCard(cvr: CvrRow) {
        ncards++
        val ustyle = uniques.getOrPut(cvr.contests()) { PrecinctUniqueStyle(precinctPortion, cvr.contests(), infos) }
        ustyle.addCard(cvr)
    }

    // ??
    /* fun convert(cardStyleMap: Map<CardStyleId, CvrCardStyle>) {
        // ignore the ballotType
        // styleMap = uniques.mapKeys { cardStyleMap[CardStyleId(ballotType, it.key)]?.ballotType ?: "unknown" }
        //styleMap = uniques.mapKeys { cardStyleMap[CardStyleId(ballotType, it.key)]?.ballotType ?: "unknown" }
    } */

    override fun toString() = buildString {
        append("PrecinctStyles(precinctPortion='$precinctPortion', nunique=${uniques.size}, ncards=$ncards, [")
        //styleMap.forEach { append("${it.key}=${it.value.ncards},") }
        append("]")
    }
}

fun findPrecinctStyles(corlaCvrs: CorlaCountyCvrsIF, infos: Map<Int, ContestInfo>): List<PrecinctCardStyle> {
    val styleCounters = mutableMapOf<String, PrecinctCardStyle>()
    corlaCvrs.cvrs().forEach { cvr ->
        // val cvrStyleId = CardStyleId(cvr.ballotType, cvr.contests())
        // val cardStyle = cardStyleMap.getOrPut(cvrStyleId) { CvrCardStyle(cvr.ballotType, cvr.contests()) }
        // val id = Pair(cvr.ballotType, cvr.precinctPortion ?: "none")
        val pcStyle = styleCounters.getOrPut(cvr.precinctPortion!!) { PrecinctCardStyle(cvr.precinctPortion, infos) }
        pcStyle.addCard(cvr)
    }
    // styleCounters.values.forEach { it.convert(corlaCvrs.cardStyleMap()) }
    return styleCounters.values.toList()
}

fun findUniqueStyles(corlaCvrs: CorlaCountyCvrsIF, infos: Map<Int, ContestInfo>): List<UniqueStyle> {
    val styleCounters = mutableMapOf<Set<Int>, UniqueStyle>()
    corlaCvrs.cvrs().forEach { cvr ->
        val pcStyle = styleCounters.getOrPut(cvr.contests()) { UniqueStyle(cvr.contests(), infos) }
        pcStyle.addCard(cvr)
    }
    // styleCounters.values.forEach { it.convert(corlaCvrs.cardStyleMap()) }
    return styleCounters.values.toList()
}

data class CvrTabulations(val infos: Map<Int, ContestInfo>,
                          val cvrTabs: Map<Int, ContestTabulation>,
                          val redactedTabs: Map<Int, ContestTabulation>) {

    fun sum(): Map<Int, ContestTabulation> { // contest -> contestTab
        val sum = mutableMapOf<Int, ContestTabulation>()
        sum.sumContestTabulations(cvrTabs)
        sum.sumContestTabulations(redactedTabs)
        return sum
    }

    override fun toString()= buildString {
        appendLine("\ncvr tabs")
        cvrTabs.forEach { appendLine(" ${nfn(it.value.ncards(), 3)}: $it")}
        appendLine("\nredacted tabs")
        redactedTabs.forEach { appendLine(" ${nfn(it.value.ncards(), 3)}: $it")}
    }
}

fun tabulateCvrRows(rows: Iterator<CvrRow>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val sum = mutableMapOf<Int, ContestTabulation>()
    rows.forEach { row ->
        row.contestVotes.forEach {
            sum.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
        }
    }
    return sum
}

fun tabulate(corlaCvrs: CorlaCountyCvrsIF): CvrTabulations  {
    val infos = corlaCvrs.makeContestInfo().map { it ->
        ContestInfo(
            it.name, it.id, it.candidateNames,
            if (it.isIrv) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY,
            it.nwinners
        )
    }.associateBy { it.id }

    val cvrTabs = tabulateCvrRows(corlaCvrs.cvrs().iterator(), infos)

    val sumRedaction = mutableMapOf<Int, ContestTabulation>()
    val redaction = corlaCvrs.redaction()
    redaction.groups().forEach { group ->
        val groupCardsByContest = redaction.cardCountByContest()
        group.candVotes.forEach { (scontestId, candVotes) ->
            sumRedaction.sumContestTabulationsFromCandVotes(infos[scontestId]!!, candVotes, groupCardsByContest[scontestId] ?: 1)
        }
    }
    return CvrTabulations(infos, cvrTabs, sumRedaction)
}

fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
}

class Report(val print: Boolean) {
    val lines = mutableListOf<String>()

    fun add(line: String) {
        lines.add(line)
        if (print) println(line)
    }

    fun writeTo(output: String) {
        val filePath = Path(output)
        filePath.writeLines(lines)
    }
}
