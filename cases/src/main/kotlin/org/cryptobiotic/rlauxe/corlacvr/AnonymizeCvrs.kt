package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.makeContestInfo
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromCandVotes
import java.io.File
import kotlin.collections.set
import kotlin.math.max

/*
Derived from github/nealmcb/anonymize_cvr/anonymize_cvr.py

Anonymize Cast Vote Records (CVR) per Colorado C.R.S. 24-72-205.5.

Ballot styles with fewer than MIN_BALLOTS ballots must be aggregated to
protect voter privacy.

Terminology used throughout this module:

  style         — the set of contests for a ballot

  named_style   — the value in the column identified by --stylecol, if any.
                  Assigned by the voting machine; almost certainly obsolete
                  in Colorado, but still accepted.

  ballot_type   — the value in the BallotType column, if present.
*/

private val logger = KotlinLogging.logger("AnonymizeCvr2")
private val warnLeakage = false
private val addNrows = false

private const val NEAR_UNANIMOUS_THRESHOLD = 2  // "all but N votes" triggers balancing (Rule c)
private const val MIN_CONTRASTING_VOTES = 3  // contrasting votes needed per contest after balancing
private const val COVERAGE_WEIGHT = 10.0  // weight for contest coverage vs. vote-balance score
private const val DONOR_SURPLUS_THRESHOLD = 3  // minimum surplus above min_ballots for a style/precinct to donate freely

// redactPrecinct = true as default ??
// leave in other header fields; retainHeaders = false?
// ints or strings ?
// format strings with ="value" ?
// 222134,1,GEN-2322,1,1-GEN-2322-1,169,"Property Owner [27, 27, 16, 27, 16, 27, ...]",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

class Anonymize(
    val corlaCvrs: CorlaRawCvrsIF,
    val minBallots: Int,
    val outputFile: String?,
    val redactOnPrecinct: Boolean = false,
    val styleCol: Int? = null,
    val noContestBalancing: Boolean = false,
    val redactedListFile: String? = null,
    val redactPrecinct: Boolean = true,
) {
    constructor(input:String,
                minBallots: Int,
                outputFile: String?,
                redactOnPrecinct: Boolean,
                styleCol: Int?,
                noContestBalancing: Boolean,
                redactedListFile: String?):
            this(readCorlaCvrs(input), minBallots, outputFile, redactOnPrecinct, styleCol, noContestBalancing, redactedListFile)

    val schema = corlaCvrs.schema
    val infos: Map<Int, ContestInfo>
    val styleMap: Map<Set<Int>, CvrCardStyle> = corlaCvrs.cardStyleMap()
    val styleNameMap: Map<String, CvrCardStyle>

    val db = CvrDatabase(corlaCvrs)

    // I think this replaces RowIndex and collect_rare_rows
    val rareStyleMap: Map<Set<Int>, CvrCardStyle> // only styles with countCards < minBallots
    val rareRowIndices: List<Int> // rare row Indices; no balancing yet
    val rowsByRareStyle: Map<CvrCardStyle, MutableList<CvrRow>>

    // I think this replaces Aggregation ?
    val tabsByRareStyle: Map<CvrCardStyle, Map<Int, ContestTabulation>>

    init {
        infos = corlaCvrs.makeContestInfo().map { it ->
            ContestInfo(
                it.name, it.id, it.candidateNames,
                if (it.isIrv) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY,
                it.nwinners
            )
        }.associateBy { it.id }

        rareStyleMap = styleMap.filter { it.value.countCards < minBallots }
        styleNameMap = styleMap.mapKeys { it.value.name }

        val redacted_row_indices = mutableListOf<Int>() // needed ?
        val rare_rows_map = mutableMapOf<CvrCardStyle, MutableList<CvrRow>>()
        corlaCvrs.cvrs().forEachIndexed { idx, row ->
            val rareStyle = rareStyleMap[row.contests()]
            if (rareStyle != null) {
                rare_rows_map.getOrPut(rareStyle) { mutableListOf() }.add(row)
                redacted_row_indices.add(idx)
            }
        }
        rareRowIndices = redacted_row_indices
        rowsByRareStyle = rare_rows_map

        tabsByRareStyle = rowsByRareStyle.mapValues { (_, rowlist) -> tabulateCvrRows(rowlist, infos) }
    }

    /*
    Pass 2: stream the file, load rare rows and a targeted donor pool,
    build the aggregate, and return the set of aggregated row indices
    and the aggregate summary row.

    Returns:
        redacted_row_indices  — 0-based row indices of all ballots in the aggregate
        aggregate_row         — the AGGREGATED summary row to append to the output
    */
    fun loadDonorPool(
        index: RowIndex,
        needs: RedactionNeeds,
        db: CvrDatabase,
        minBallots: Int,
        redactOnPrecinct: Boolean
    ): Pair<Set<Int>, Aggregate> {

        val perContestPoolTarget = 10 * minBallots

        // Pre-compute how many rows contain each contest.
        // Each (style, precinct) key's style string encodes which contests are present.
        val contestTotalCounts = mutableMapOf<Int, Int>()
        index.rowsByPrivacyUnit.forEach { (styleName, rowlist) ->
            val style = styleNameMap[styleName]!!
            val count = rowlist.size
            for ((contestId, contestName) in db.contestNames.withIndex()) {
                if (style.contains(contestId)) {
                    contestTotalCounts[contestId] = contestTotalCounts.getOrDefault(contestId, 0) + count
                }
            }
        }

        val rarePrivacyUnitSet: Set<String> = needs.rarePrivacyUnitPairs.keys.toMutableSet()

        // Rare-ballot contests: any contest present on at least one rare ballot.
        val rareBallotContests = mutableSetOf<Int>()
        for (styleName in rarePrivacyUnitSet) {
            val style = styleNameMap[styleName]!!
            for ((contestId, contestName) in db.contestNames.withIndex()) {
                if (style.contains(contestId)) {
                    rareBallotContests.add(contestId)
                }
            }
        }

        // Rare contests: rare-ballot contests with fewer than perContestPoolTarget
        // total appearances in the full CVR.
        val rareContests = mutableSetOf<Int>()
        for (contestId in rareBallotContests) {
            if (contestTotalCounts[contestId] ?: 0 < perContestPoolTarget) {
                rareContests.add(contestId)
            }
        }

        // Row counts per privacy unit, passed to CommonPool for Rule d enforcement.
        val rowcountByPrivacyUnit = mutableMapOf<String, Int>()
        for ((key, rowIndices) in index.rowsByPrivacyUnit) {
            rowcountByPrivacyUnit[key] = rowIndices.size
        }

        // Tracks how many donor rows have been loaded per rare-ballot contest.
        val donorCountByRareBallotContest = mutableMapOf<Int, Int>()

        // Tracks vote distribution of loaded donors per rare-ballot contest.
        // contest_name -> {choice_name: count of donors voting "1" for that choice}
        // Used to ensure we load enough contrasting donors to fix near-unanimity.
        val donorVotedTally = mutableMapOf<Int, MutableMap<String, Int>>() // contestId -> candName -> count
        // could also use
        val donorVotedTab = mutableMapOf<Int, ContestTabulation>() // contestId -> contestTab

        val rareRows = mutableListOf<CvrRow>()
        val donorByPrivacyUnit = mutableMapOf<String, MutableList<CvrRow>>()
        val blockedByPrivacyUnit = mutableMapOf<String, MutableList<CvrRow>>()

        // Maps id(row) to rowIdx for all loaded rows, used to identify aggregated rows.
        val rowToIdx = mutableMapOf<Int, Int>()

        println("*** Pass 2: Building the aggregate row.")
        println()

        var ndonors = 0
        var rowIdx = -1
        for (row in corlaCvrs.cvrs()) {
            val rowStyle = styleMap[row.contests()]!!
            val privacyUnit = rowStyle.name
            rowIdx++

        /* File(csvPath).bufferedReader(Charsets.UTF_8).use { reader ->
            val csvReader = reader.lineSequence().iterator()
            repeat(4) { csvReader.next() }

            for ((rowIdx, row) in csvReader.asSequence().filter { it.trim().split(",").any { value -> value.isNotBlank() } }.withIndex()) {
                val rowStyle = index.styleForRow(rowIdx) ?: continue
                val privacyUnit = rowStyle */

            if (privacyUnit in rarePrivacyUnitSet) {
                rareRows.add(row)
                addToRowIdx(row, rowIdx, rowToIdx)
            } else {
                val privacyUnitCount = rowcountByPrivacyUnit.getOrDefault(privacyUnit, 0)
                if (privacyUnitCount <= minBallots) {
                    if (privacyUnitCount == minBallots) {
                        // Blocked style: at exactly minBallots, can't donate
                        // individual ballots without violating Rule d.  Track for
                        // potential whole-style pull if individual borrowing stalls.
                        var styleHasRareContest = false
                        for ((contestId, contestName) in db.contestNames.withIndex()) {
                            // rowStyle[i] == '1' means that rowStyle contains the ith contest
                            if (contestId in rareBallotContests && rowStyle.contains(contestId)) {
                                styleHasRareContest = true
                                break
                            }
                        }
                        if (styleHasRareContest) {
                            blockedByPrivacyUnit.computeIfAbsent(privacyUnit) { mutableListOf() }.add(row)
                            addToRowIdx(row, rowIdx, rowToIdx)
                        }
                    }
                    continue
                }

                // Find which rare-ballot contests are present on this row.
                val rowRareContests = rareBallotContests.filter { contestId ->
                    // TODO the rows dont know their style
                    rowStyle.contains(contestId)
                }

                if (rowRareContests.isEmpty()) {
                    continue
                }

                if (privacyUnitCount <= minBallots + DONOR_SURPLUS_THRESHOLD) {
                    // Tight surplus: only load if this row has a rare contest.
                    val hasRareContest = rowRareContests.any { it in rareContests }
                    if (!hasRareContest) {
                        continue
                    }
                } else {
                    // Comfortable surplus: load if pool needs more donors for any
                    // rare-ballot contest, OR if this row provides contrasting votes
                    // for a contest where loaded donors are currently too one-sided.
                    val poolNeedsMore = rowRareContests.any { contestId ->
                        donorCountByRareBallotContest.getOrDefault(contestId, 0) < perContestPoolTarget
                    }

                    // TODO logic is messed up here; too many rows in donorByPrivacyUnit
                    if (!poolNeedsMore) {
                        var needsContrast = false
                        for (contestId in rowRareContests) {
                        // val needsContrast = rowRareContests.any { contestId ->
                            val tally: MutableMap<String, Int> = donorVotedTally[contestId] ?: continue

                            // For this contest, find the choice with the most votes.
                            var maxChoiceName: String = "none"
                            var maxCount = -1
                            for ((choiceName, count) in tally) {
                                if (count > maxCount) {
                                    maxCount = count
                                    maxChoiceName = choiceName
                                }
                            }

                            //          row_voted = None
                            //                            for col_idx, choice_name in db.contest_choice_meta.get(
                            //                                contest_name, {}
                            //                            ).items():
                            //                                if row[col_idx].strip() == "1":
                            //                                    row_voted = choice_name
                            //                                    break
                            // var row_voted: Int? = null

                            //       row_voted = None
                            //                            for col_idx, choice_name in db.contest_choice_meta.get(
                            //                                contest_name, {}
                            //                            ).items():
                            //                                if row[col_idx].strip() == "1":
                            //                                    row_voted = choice_name
                            //                                    break
                            // TODO looks like "find which choice the row voted for"
                            val contestChoiceIds: Map<Int, String> = db.contestChoiceIds.getOrDefault(contestId, emptyMap()) // contest name -> {choiceId, choice_name}

                            val votedForList = row.contestVotesFor(contestId)?.votedFor
                            val choiceName = if (votedForList != null && votedForList.isNotEmpty()) {
                                val votedFor = votedForList.first()
                                contestChoiceIds[votedFor]
                            } else null
                            if (choiceName == null || choiceName == maxChoiceName)
                                continue

                            val contrastingSoFar = tally.filter { it.key != maxChoiceName }.values.sum()
                            if (contrastingSoFar < MIN_CONTRASTING_VOTES) {
                                needsContrast = true
                                break
                            }
                        }
                        if (!needsContrast) {
                            continue
                        }
                    } // !poolNeedsMore

                } // privacy_unit_count > min_ballots

                ndonors++
                donorByPrivacyUnit.computeIfAbsent(privacyUnit) { mutableListOf() }.add(row)
                addToRowIdx(row, rowIdx, rowToIdx)

                // TODO review
                for (contestId in rowRareContests) {
                    val contestName = db.contestNames.get(contestId)
                    donorCountByRareBallotContest[contestId] = donorCountByRareBallotContest.getOrDefault(contestId, 0) + 1

                    // TODO change donorVotedTally to use dandId to avoid extra lookup
                    val tally = donorVotedTally.computeIfAbsent(contestId) { mutableMapOf() } // contestId -> candName -> count
                    val contestChoiceIds: Map<Int, String> = db.contestChoiceIds.getOrDefault(contestId, emptyMap()) // contest name -> {choiceId, choice_name}

                    val votedForList = row.contestVotesFor(contestId)?.votedFor
                    if (votedForList != null && votedForList.isNotEmpty()) {
                        val votedFor = votedForList.first()
                        val choiceName = contestChoiceIds[votedFor]
                        tally[choiceName!!] = tally.getOrDefault(choiceName, 0) + 1
                    }
                } // loop over rowRareContests

            } //  privacyUnit !in rarePrivacyUnitSet

        } // loop over rows

        println("ndonors = $ndonors")
        val pool = CommonPool(
            donorByPrivacyUnit.mapValues { it.value.toList() },
            minBallots,
            rowcountByPrivacyUnit,
            blockedByPrivacyUnit.mapValues { it.value.toList() }
        )

        val aggregate: Aggregate = buildAggregate(rareRows.toList(), rareBallotContests, pool, db, minBallots)
        val borrowedAfterAb = aggregate.totalCount() - rareRows.size
        if (borrowedAfterAb > 0) {
            println("  Ballots borrowed for minimum counts: $borrowedAfterAb")
        }

        println("\n*** Balancing near-unanimous contests.\n")
        println("  Make sure that the following constraint is met:")
        println("  - No contest in the aggregate may be near-unanimous. 'Near-unanimous'")
        println("    means all but $NEAR_UNANIMOUS_THRESHOLD votes go to a single choice.")
        println()
        // Loop until no near-unanimous contests remain or the pool is exhausted.
        // Each pass either adds at least one ballot (shrinking the finite pool) or
        // adds nothing, in which case we are stuck and exit.  Termination is guaranteed.
        var stillProblematic: List<FindContest> = emptyList()
        while (true) {
            val countBefore = aggregate.totalCount()
            balanceUnanimity(aggregate, pool, db)
            stillProblematic = findNearUnanimousContests(aggregate, db)
            if (stillProblematic.isEmpty()) {
                break
            }
            if (aggregate.totalCount() == countBefore) {
                break  // balanceUnanimity could not add any ballots; no point looping
            }
        }

        val borrowedAfterC = aggregate.totalCount() - rareRows.size
        val unanimityBorrowed = borrowedAfterC - borrowedAfterAb
        if (unanimityBorrowed > 0) {
            println("  Total ballots borrowed for unanimity balancing: $unanimityBorrowed")
        }

        if (stillProblematic.isNotEmpty()) {
            println()
            println("  WARNING: the following contests remain near-unanimous after balancing.")
            println("  No contrasting ballots were available in the donor pool.")
            for ((contestName, maxChoice, maxVotes, totalVotes) in stillProblematic) {
                println("    '$contestName': '$maxChoice' has $maxVotes out of $totalVotes votes")
            }
        }

        // Identify which row indices are in the aggregate by object identity.
        val redactedRowIndices = mutableSetOf<Int>()
        for (ballot in aggregate.ballots) {
            val idx = rowToIdx[ballot.hashCode()]
            if (idx != null) {
                if (!redactedRowIndices.add(idx)) {
                    logger.warn{"duplicate ${ballot.hashCode()}"}
                }
            }
        }

        return Pair(redactedRowIndices, aggregate)
    }

    fun addToRowIdx(row: CvrRow, rowIdx: Int, rowToIdx: MutableMap<Int, Int>) {
        //if (rowToIdx.values.find{ it == rowIdx} != null)
        //    print("")
        rowToIdx[row.hashCode()] = rowIdx
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    fun printBorrowingNeeds(aggregate: Aggregate, minBallots: Int) {
        // Print a one-time summary of why ballot borrowing is needed.
        if (aggregate.needsMoreTotalBallots()) {
            val needed = minBallots - aggregate.totalCount()
            println("  Aggregate has ${aggregate.totalCount()} ballot(s); " +
                        "need $needed more to reach minimum of $minBallots."
            )
        }
        val contestNeeds = aggregate.contestsNeedingBallots()
        if (contestNeeds.isNotEmpty()) {
            println("  Contests below minimum:")
            for ((contest, needed) in contestNeeds.toSortedMap()) {
                println("    '${db.contestNames.get(contest)}': needs $needed more ballot(s)")
            }
        }
    }

    fun buildAggregate(
        rareBallots: List<CvrRow>,
        rareContests: Set<Int>,
        pool: CommonPool,
        db: CvrDatabase,
        minBallots: Int
    ): Aggregate {
        /*
        Build the aggregate from rare ballots, borrowing from the pool as needed.

        Satisfies Rules a and b simultaneously using a coverage-weighted ballot
        selection: each borrowed ballot is chosen to cover as many under-represented
        contests as possible while also reducing vote imbalance.  Rule d (only borrow
        from styles that remain common after borrowing) is enforced by CommonPool.
        */
        val aggregate = Aggregate(rareBallots, rareContests, db, minBallots, infos)

        if (!aggregate.satisfiesMinimums()) {
            println("  Rare ballots: ${aggregate.totalCount()}.")
            println("  Borrowing from common styles to satisfy these requirements:")
            println("  - the ballot aggregation must contain at least $minBallots ballots.")
            println("  - every contest in the rare styles must appear on at least $minBallots ballots in the aggregation.")
            println()
            printBorrowingNeeds(aggregate, minBallots)
            println("\n  Selecting ballots to borrow from common styles (this can take a few minutes)...")
        }

        var borrowedCount = 0

        while (!aggregate.satisfiesMinimums()) {
            val result = pool.bestCandidateFor(aggregate, db)
            if (result == null) {
                val blocked = pool.pullBlockedStyleFor(aggregate.contestsNeedingBallots(), db)
                if (blocked != null) {
                    val (_, blockedRows) = blocked
                    for (ballot in blockedRows) {
                        aggregate.add(ballot)
                    }
                    continue
                }
                for ((contest, needed) in aggregate.contestsNeedingBallots()) {
                    System.err.println(
                        "WARNING: could not find enough ballots for contest " +
                                "'$contest' (still need $needed more). " +
                                "This contest only appears on rare ballot styles."
                    )
                }
                break
            }
            val (styleSig, rowIdx, ballot) = result
            borrowedCount += 1
            aggregate.add(ballot)
            pool.remove(styleSig, rowIdx)
        }

        return aggregate
    }

    data class FindContest(val contestName: String, val maxChoice: String?, val maxVotes: Int, val totalVotes: Int)

    fun findNearUnanimousContests(
        aggregate: Aggregate,
        db: CvrDatabase
    ): List<FindContest> {
        /*
        Return (contest_name, max_choice, max_votes, total_votes) for every rare contest
        in the aggregate that is near-unanimous.

        Contests with only one available choice are excluded: they are always unanimous
        by definition and nothing can be done about it.  The check uses the number of
        choice columns in the CVR, not the number of choices that received votes, so a
        two-choice contest where all ballots voted for the same candidate is NOT excluded.
        */
        val result = mutableListOf<FindContest>()
        for ((contestId, contestTab) in aggregate.contestTabs) {
            val contestName = db.contestNames.get(contestId)

            if (contestId !in aggregate.rareContests) {
                continue
            }
            if (contestTab.votes.isEmpty()) {
                continue
            }
            if ((db.contestColumns[contestName]?.size ?: 0) <= 1) { // ??
                continue
            }
            // total votes in the contest
            val totalVotes = contestTab.nvotes()
            if (totalVotes == 0) {
                continue
            }
            // choice with max votes
            var maxChoiceId: Int = -1
            var maxVotes = 0
            for ((candId, votes) in contestTab.votes) { // // cand -> votes
                if (votes > maxVotes) {
                    maxVotes = votes
                    maxChoiceId = candId // to name
                }
            }
            val otherVotes = totalVotes - maxVotes
            if (otherVotes <= NEAR_UNANIMOUS_THRESHOLD) {
                result.add(FindContest(contestName, db.contestChoiceName(contestId, maxChoiceId), maxVotes, totalVotes))
            }
        }
        return result
    }

    fun balanceUnanimity(
        aggregate: Aggregate,
        pool: CommonPool,
        db: CvrDatabase
    ) {
        /*
        Add contrasting ballots to prevent near-unanimous vote patterns (Rule c).

        Only checks contests that appeared on rare ballots; near-unanimity in
        contests that belong only to common styles is not a concern here.
        */
        val problematic = findNearUnanimousContests(aggregate, db)

        if (problematic.isEmpty()) {
            println("  There are no near-unanimous contests.")
            return
        }

        for ((contestName, maxChoice, maxVotes, totalVotes) in problematic) {
            println("    '$contestName': '${maxChoice}' has $maxVotes out of $totalVotes votes")
        }

        val contrasting = findContrastingBallotsMulti(problematic, pool, db)
        if (contrasting.isNotEmpty()) {
            pool.removeRows(contrasting)
            for (ballot in contrasting) {
                aggregate.add(ballot)
            }
            println()
            println("  Borrowed ${contrasting.size} contrasting ballot(s).")
        }
    }

    data class BallotScore(val score: Int, val contests: List<String>, val row: CvrRow)
    data class ProblemContestInfo(val col_indices: List<Int>, val winning_candId: Int?)

    fun findContrastingBallotsMulti(
        problematicContests: List<FindContest>,
        pool: CommonPool,
        db: CvrDatabase
    ): List<CvrRow> {
        /**
         * Find ballots from the pool that vote differently in near-unanimous contests.
         *
         * Minimizes total ballots borrowed by preferring ballots that address multiple
         * problematic contests at once.  Aims for MIN_CONTRASTING_VOTES differing
         * ballots per contest.
         */
        if (problematicContests.isEmpty()) {
            return emptyList()
        }

        // For each problematic contest, find which column holds the leading choice.
        val contestInfo = mutableMapOf<String, ProblemContestInfo>()
        // probably should be
        // val contestInfo = mutableMapOf<String, MutableMap<String, Int>>()
        for ((contestName, maxChoiceName, _, _) in problematicContests) {
            val colIndices = db.contestColumns[contestName] ?: emptyList()
            if (colIndices.isEmpty()) {
                continue
            }
            val contestId = db.contestId(contestName)
            val candMap = db.contestChoiceIds[contestId] ?: emptyMap()
            var winning_candId: Int? = null
            for ((candId, choiceName) in candMap) {
                if (choiceName == maxChoiceName) {
                    winning_candId = candId
                    break
                }
            }
            contestInfo[contestName] = ProblemContestInfo(colIndices, winning_candId)
        }

        // Score each available ballot by how many "problematic contests it votes against".
        val ballotScores = mutableListOf<BallotScore>()
        for ((style, rowlist) in pool.styles) { // forEach { (style, rowlist) ->
            if (!pool.canDonate(style)) {
                continue
            }
            for (ballot in rowlist) {
                val satisfied = mutableListOf<String>()
                for ((contestName, _, _, _) in problematicContests) {
                    if (!contestInfo.containsKey(contestName)) {
                        continue
                    }
                    val info = contestInfo[contestName]!!
                    //                 has_contest = any(
                    //                    ballot[col_idx].strip() != "" for col_idx in info["col_indices"]
                    //                )
                    //                     val hasContest = info.col_indices.any { ballot[it].trim() != "" }
                    // apparently you just want to know if the ballot has this contest
                    val ballotStyle = styleMap[ballot.contests()]!!
                    val contestId = db.contestId(contestName)
                    if (!ballotStyle.contains(contestId)) {
                        continue
                    }
                    //                 winning_col = info["winning_col"]
                    //                if winning_col is not None and ballot[winning_col].strip() != "1":
                    //                    for col_idx in info["col_indices"]:
                    //                        if col_idx != winning_col and ballot[col_idx].strip() == "1":
                    //                            satisfied.append(contest_name)
                    //                            break
                    val votes = ballot.contestVotes.find { it.contestId == contestId }
                    // if didnt find that contest, then its an undervote
                    if (votes != null) {
                        if (!votes.votedFor.contains(info.winning_candId)) { // didnt vote for the winning candidate
                            satisfied.add(contestName)
                        }
                    }
                }
                if (satisfied.isNotEmpty()) {
                    ballotScores.add(BallotScore(satisfied.size, satisfied, ballot))
                }
            }
        }

        ballotScores.sortByDescending { it.score }

        // Greedily select ballots until each problematic contest has enough contrast.
        val contestsNeeded: MutableSet<String> = problematicContests.map { it.contestName }.toMutableSet()
        val selected: MutableList<CvrRow> = mutableListOf()
        val contrastCounts: MutableMap<String, Int> = mutableMapOf()

        for ((_, satisfied, ballot) in ballotScores) {
            if (satisfied.any { contestsNeeded.contains(it) }) {
                selected.add(ballot)
                for (c in satisfied) {
                    contrastCounts[c] = contrastCounts.getOrDefault(c, 0) + 1
                }
                for (c in contestsNeeded.toList()) {
                    if (contrastCounts[c] ?: 0 >= MIN_CONTRASTING_VOTES) {
                        contestsNeeded.remove(c)
                    }
                }
                if (contestsNeeded.isEmpty()) {
                    break
                }
            }
        }

        return selected
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Lightweight index built by pass 1 (build_row_index).
     *
     * Stores row indices (integers) rather than ballot data, so memory cost
     * is proportional to row count, not ballot width.
     *
     * rows_by_style_precinct uses "" as the precinct value for every row when
     * --redact-on-precinct is False, so a single dict handles both cases.
     *
     * rows_by_ballot_type is always populated when the BallotType column exists.
     * rows_by_named_style is populated only when --stylecol is given.
     * Only one of them is used for leakage detection (named_style takes priority).
     */
    // TODO go away
    class RowIndex {

        // styleName -> list of row indices
        val rowsByPrivacyUnit: MutableMap<String, MutableList<Int>> = mutableMapOf()

        // table of unique style strings; index into this list is the style ID
        val styleStrings: MutableList<String> = mutableListOf()

        // style ID (index into styleStrings) per row index; null for skip rows
        val styleIdForRow: MutableList<Int?> = mutableListOf()

        // named_style value -> list of row indices (only when --stylecol is given)
        val rowsByNamedStyle: MutableMap<String, MutableList<Int>> = mutableMapOf()

        // ballot_type value -> list of row indices (only when BallotType column exists)
        val rowsByBallotType: MutableMap<String, MutableList<Int>> = mutableMapOf()

        // total non-empty rows counted (includes skip rows)
        var totalRows: Int = 0

        /**
         * Return the style string for a row, or null if the row was skipped.
         */
        fun styleForRow(rowIdx: Int): String? {
            val styleId = styleIdForRow[rowIdx]
            return styleId?.let { styleStrings[it] }
        }
    }

    fun buildRowIndex(
        db: CvrDatabase,
        redactOnPrecinct: Boolean,
        checkMode: Boolean = false
    ): RowIndex {
        /*
        Pass 1: read every ballot row and build the lightweight row index.

        Stores row indices, not ballot data.  Skip rows (redacted ballots and
        aggregate rows from a prior run) are counted in totalRows but excluded
        from all grouping dicts.

        Row indices are 0-based from the first ballot row (row 5 in the file).
        Every non-empty row increments the index, including skip rows, so that
        indices are consistent across all three passes.
        */
        val index = RowIndex()
        val styleTable = mutableMapOf<String, Int>() // styleString -> style ID (index into styleStrings)
        val byPrivacyUnit = mutableMapOf<String, MutableList<Int>>()
        val byNamedStyle = mutableMapOf<String, MutableList<Int>>()
        val byBallotType = mutableMapOf<String, MutableList<Int>>()

        // val expectedCols = db.contests.size

        var rowIdx = 0
        for (row in db.corlaRawCvrs.cvrs()) {
            val rowStyle = styleMap[row.contests()]!!
            val styleStr = rowStyle.name
                if (!styleTable.containsKey(styleStr)) {
                    val styleId = index.styleStrings.size
                    styleTable[styleStr] = styleId
                    index.styleStrings.add(styleStr)
                }
                val styleId = styleTable[styleStr]!!
                index.styleIdForRow.add(styleId)
                val style = index.styleStrings[styleId]

                val pu = byPrivacyUnit.getOrPut(style) { mutableListOf() }
                pu.add(rowIdx)

                //db.namedStyleCol?.let {
                //    val namedStyle = row[it].trim()
                //    byNamedStyle.getOrPut(namedStyle) { mutableListOf() }.add(rowIdx)
                //}

                val ballotType = row.ballotType
                if (ballotType != null && ballotType.isNotEmpty()) {
                    byBallotType.getOrPut(ballotType) { mutableListOf() }.add(rowIdx)
                }
                rowIdx++
            }

        index.totalRows = index.styleIdForRow.size
        index.rowsByPrivacyUnit.putAll(byPrivacyUnit.toMap())
        index.rowsByNamedStyle.putAll(byNamedStyle.toMap())
        index.rowsByBallotType.putAll(byBallotType.toMap())
        return index
    }

    class RedactionNeeds {
        /**
         * Describes what the CVR requires before it can be safely published.
         *
         * Populated by check_redaction_needs().  The redaction logic reads this to
         * decide what work to do.
         *
         * Rare styles and rare (style, precinct) pairs both require the same kind of
         * treatment: ballots must be aggregated so no individual voter can be identified.
         */

        // Styles with too few ballots.
        // Key: style string.  Value: ballot count.
        val rareStyles: MutableMap<String, Int> = mutableMapOf()

        // Privacy units (style, precinct) with too few ballots.
        // When --redact-on-precinct is False, precinct is always "" so each key
        // is (style, "") and the unit is equivalent to the style alone.
        // Per C.R.S. 24-72-205.5, the privacy unit is the combination of contest TODO
        // pattern and precinct, not each independently.
        // Key: (style string, PrecinctPortion value).  Value: ballot count.
        val rarePrivacyUnitPairs: MutableMap<String, Int> = mutableMapOf()

        // Human-readable leakage warnings.  Leakage is reported but not corrected.
        val leakageWarnings: MutableList<String> = mutableListOf()

        fun needsRedaction(): Boolean {
            // Return True if any redaction work is required.
            return rareStyles.isNotEmpty() || rarePrivacyUnitPairs.isNotEmpty()
        }
    }

    // TODO go away
    inner class CvrDatabase(val corlaRawCvrs: CorlaRawCvrsIF) {
        val hasBallotType: Boolean = corlaRawCvrs.hasBallotType()
        val namedStyleCol = null

        /**
         * After construction the following are available:
         *
         *   contest_names         — ordered list of unique contest names
         *   contest_to_columns    — contest name -> list of column indices
         *   contest_choices      — contest name -> {col_idx, choice_name}
         */
        // Ordered list of unique contest names; contestId = idx.
        val contestNames: List<String>

        // contest name -> list of column indices for each contest's choices
        val contestColumns: Map<String, List<Int>>

        // contest name -> {choiceId, choice_name}; todo change to candId NOT column indices
        val contestChoicesColIdx: Map<String, Map<Int, String>>
        val contestChoiceIds: Map<Int, Map<Int, String>>  // contestId -> choiceId -> choiceName

        init {
            contestNames = corlaRawCvrs.schema.contests.map { it.contestName }

            contestColumns = corlaRawCvrs.schema.contests.map { scontest ->
                val colIndices = List(scontest.ncols) { scontest.startCol + it }
                Pair(scontest.contestName, colIndices)
            }.toMap()

            contestChoicesColIdx = corlaRawCvrs.schema.contests.map { scontest ->
                val choiceNames: List<String> = corlaRawCvrs.schema.choices(scontest.contestIdx)
                val choiceNameMap = choiceNames.mapIndexed { idx, choiceName -> Pair(scontest.startCol + idx, choiceName)}.toMap()
                Pair(scontest.contestName, choiceNameMap)
            }.toMap()

            contestChoiceIds = corlaRawCvrs.schema.contests.mapIndexed { idx, scontest ->
                val choiceNames: List<String> = corlaRawCvrs.schema.choices(scontest.contestIdx)
                val choiceNameMap = choiceNames.mapIndexed { idx, choiceName -> Pair(idx, choiceName)}.toMap()
                Pair(idx, choiceNameMap)
            }.toMap()
        }

        fun contestId(name: String) = contestNames.indexOf(name)

        fun contestChoiceName(contestId: Int, candId: Int): String {
            return contestChoiceIds[contestId]?.get(candId) ?: "unknown"
        }

        fun ballot_has_contest(row: CvrRow, contestName: String): Boolean {
            val style = styleMap[row.contests()]!!
            return style.contains(contestId(contestName))
        }

        fun ballot_has_contest(row: CvrRow, contestId: Int): Boolean {
            val style = styleMap[row.contests()]!!
            return style.contains(contestId)
        }
    }

    class Aggregate(
        initialBallots: List<CvrRow>,
        val rareContests: Set<Int>,
        val db: CvrDatabase,
        val minBallots: Int,
        val infos: Map<Int, ContestInfo>
    ) {

        val ballots: MutableList<CvrRow> = mutableListOf()
        val ballotIds: MutableSet<Int> = mutableSetOf()
        val contestTabs = mutableMapOf<Int, ContestTabulation>()

        // For each contest, how many ballots in the aggregate include that contest.
        // val contestBallotCounts: MutableMap<String, Int> = mutableMapOf()

        // Replace by tabulation
        // For each contest (the list of contests already exists in the db and will
        // not change), establish a dictionary which maps the contest name to an inner
        // dictionary (which will be filled in by add()) which will map the contest's
        // choices to the number of votes for each choice.
        //val contestChoiceCounts: MutableMap<String, MutableMap<String, Int>> = db.contestColumns.keys.associateWith { mutableMapOf<String, Int>() }
        //    .toMutableMap()

        init {
            initialBallots.forEach {
                add(it)
            }
        }

        fun contestBallotCounts(contestId: Int): Int {
            // val contestId= db.contestId(contestName)!!
            return contestTabs[contestId] ?. ncards() ?: 0
        }

        fun add(ballot: CvrRow) {
            ballots.add(ballot)
            ballotIds.add(System.identityHashCode(ballot))
            ballot.contestVotes.forEach {
                contestTabs.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
            }

            /* Add a ballot to the aggregate, updating all tracked counts.
            ballots.add(ballot)
            ballotIds.add(System.identityHashCode(ballot))

            for ((contestName, colIndices) in db.contestColumns) {
                val present = colIndices.any { idx -> ballot[idx].trim().isNotEmpty() }
                if (present) {
                    contestBallotCounts[contestName] = contestBallotCounts.getOrDefault(contestName, 0) + 1
                }
            }

            // for each contest
            for ((contestName, colMap) in db.contestChoices) {
                for ((colIdx, choiceName) in colMap) {
                    // who did they vote for ??
                    val value = ballot[colIdx].trim()
                    if (value.isEmpty() || value == "0") continue

                    val increment = value.toDoubleOrNull()?.toInt() ?: 1

                    // get the inner [choiceName:count] dict for the contest.
                    val counts = contestChoiceCounts.getOrPut(contestName) { mutableMapOf() }
                    counts[choiceName] = counts.getOrDefault(choiceName, 0) + increment
                }
            } */
        }

        fun containsBallot(ballot: CvrRow): Boolean {
            return ballotIds.contains(System.identityHashCode(ballot))
        }

        fun totalCount(): Int {
            return ballots.size
        }

        fun needsMoreTotalBallots(): Boolean {
            // True if the aggregate does not yet have min_ballots total (Rule a).
            return ballots.size < minBallots
        }

        fun contestsNeedingBallots(): Map<Int, Int> {
            // Return {contest: ballots_still_needed} for Rule b (each rare contest
            // needs >= min_ballots).
            val result = mutableMapOf<Int, Int>()
            for (contest in rareContests) {
                val count = contestBallotCounts(contest)
                if (count < minBallots) {
                    result[contest] = minBallots - count
                }
            }
            return result
        }

        fun satisfiesMinimums(): Boolean {
            // True when both Rule a and Rule b are satisfied.
            return !needsMoreTotalBallots() && contestsNeedingBallots().isEmpty()
        }

        //fun choiceCounts(): Map<Int, Map<String, Int>> {
            // Return per-contest per-choice vote counts (used for unanimity checking).
            //return contestChoiceCounts
        //}

        fun build(): List<Map<Int, ContestTabulation>> {
            return listOf(tabulateCvrRows(ballots, infos))
        }
    }

    class CommonPool(
        stylesIn: Map<String, List<CvrRow>>,
        val minBallots: Int,
        val rowCountByPrivacyUnit: Map<String, Int>,
        blockedStyles: Map<String, List<CvrRow>>? = null
    ) {
        /**
         * Pool of common-style ballots available for borrowing into the aggregate.
         *
         * Enforces Rule d: borrowing a ballot from a pair never leaves that pair with
         * fewer than min_ballots ballots remaining in the full dataset.  Surplus is
         * tracked against the full dataset count from pass 1 (via full_counts), not
         * against the number of donor rows loaded into memory, so that loading only a
         * subset of a pair's ballots does not artificially restrict borrowing.
         *
         * Keys are (style_string, precinct) tuples.  When --redact-on-precinct is
         * False, precinct is always "" so each key is (style_string, "").
         */

        val styles: MutableMap<String, MutableList<CvrRow>> = stylesIn.mapValues { it.value.toMutableList() }.toMutableMap()
        val removedCounts: MutableMap<String, Int> = mutableMapOf()
        val blockedStylesMap: MutableMap<String, MutableList<CvrRow>> = mutableMapOf()

        init {
            blockedStyles?.forEach { (key, rows) ->
                blockedStylesMap[key] = rows.toMutableList()
            }
        }

        fun surplus(key: String): Int {
            /**
             * How many more ballots this pair can donate while keeping
             * full_remaining >= min_ballots.  Positive means donating is allowed.
             */
            val full = rowCountByPrivacyUnit[key] ?: 0
            val removed = removedCounts[key] ?: 0
            return full - removed - minBallots
        }

        fun canDonate(key: String): Boolean {
            /** True if this pair can still donate at least one ballot without violating Rule d. */
            return surplus(key) > 0
        }

        fun isEmpty(): Boolean {
            return styles.isEmpty()
        }

        fun remove(key: String, rowIdx: Int) {
            /** Remove one ballot; drop the pair from the pool if it can no longer donate. */
            val rows = styles[key] ?: return
            rows.removeAt(rowIdx)
            removedCounts[key] = (removedCounts[key] ?: 0) + 1
            if (rows.isEmpty() || surplus(key) <= 0) {
                styles.remove(key)
            }
        }

        fun removeRows(rowsToRemove: List<CvrRow>) {
            /** Remove all ballots in rows_to_remove from the pool (matched by object identity). */
            val removeIds = rowsToRemove.map { System.identityHashCode(it) }.toSet()
            styles.keys.toList().forEach { key ->
                val rows = styles[key] ?: return@forEach
                val remaining = mutableListOf<CvrRow>()
                var removedHere = 0
                rows.forEach { row ->
                    if (System.identityHashCode(row) in removeIds) {
                        removedHere++
                    } else {
                        remaining.add(row)
                    }
                }
                if (removedHere > 0) {
                    removedCounts[key] = (removedCounts[key] ?: 0) + removedHere
                }
                if (remaining.isEmpty() || surplus(key) <= 0) {
                    styles.remove(key)
                } else {
                    styles[key] = remaining.toMutableList()
                }
            }
        }

        fun bestCandidateFor(
            aggregate: Aggregate,
            db: CvrDatabase
        ): BestCandidateFor? {
            /**
             * Return (key, row_idx, ballot) for the best ballot to borrow, or None.
             *
             * Prioritizes ballots that cover the most contests still needing ballots
             * (Rule b), weighted by how much they reduce vote imbalance.  Falls back
             * to any ballot from the pair with the most surplus when only the total
             * count is short (Rule a).
             */
            val contestsNeeded = aggregate.contestsNeedingBallots()
            return when {
                contestsNeeded.isNotEmpty() -> bestForContests(aggregate, db, contestsNeeded)
                aggregate.needsMoreTotalBallots() -> anyCandidate(aggregate)
                else -> null
            }
        }

        data class BestCandidateFor(val styleName: String, val idx: Int, val row: CvrRow)

        fun bestForContests(
            aggregate: Aggregate,
            db: CvrDatabase,
            contestsNeeded: Map<Int, Int> // contestId -> count rows needed
        ): BestCandidateFor? {
            /**
             * Find the best ballot for bringing the aggregation into agreement with Rule b
             * (has at least min_ballots per contest).
             *
             * The arg "needed" is a dictionary: {contest:number of ballots needed for that contest}
             */
            val neededList = contestsNeeded.filterValues { it > 0 }.keys.toList()
            var bestCandidate: BestCandidateFor? = null
            var bestScore = -1.0

            styles.forEach { (key, rowlist) ->
                if (!canDonate(key)) return@forEach
                rowlist.forEachIndexed { idx, row ->
                    if (aggregate.containsBallot(row)) return@forEachIndexed
                    val covered = neededList.filter { contestId ->
                        db.ballot_has_contest(row, contestId)
                    }
                    if (covered.isEmpty()) return@forEachIndexed
                    // "gain" indicates an improvement in the score, even though it is
                    // accomplished by a "reduction" in the imbalance.

                    //         fun imbalanceReduction(
                    //            contestId: Int,
                    //            ballot: CvrRow,
                    //            contestTabs: Map<Int, ContestTabulation>,
                    //            contestChoicesColId: Map<Int, Map<Int, String>>
                    val gain = covered.sumOf { contestId ->
                        // val contestId = db.contestId(it)
                        imbalanceReduction(contestId, row, aggregate.contestTabs, db.contestChoiceIds)
                    }
                    val score = COVERAGE_WEIGHT * covered.size + gain
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = BestCandidateFor(key, idx, row)
                    }
                }
            }

            return bestCandidate
        }

        fun anyCandidate(
            aggregate: Aggregate
        ): BestCandidateFor? {
            /** Pick any ballot from the pair with the most borrowing surplus. */
            var bestKey: String? = null
            var bestSurplus = 0
            styles.keys.forEach { key ->
                val s = surplus(key)
                if (s > 0 && s > bestSurplus) {
                    bestKey = key
                    bestSurplus = s
                }
            }
            val key = bestKey ?: return null
            styles[key]?.forEachIndexed { idx, row ->
                if (aggregate.containsBallot(row)) return@forEachIndexed
                return BestCandidateFor(key, idx, row)
            }
            return null
        }

        fun pullBlockedStyleFor(
            neededContests: Map<Int, Int>,
            db: CvrDatabase
        ): Pair<String, List<CvrRow>>? {
            /**
             * Find the blocked style (at exactly min_ballots rows in the full CVR) that
             * covers the most contests still needing ballots, pull all its rows, and return
             * (key, rows).  Returns None if no blocked style covers any needed contest.
             *
             * A blocked style can't donate individual ballots without violating Rule d, but
             * pulling the entire style into the aggregate is safe: all its voters become
             * part of the aggregate, and none are left as an identifiable minority.
             */
            val neededList = neededContests.filterValues { it > 0 }.keys
            if (neededList.isEmpty()) return null

            var bestKey: String? = null
            var bestCoverage = 0

            blockedStylesMap.forEach { (key, rows) ->
                var coverage = 0
                for (contest in neededList) {
                    for (row in rows) {
                        if (db.ballot_has_contest(row, contest)) {
                            coverage++
                            break // count each needed contest at most once
                        }
                    }
                }
                if (coverage > bestCoverage) {
                    bestCoverage = coverage
                    bestKey = key
                }
            }

            val key = bestKey ?: return null
            val rows = blockedStylesMap.remove(key) ?: return null
            return key to rows
        }

        /* fun _ballot_has_contest(
            ballot: CvrRow,
            contest: String,
            contestToColumns: Map<String, List<Int>>
        ): Boolean {
            // Return True if the ballot has any non-empty column for the given contest.
            val colIndices = contestToColumns[contest] ?: emptyList()
            return colIndices.any { ballot.contestVotes.isNotEmpty() }
        } */

        fun imbalanceReduction(
            contestId: Int,
            ballot: CvrRow,
            contestTabs: Map<Int, ContestTabulation>,
            contestChoicesColId: Map<Int, Map<Int, String>>
        ): Double { // TODO why float ?
            /*
            Estimate how much adding this ballot reduces vote imbalance for a contest.

            Imbalance is max_choice_votes minus the sum of all other votes.
            Returns the improvement (positive = less imbalanced), or 0.0 if the ballot
            does not participate in the contest or makes imbalance worse.
            */

            // python
            // "current" maps choices to votes for that contest.
            // "total" is the sum of all votes for all choices
            // current_max is the vote total for the choice with the highest number of votes.
            val currentTab = contestTabs[contestId]
            if (currentTab == null) return 0.0

            val currentTotal = currentTab.nvotes() ?: 0
            val currentMaxVote = currentTab.votes.maxByOrNull { it.value }
            if (currentMaxVote == null) return 0.0
            val currentMax = currentMaxVote.value
            val sumOfOtherVotes = (currentTotal - currentMax)
            val currentGap = currentMax - (currentTotal - currentMax) // = 2*currentMax - currentTotal

            // record the votes on this ballot for each of the choices available for this contest
            val contributions = mutableMapOf<Int, Int>()
            val cands = contestChoicesColId[contestId]
            if (cands != null) {
                for ((candId, choiceName) in cands) {
                    val value = ballot.candVote(contestId, candId)
                    if (value != null && value == 1) contributions[candId] = 1
                }
            }
            if (contributions.isEmpty()) {
                return 0.0
            }

            val newCounts = currentTab.votes.toMutableMap() // make a copy
            for ((candId, inc) in contributions) {
                newCounts[candId] = newCounts.getOrDefault(candId, 0) + inc
            }
            val newTotal = currentTotal + contributions.values.sum()
            val newMax = newCounts.values.maxOrNull() ?: 0
            val newGap = newMax - (newTotal - newMax)

            val python_result = max(0.0, (currentGap - newGap).toDouble())
            // println("   $contestId imbalanceReduction = $python_result")

            // a different way
            val orgImbalance = calcImbalance(currentTab.votes)

            val contestVote = ballot.contestVotes.find { it.contestId == contestId }
            if (contestVote == null) return 0.0

            contestVote.candVotes().forEach { (cand, vote) ->
                val candCount = newCounts[cand] ?: 0
                newCounts[cand] = candCount + vote
            }
            val voteForAny = contestVote.votedFor.count()
            val voteForMax = if (contestVote.votedFor.contains(currentMaxVote.key)) 1 else 0
            val imbalanceDiff = 2 * voteForMax - voteForAny

            // println("   my imbalanceDiff = $imbalanceDiff")

            return python_result
        }

        // votes: cand -> vote total, return (candid, imbalance)
        fun calcImbalance(votes: Map<Int, Int>): Pair<Int, Int>? {
            val currentTotal = votes.values.sum()
            val currentMaxVote = votes.maxByOrNull { it.value }
            if (currentMaxVote == null) return null
            val currentMax = currentMaxVote.value
            val sumOfOtherVotes = (currentTotal - currentMax)
            return Pair(currentMaxVote.key, currentMax - (currentTotal - currentMax)) // = 2*currentMax - currentTotal
        }
    }

    fun MutableMap<Int, ContestTabulation>.sumContestTabulationsFromVotes(info: ContestInfo, votes: Map<Int, Int>) {
        val contestSum = this.getOrPut(info.id) { ContestTabulation(info) }
        votes.forEach { (cand, vote) -> contestSum.addVote(cand, vote)}
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // redact

    fun stream_redacted_output(
        redactedAggregations: List<Map<Int, ContestTabulation>>,
        redactedRows: Set<Int>
    ) {

        /**
         * Pass 3: stream input to output, redacting rows in redacted_row_indices,
         * appending the aggregate row (if any), and verifying vote tallies match.
         *
         * pre_tally  — running sum of original vote columns for every input row.
         * post_tally — running sum of vote columns for non-redacted rows, plus the
         *              aggregate row.  Must equal pre_tally if redaction is correct.
         */
        // Columns that carry no information in the output and should be omitted entirely.
        // CountingGroup is always removed.
        // PrecinctPortion is removed when not redacting on precinct, because every row has it blanked in that case.

        File(outputFile).bufferedWriter(Charsets.UTF_8).use { bwriter ->
            // write headers
            corlaCvrs.headers().forEach {
                bwriter.write(it)
                bwriter.newLine()
            }

            corlaCvrs.cvrs().forEachIndexed { idx, row ->
                if (redactedRows.contains(idx)) {
                    bwriter.write(writeRow(row, true))
                } else {
                    // write unredacted
                    bwriter.write(writeRow(row, false))
                }
            }

            // might want to use the style at some point
            redactedAggregations.forEach { agg ->
                val last = schema.nchoices
                val aggrow = buildString {
                    append("AGGREGATED")
                    repeat(schema.nheaders - 2) { append(",") }
                    append("AGGREGATED,")
                    var count = 0
                    schema.contests.forEach { scontest ->
                        val tab = agg[scontest.contestIdx]
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

                if (addNrows) {
                    val ncardrow = buildString {
                        append("AGGREGATED")
                        repeat(schema.nheaders - 2) { append(",") }
                        append("${redactedRows.size},")
                        append("NCARDS,")
                        var count = 0
                        schema.contests.forEach { scontest ->
                            val tab = agg[scontest.contestIdx]
                            repeat(scontest.ncols) {
                                count++
                                if (tab == null) {
                                    append("0")
                                    if (count != last) append(",")
                                } else {
                                    val ncards = tab.ncards()
                                    append("$ncards")
                                    if (count != last) append(",")
                                }
                            }
                        }
                        appendLine()
                    }
                    bwriter.write(ncardrow)
                }
            }
        }
    }

    fun writeRow(row: CvrRow, redacted: Boolean) = buildString {
        append(corlaCvrs.csvHeader(row, redactPrecinct = redactPrecinct))
        val last = schema.nchoices
        val rowMap: Map<Int, List<Int>> = row.contestVotes.map { Pair(it.contestId, it.votedFor) }.toMap()
        var count = 0
        schema.contests.forEach { scontest ->
            if (scontest.contestIdx == 20)
                print("")
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

    fun perform_redaction(
        db: CvrDatabase,
        index: RowIndex,
        needs: RedactionNeeds,
        minBallots: Int,
        outputFile: String,
        redactOnPrecinct: Boolean,
        redactedListFile: String?,
        noContestBalancing: Boolean = false
    ) {
        val stopwatch = Stopwatch()

        var redactedRowIndices = emptySet<Int>()
        var redactedAggregations = emptyList<Map<Int, ContestTabulation>>()

        // Orchestrate passes 2 and 3 to produce the anonymized CVR.
        if (needs.needsRedaction()) {

            if (noContestBalancing) {
                redactedAggregations = rowsByRareStyle.values.map { rowlist -> tabulateCvrRows(rowlist, infos) }
                redactedRowIndices = rareRowIndices.toSet()

            } else {
                val result = loadDonorPool(index, needs, db, minBallots, redactOnPrecinct)
                val rareRowIndices = result.first
                val aggregate = result.second

                var rareCount = 0
                for ((key, rowIndices) in index.rowsByPrivacyUnit) {
                    if (key in needs.rarePrivacyUnitPairs) {
                        rareCount += rowIndices.size
                    }
                }

                println("\n*** Pass 2 complete.")
                println("  Ballots from rare styles/precincts: $rareCount")
                if (!noContestBalancing) {
                    val borrowedCount = rareRowIndices.size - rareCount
                    if (borrowedCount > 0) {
                        println("  Ballots borrowed from common styles: $borrowedCount")
                    }
                }
                println("  Total ballots in aggregate: ${rareRowIndices.size}")

                redactedAggregations = aggregate.build()
                redactedRowIndices = rareRowIndices
            }
        }
        println("\n*** Pass 3: Writing output.")
        stream_redacted_output(redactedAggregations, redactedRowIndices)
        println("  Output written to $outputFile.")

        if (redactedListFile != null) {
            println("  Redacted ballot list written to $redactedListFile.")
        }

        println("That took $stopwatch")
    }

    fun execute_redact() {
        val db = CvrDatabase(corlaCvrs)

        println("*** Pass 1: Looking for rare ballot styles.")
        println()
        val index = buildRowIndex(db, redactOnPrecinct, checkMode = true)

        val needs = checkRedactionNeeds(index, db, minBallots, redactOnPrecinct)

        if (warnLeakage) {
            for (warning in needs.leakageWarnings) {
                System.err.println("WARNING: $warning")
            }
        }

        reportCheckResults(index, db, needs, redactOnPrecinct)

        perform_redaction(
            db,
            index,
            needs,
            minBallots,
            outputFile!!,
            redactOnPrecinct,
            redactedListFile,
            noContestBalancing
        )
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // check

    fun checkRedactionNeeds(
        index: RowIndex,
        db: CvrDatabase,
        minBallots: Int,
        redactOnPrecinct: Boolean, // not supporting yet
    ): RedactionNeeds {
        val needs = RedactionNeeds()

        // Compute per-style totals by summing across all (style, precinct) keys.
        // When redact_on_precinct is False, all precincts are "" so each style has
        // exactly one key and the sum equals the style's total ballot count.
        val styleTotals = mutableMapOf<String, Int>().withDefault { 0 }
        for ((style, rowIndices) in index.rowsByPrivacyUnit) {
            styleTotals[style] = styleTotals.getValue(style) + rowIndices.size
        }

        for ((style, total) in styleTotals) {
            if (total < minBallots) {
                needs.rareStyles[style] = total
            }
        }

        // Always populate rare_privacy_unit_pairs.
        // When redact_on_precinct is False, all precincts are "" so each key is
        // (style, "") and the count equals the style's total ballot count.
        for ((stylePrecinctPair, rowIndices) in index.rowsByPrivacyUnit) {
            if (rowIndices.size < minBallots) {
                needs.rarePrivacyUnitPairs[stylePrecinctPair] = rowIndices.size
            }
        }

        // Leakage detection (Rule 10): use named_style if --stylecol was given,
        // otherwise use ballot_type.  Only one check is run.
        if (db.namedStyleCol != null) {
            val namedStylesByStyle = mutableMapOf<String, MutableSet<String>>()
            for ((namedStyle, rowIndices) in index.rowsByNamedStyle) {
                for (rowIdx in rowIndices) {
                    val rowStyle = index.styleForRow(rowIdx)
                    if (rowStyle != null) {
                        namedStylesByStyle
                            .getOrPut(rowStyle) { mutableSetOf() }
                            .add(namedStyle)
                    }
                }
            }
            for ((style, namedStyles) in namedStylesByStyle) {
                if (namedStyles.size > 1) {
                    val names = namedStyles.sorted().joinToString(", ")
                    needs.leakageWarnings.add(
                        "Leakage: named styles [$names] all share the same contest pattern"
                    )
                }
            }
        } else if (db.hasBallotType) {
            val ballotTypesByStyle = mutableMapOf<String, MutableSet<String>>()
            for ((ballotType, rowIndices) in index.rowsByBallotType) {
                for (rowIdx in rowIndices) {
                    val rowStyle = index.styleForRow(rowIdx)
                    if (rowStyle != null) {
                        ballotTypesByStyle
                            .getOrPut(rowStyle) { mutableSetOf() }
                            .add(ballotType)
                    }
                }
            }
            for ((style, ballotTypes) in ballotTypesByStyle) {
                if (ballotTypes.size > 1) {
                    val types = ballotTypes.sorted().joinToString(", ")
                    needs.leakageWarnings.add(
                        "Leakage: ballot types [$types] all share the same contest pattern"
                    )
                }
            }
        }

        return needs
    }

    fun reportCheckResults(
        index: RowIndex,
        db: CvrDatabase,
        needs: RedactionNeeds,
        redactOnPrecinct: Boolean
    ) {
        // Print the rare-style report and redaction verdict to stdout.
        val ballotTypesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
        for ((ballotType, rowIndices) in index.rowsByBallotType) {
            for (rowIdx in rowIndices) {
                val style = index.styleForRow(rowIdx)
                if (style != null) {
                    ballotTypesByStyle.getOrPut(style) { mutableSetOf() }.add(ballotType)
                }
            }
        }

        val namedStylesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
        for ((namedStyle, rowIndices) in index.rowsByNamedStyle) {
            for (rowIdx in rowIndices) {
                val style = index.styleForRow(rowIdx)
                if (style != null) {
                    namedStylesByStyle.getOrPut(style) { mutableSetOf() }.add(namedStyle)
                }
            }
        }

        val totalStyles = index.styleStrings.size
        val showPrecinct = false // redactOnPrecinct && db.precinctPortionIdx != null

        if (needs.rarePrivacyUnitPairs.isNotEmpty()) {
            if (showPrecinct) {
                val totalPairs = index.rowsByPrivacyUnit.size
                println(
                    "  Rare ballot style/precinct combinations " +
                            "(${needs.rarePrivacyUnitPairs.size} of $totalPairs total):"
                )
            } else {
                println(
                    "  Rare ballot styles " +
                            "(${needs.rarePrivacyUnitPairs.size} of $totalStyles total):"
                )
            }
            val styleToId: MutableMap<String, Int> = mutableMapOf()
            for ((i, s) in index.styleStrings.withIndex()) {
                styleToId[s] = i
            }

            for ((style, count) in needs.rarePrivacyUnitPairs.toSortedMap()) {
                val ballotTypes = ballotTypesByStyle[style] ?: emptySet()
                val namedStylesForStyle = namedStylesByStyle[style] ?: emptySet()

                val parts = mutableListOf<String>()
                if (ballotTypes.isNotEmpty()) {
                    parts.add(
                        "ballot type: " + ballotTypes.sorted().joinToString(", ") { "\"$it\"" }
                    )
                } else if (namedStylesForStyle.isNotEmpty()) {
                    parts.add(
                        "named style: " + namedStylesForStyle.sorted().joinToString(", ") { "\"$it\"" }
                    )
                }
                parts.add("style #${styleToId[style]}")
                println("    $count ballot(s)  [${parts.joinToString(", ")}]")
            }
        }

        if (needs.needsRedaction()) {
            println("\nRedaction is needed.")
        } else {
            println("No redaction needed.")
        }
    }

    // Print the rare-style report and redaction verdict to stdout.
    fun execute_check() {
        val db = CvrDatabase(corlaCvrs)

        println("*** Pass 1: Looking for rare ballot styles.")
        println()
        val index = buildRowIndex(db, redactOnPrecinct, checkMode = true)

        val needs = checkRedactionNeeds(index, db, minBallots, redactOnPrecinct)
        for (warning in needs.leakageWarnings) {
            System.err.println("WARNING: $warning")
        }
        reportCheckResults(index, db, needs, redactOnPrecinct)
    }

}
