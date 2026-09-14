package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromVotes
import java.io.File
import kotlin.math.max

/*
Anonymize Cast Vote Records (CVR) per Colorado C.R.S. 24-72-205.5.

Ballot styles with fewer than MIN_BALLOTS ballots must be aggregated to
protect voter privacy.

Terminology used throughout this module:

  style         — the contest pattern for a ballot, represented as a string
                  of '1' and '0' characters.  Each position corresponds to
                  one contest (in the order contests appear in the CVR file);
                  '1' means the contest is present on the ballot, '0' means
                  it is absent.  This is the only style definition that
                  drives redaction decisions.

  named_style   — the value in the column identified by --stylecol, if any.
                  Assigned by the voting machine; almost certainly obsolete
                  in Colorado, but still accepted.

  ballot_type   — the value in the BallotType column, if present.
*/

private val logger = KotlinLogging.logger("AnonymizeCvr2")

private const val VERSION = "0.2"

private const val MIN_BALLOTS_DEFAULT = 10
private const val NEAR_UNANIMOUS_THRESHOLD = 2  // "all but N votes" triggers balancing (Rule c)
private const val MIN_CONTRASTING_VOTES = 3  // contrasting votes needed per contest after balancing
private const val COVERAGE_WEIGHT = 10.0  // weight for contest coverage vs. vote-balance score
private const val DONOR_SURPLUS_THRESHOLD = 3  // minimum surplus above min_ballots for a style/precinct to donate freely

class Anonymize(
    val input: String,
    val minBallots: Int,
    val outputFile: String?,
    val redactOnPrecinct: Boolean,
    val styleCol: Int?, // = null,
    val noContestBalancing: Boolean,
    val redactedListFile: String?,
) {
    val corlaCvrs = readCorlaCvrs(input)
    val schema = corlaCvrs.schema
    val infos: Map<Int, ContestInfo>
    val styleMap: Map<Set<Int>, CvrCardStyle>
    val rareStyleMap: Map<Set<Int>, CvrCardStyle>
    val redactedRowIndices: List<Int>
    val rareRowsMap: Map<CvrCardStyle, MutableList<CvrRow>>
    
    init {
        infos = corlaCvrs.makeContestInfo().map { it ->
            ContestInfo(
                it.name, it.id, it.candidateNames,
                if (it.isIrv) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY,
                it.nwinners
            )
        }.associateBy { it.id }
        
        styleMap = corlaCvrs.ballotStyles.cardStyleMap
        rareStyleMap = styleMap.filter { it.value.countCards < minBallots }

        println("*** Pass 2: Collecting rare ballots (no contest balancing).")
        val redacted_row_indices = mutableListOf<Int>() // needed ?
        val rare_rows_map = mutableMapOf<CvrCardStyle, MutableList<CvrRow>>()
        corlaCvrs.cvrs().forEachIndexed { idx, cvr ->
            val contestIds = cvr.contestVotes.map { it.contestId }.toSet()
            val rareStyle = rareStyleMap[contestIds]
            if (rareStyle != null) {
                val rareRows = rare_rows_map.getOrPut(rareStyle) { mutableListOf() }
                rareRows.add(cvr)
                redacted_row_indices.add(idx)
            }
        }
        redactedRowIndices = redacted_row_indices
        rareRowsMap = rare_rows_map
    }

    /*
    Pass 2: stream the file, load rare rows and a targeted donor pool,
    build the aggregate, and return the set of aggregated row indices
    and the aggregate summary row.

    Returns:
        redacted_row_indices  — 0-based row indices of all ballots in the aggregate
        aggregate_row         — the AGGREGATED summary row to append to the output

    fun load_donor_pool(
        db: CvrDatabase2,
        needs: RedactionNeeds2,
    ): Pair<Set<Int>, List<String>> {

        val perContestPoolTarget = 10 * minBallots

        // Pre-compute how many rows contain each contest.
        val cvrTabulation: Map<Int, ContestTabulation> = tabulate(corlaCvrs.cvrs)
        val contestTotalCounts = cvrTabulation.mapValues { it.value.ncards() }

        /* Each (style, precinct) key's style string encodes which contests are present.
        val contestTotalCounts = mutableMapOf<String, Int>().withDefault { 0 }
        //     for (style, _), row_indices in index.rows_by_privacy_unit.items():
        for ((stylePrecinct, rowIndices) in index.rowsByPrivacyUnit) { // (style_string, precinct) -> list of row indices
            val count = rowIndices.size                                // style string = Tuple[str, str] = style ,precinct
            for ((i, contestName) in db.contestNames.withIndex()) {  // contestNames = Ordered list of unique contest names (defines positions in style strings).
                if (stylePrecinct.first[i] == '1') {  // if the
                    contestTotalCounts[contestName] = contestTotalCounts.getValue(contestName) + count
                }
            }
        } */

        val rarePrivacyUnitSet = needs.rarePrivacyUnitPairs.keys.toSet()

        // Rare-ballot contests: any contest present on at least one rare ballot. TODO
        val styleMap: Map<Set<Int>, CvrCardStyle> = corlaCvrs.ballotStyles.cardStyleMap
        val rareStyleMap: Map<Set<Int>, CvrCardStyle> = styleMap.filter { it.value.countCards < minBallots }

        // all contests on a rare style
        // val rareBallotContests: Set<Int> = rareStyleMap.map { it.key }.flatten().toSet()

        val rareBallotContests = mutableSetOf<String>() //  why not id ?
        for (style in rarePrivacyUnitSet) {
            db.contestNames.forEachIndexed { idx, contestName ->
                if (style[i] == '1') {  // WTF ??
                    rareBallotContests.add(contestName)
                }
            }
        }

        // all contests on a any ballot that has a rare style. Is this different ??
        // Rare contests: rare-ballot contests with fewer than perContestPoolTarget total appearances in the full CVR.
        val rareContests: Set<Int> = rareBallotContests.filterTo(mutableSetOf()) {
            contestTotalCounts.getValue(it) < perContestPoolTarget
        }



        // Row counts per privacy unit, passed to CommonPool2 for Rule d enforcement.
        // assume privacy unit is a style,
        // val rowCountByPrivacyUnit = index.rowsByPrivacyUnit.mapValues { it.value.size }.toMutableMap()
        val rowCountByPrivacyUnit = emptyMap<String, Int>()

        //////////////////// wtf
        // Tracks how many donor rows have been loaded per rare-ballot contest.
        val donorCountByRareBallotContest = mutableMapOf<String, Int>().withDefault { 0 }

        // Tracks vote distribution of loaded donors per rare-ballot contest.
        // contest_name -> {choice_name: count of donors voting "1" for that choice}
        // Used to ensure we load enough contrasting donors to fix near-unanimity.
        val donorVotedTally = mutableMapOf<String, MutableMap<String, Int>>() // contest -> choice -> votes

        val donorByPrivacyUnit =
            mutableMapOf<String, MutableList<CvrRow>>().withDefault { mutableListOf() }
        val blockedByPrivacyUnit =
            mutableMapOf<String, MutableList<CvrRow>>().withDefault { mutableListOf() }

        // Maps id(row) to rowIdx for all loaded rows, used to identify aggregated rows.
        val rowToIdx = mutableMapOf<Int, Int>()
        val rareRows = mutableListOf<CvrRow>()

        var rowIdx = 0
        for (row in corlaCvrs.cvrs) {
            val rowStyle = styleMap[row.contests()]!!
            val privacyUnit = rowStyle.name

            if (privacyUnit in rarePrivacyUnitSet) {
                rareRows.add(row)
                rowToIdx[row.hashCode()] = rowIdx  // TODO hashcode !!
            } else {

                val privacyUnitCount = rowCountByPrivacyUnit[privacyUnit] ?: 0
                if (privacyUnitCount <= minBallots) {
                    if (privacyUnitCount == minBallots) {
                        // Blocked style: at exactly minBallots, can't donate
                        // individual ballots without violating Rule d.  Track for
                        // potential whole-style pull if individual borrowing stalls.
                        val styleHasRareContest = db.contestNames.withIndex().any { (i, contestName) ->
                            contestName in rareBallotContests && rowStyle[i] == '1'
                        }
                        if (styleHasRareContest) {
                            blockedByPrivacyUnit.getValue(privacyUnit).add(row)
                            rowToIdx[row.hashCode()] = rowIdx
                        }
                    }
                    continue
                }

                // Find which rare-ballot contests are present on this row.
                val rowRareContests = mutableListOf<String>()
                //rareBallotContests.filter {
                //    ballotHasContest(row, it, db.contest_to_columns)
                //}

                if (rowRareContests.isEmpty()) continue

                if (privacyUnitCount <= minBallots + DONOR_SURPLUS_THRESHOLD) {
                    // Tight surplus: only load if this row has a rare contest.
                    val hasRareContest = rowRareContests.any { it in rareContests }
                    if (!hasRareContest) continue
                } else {
                    // Comfortable surplus: load if pool needs more donors for any
                    // rare-ballot contest, OR if this row provides contrasting votes
                    // for a contest where loaded donors are currently too one-sided.
                    val poolNeedsMore = rowRareContests.any {
                        donorCountByRareBallotContest.getValue(it) < perContestPoolTarget
                    }

                    if (!poolNeedsMore) {
                        var needsContrast = false
                        for (contestName in rowRareContests) {
                            val tally = donorVotedTally[contestName] ?: continue

                            // For this contest, find the choice with the most votes.
                            val maxChoiceName: String? = tally.maxByOrNull { it.value }?.key

                            var rowVoted: String? = null
                            for ((colIdx, choiceName) in db.contestChoiceMeta[contestName] ?: emptyMap()) {
                                if (row[colIdx].trim() == "1") {
                                    rowVoted = choiceName
                                    break
                                }
                            }
                            if (rowVoted == null || rowVoted == maxChoiceName) {
                                continue
                            }
                            var contrastingSoFar = 0
                            for ((c, v) in tally) {
                                if (c != maxChoiceName) {
                                    contrastingSoFar += v
                                }
                            }
                            if (contrastingSoFar < MIN_CONTRASTING_VOTES) {
                                needsContrast = true
                                break
                            }
                        }
                        if (!needsContrast) {
                            continue
                        }
                    }
                }

                donorByPrivacyUnit.getValue(privacyUnit).add(row)
                rowToIdx[row.hashCode()] = rowIdx
                rowRareContests.forEach { contestName ->
                    donorCountByRareBallotContest[contestName] =
                        donorCountByRareBallotContest.getValue(contestName) + 1
                    val tally = donorVotedTally.getOrPut(contestName) { mutableMapOf() }
                    db.contestChoiceMeta[contestName]?.forEach { (colIdx, choiceName) ->
                        if (row[colIdx].trim() == "1") {
                            tally[choiceName] = tally.getValue(choiceName) + 1
                        }
                    }
                }
            }
            rowIdx++
        }

        val pool = CommonPool2(
            donorByPrivacyUnit.mapValues { it.value.toList() },
            minBallots,
            rowCountByPrivacyUnit,
            blockedByPrivacyUnit.mapValues { it.value.toList() }
        )

        val aggregate = buildAggregate2(rareRows, rareBallotContests, pool, db, minBallots)
        val borrowedAfterAb = aggregate.totalCount() - rareRows.size
        if (borrowedAfterAb > 0) {
            println("  Ballots borrowed for minimum counts: $borrowedAfterAb")
        }

        println("\n*** Balancing near-unanimous contests.\n")
        println("  Make sure that the following constraint is met:")
        println("  - No contest in the aggregate may be near-unanimous. 'Near-unanimous'")
        println("    means all but ${NEAR_UNANIMOUS_THRESHOLD} votes go to a single choice.")
        println()
        // Loop until no near-unanimous contests remain or the pool is exhausted.
        // Each pass either adds at least one ballot (shrinking the finite pool) or
        // adds nothing, in which case we are stuck and exit.  Termination is guaranteed.
        var stillProblematic: List<NearUnanimousContest> = listOf()
        while (true) {
            val countBefore = aggregate.totalCount()
            balanceUnanimity(aggregate, pool, db)
            stillProblematic = findNearUnanimousContests(aggregate, db)
            if (stillProblematic.isEmpty() || aggregate.totalCount() == countBefore) {
                break
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
            rowToIdx[ballot.hashCode()]?.let { redactedRowIndices.add(it) }
        }

        val aggregateRow = buildAggregateRow(aggregate.ballots, db, "AGGREGATED")

        return redactedRowIndices to aggregateRow
    } */

    /////////////////////////////////////////////////////////////////////////////////////////////

    fun loadDonorPool(
        csvPath: String,
        index: RowIndex,
        needs: RedactionNeeds,
        db: CvrDatabase,
        minBallots: Int,
        redactOnPrecinct: Boolean
    ): Pair<Set<Int>, List<String>> {
        /*
        Pass 2: stream the file, load rare rows and a targeted donor pool,
        build the aggregate, and return the set of aggregated row indices
        and the aggregate summary row.

        Returns:
            redacted_row_indices  — 0-based row indices of all ballots in the aggregate
            aggregate_row         — the AGGREGATED summary row to append to the output
        */
        val perContestPoolTarget = 10 * minBallots

        // Pre-compute how many rows contain each contest.
        // Each (style, precinct) key's style string encodes which contests are present.
        val contestTotalCounts = mutableMapOf<String, Int>()
        index.rowsByPrivacyUnit.forEach {
        // for ((style, _), rowIndices) in index.rowsByPrivacyUnit {
            val count = rowIndices.size
            for ((i, contestName) in db.contestNames.withIndex()) {
                if (style[i] == '1') {
                    contestTotalCounts[contestName] = contestTotalCounts.getOrDefault(contestName, 0) + count
                }
            }
        }

        val rarePrivacyUnitSet = needs.rarePrivacyUnitPairs.keys.toMutableSet()

        // Rare-ballot contests: any contest present on at least one rare ballot.
        val rareBallotContests = mutableSetOf<String>()
        for ((style, _) in rarePrivacyUnitSet) {
            for ((i, contestName) in db.contestNames.withIndex()) {
                if (style[i] == '1') {
                    rareBallotContests.add(contestName)
                }
            }
        }

        // Rare contests: rare-ballot contests with fewer than perContestPoolTarget
        // total appearances in the full CVR.
        val rareContests = mutableSetOf<String>()
        for (contestName in rareBallotContests) {
            if (contestTotalCounts[contestName] ?: 0 < perContestPoolTarget) {
                rareContests.add(contestName)
            }
        }

        // Row counts per privacy unit, passed to CommonPool for Rule d enforcement.
        val rowcountByPrivacyUnit = mutableMapOf<Pair<String, String>, Int>()
        for ((key, rowIndices) in index.rowsByPrivacyUnit) {
            rowcountByPrivacyUnit[key] = rowIndices.size
        }

        // Tracks how many donor rows have been loaded per rare-ballot contest.
        val donorCountByRareBallotContest = mutableMapOf<String, Int>()

        // Tracks vote distribution of loaded donors per rare-ballot contest.
        // contest_name -> {choice_name: count of donors voting "1" for that choice}
        // Used to ensure we load enough contrasting donors to fix near-unanimity.
        val donorVotedTally = mutableMapOf<String, MutableMap<String, Int>>()

        val rareRows = mutableListOf<List<String>>()
        val donorByPrivacyUnit = mutableMapOf<Pair<String, String>, MutableList<List<String>>>()
        val blockedByPrivacyUnit = mutableMapOf<Pair<String, String>, MutableList<List<String>>>()

        // Maps id(row) to rowIdx for all loaded rows, used to identify aggregated rows.
        val rowToIdx = mutableMapOf<Int, Int>()

        println("*** Pass 2: Building the aggregate row.")
        println()

        File(csvPath).bufferedReader(Charsets.UTF_8).use { reader ->
            val csvReader = reader.lineSequence().iterator()
            repeat(4) { csvReader.next() }

            for ((rowIdx, row) in csvReader.asSequence().filter { it.trim().split(",").any { value -> value.isNotBlank() } }.withIndex()) {
                val rowStyle = index.styleForRow(rowIdx) ?: continue

                val precinct = ""
                val privacyUnit = Pair(rowStyle, precinct)

                if (privacyUnit in rarePrivacyUnitSet) {
                    rareRows.add(row.split(","))
                    rowToIdx[row.split(",").hashCode()] = rowIdx
                } else {
                    val privacyUnitCount = rowcountByPrivacyUnit.getOrDefault(privacyUnit, 0)
                    if (privacyUnitCount <= minBallots) {
                        if (privacyUnitCount == minBallots) {
                            // Blocked style: at exactly minBallots, can't donate
                            // individual ballots without violating Rule d.  Track for
                            // potential whole-style pull if individual borrowing stalls.
                            var styleHasRareContest = false
                            for ((i, contestName) in db.contestNames.withIndex()) {
                                if (contestName in rareBallotContests && rowStyle[i] == '1') {
                                    styleHasRareContest = true
                                    break
                                }
                            }
                            if (styleHasRareContest) {
                                blockedByPrivacyUnit.computeIfAbsent(privacyUnit) { mutableListOf() }.add(row.split(","))
                                rowToIdx[row.split(",").hashCode()] = rowIdx
                            }
                        }
                        continue
                    }

                    // Find which rare-ballot contests are present on this row.
                    val rowRareContests = rareBallotContests.filter { contestName ->
                        _ballotHasContest(row.split(","), contestName, db.contestToColumns)
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
                        val poolNeedsMore = rowRareContests.any {
                            donorCountByRareBallotContest.getOrDefault(it, 0) < perContestPoolTarget
                        }

                        if (!poolNeedsMore) {
                            val needsContrast = rowRareContests.any { contestName ->
                                val tally = donorVotedTally[contestName] ?: return@any false

                                // For this contest, find the choice with the most votes.
                                var maxChoice: String? = null
                                var maxCount = -1
                                for ((choiceName, count) in tally) {
                                    if (count > maxCount) {
                                        maxCount = count
                                        maxChoice = choiceName
                                    }
                                }

                                val rowVoted = db.contestChoiceMeta[contestName]?.entries?.firstOrNull { (colIdx, _) ->
                                    row.split(",")[colIdx].trim() == "1"
                                }?.value
                                if (rowVoted == null || rowVoted == maxChoice) {
                                    return@any false
                                }
                                val contrastingSoFar = tally.filter { it.key != maxChoice }.values.sum()
                                contrastingSoFar < MIN_CONTRASTING_VOTES
                            }
                            if (!needsContrast) {
                                continue
                            }
                        }
                    }

                    donorByPrivacyUnit.computeIfAbsent(privacyUnit) { mutableListOf() }.add(row.split(","))
                    rowToIdx[row.split(",").hashCode()] = rowIdx
                    for (contestName in rowRareContests) {
                        donorCountByRareBallotContest[contestName] = donorCountByRareBallotContest.getOrDefault(contestName, 0) + 1
                        val tally = donorVotedTally.computeIfAbsent(contestName) { mutableMapOf() }
                        for ((colIdx, choiceName) in db.contestChoiceMeta.getOrDefault(contestName, emptyMap())) {
                            if (row.split(",")[colIdx].trim() == "1") {
                                tally[choiceName] = tally.getOrDefault(choiceName, 0) + 1
                                break
                            }
                        }
                    }
                }
            }
        }

        val pool = CommonPool(
            donorByPrivacyUnit.mapValues { it.value.toList() },
            minBallots,
            rowcountByPrivacyUnit,
            blockedByPrivacyUnit.mapValues { it.value.toList() }
        )

        val aggregate = buildAggregate(rareRows.toList(), rareBallotContests, pool, db, minBallots)
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
        var stillProblematic: List<Triple<String, String?, Int>> = emptyList()
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
            rowToIdx[ballot.hashCode()]?.let { redactedRowIndices.add(it) }
        }

        val aggregateRow = _build_aggregate_row(aggregate.ballots, db, "AGGREGATED")

        return Pair(redactedRowIndices, aggregateRow)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    fun printBorrowingNeeds(aggregate: Aggregate, minBallots: Int) {
        // Print a one-time summary of why ballot borrowing is needed.
        if (aggregate.needsMoreTotalBallots()) {
            val needed = minBallots - aggregate.totalCount()
            println(
                "  Aggregate has ${aggregate.totalCount()} ballot(s); " +
                        "need $needed more to reach minimum of $minBallots."
            )
        }
        val contestNeeds = aggregate.contestsNeedingBallots()
        if (contestNeeds.isNotEmpty()) {
            println("  Contests below minimum:")
            for ((contest, needed) in contestNeeds.toSortedMap()) {
                println("    '$contest': needs $needed more ballot(s)")
            }
        }
    }

    fun buildAggregate(
        rareBallots: List<List<String>>,
        rareContests: Set<String>,
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
        val aggregate = Aggregate(rareBallots, rareContests, db, minBallots)

        if (!aggregate.satisfiesMinimums()) {
            println("  Rare ballots: ${aggregate.totalCount()}.")
            println("  Borrowing from common styles to satisfy these requirements:")
            println(
                "  - the ballot aggregation must contain at least $minBallots ballots."
            )
            println(
                "  - every contest in the rare styles must appear on at least" +
                        " $minBallots ballots in the aggregation."
            )
            println()
            printBorrowingNeeds(aggregate, minBallots)
            println(
                "\n  Selecting ballots to borrow from common styles (this can take a few minutes)..."
            )
        }

        var borrowedCount = 0

        while (!aggregate.satisfiesMinimums()) {
            val result = pool.bestCandidateFor(aggregate, db)
            if (result == null) {
                val blocked = pool.pullBlockedStyleFor(
                    aggregate.contestsNeedingBallots(), db
                )
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
    fun findNearUnanimousContests(
        aggregate: Aggregate,
        db: CvrDatabase
    ): List<Triple<String, String?, Int, Int>> {
        /*
        Return (contest_name, max_choice, max_votes, total_votes) for every rare contest
        in the aggregate that is near-unanimous.

        Contests with only one available choice are excluded: they are always unanimous
        by definition and nothing can be done about it.  The check uses the number of
        choice columns in the CVR, not the number of choices that received votes, so a
        two-choice contest where all ballots voted for the same candidate is NOT excluded.
        */
        val result = mutableListOf<Triple<String, String?, Int, Int>>()
        for ((contestName, choiceVotes) in aggregate.choiceCounts()) {
            if (contestName !in aggregate.rareContests) {
                continue
            }
            if (choiceVotes.isEmpty()) {
                continue
            }
            if ((db.contestToColumns[contestName]?.size ?: 0) <= 1) {
                continue
            }
            val totalVotes = choiceVotes.values.sum()
            if (totalVotes == 0) {
                continue
            }
            var maxChoice: String? = null
            var maxVotes = 0
            for ((choice, votes) in choiceVotes) {
                if (votes > maxVotes) {
                    maxVotes = votes
                    maxChoice = choice
                }
            }
            val otherVotes = totalVotes - maxVotes
            if (otherVotes <= NEAR_UNANIMOUS_THRESHOLD) {
                result.add(Triple(contestName, maxChoice, maxVotes, totalVotes))
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
            println(
                "    '$contestName': '$maxChoice' has $maxVotes out of $totalVotes votes"
            )
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

    fun findContrastingBallotsMulti(
        problematicContests: List<Triple<String, String?, Int>>,
        pool: CommonPool,
        db: CvrDatabase
    ): List<List<String>> {
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
        val contestInfo: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()
        for ((contestName, winningChoice, _) in problematicContests) {
            val colIndices = db.contestToColumns[contestName] ?: emptyList()
            if (colIndices.isEmpty()) {
                continue
            }
            val colMap = db.contestChoiceMeta[contestName] ?: emptyMap()
            var winningCol: Int? = null
            for ((colIdx, choiceName) in colMap) {
                if (choiceName == winningChoice) {
                    winningCol = colIdx
                    break
                }
            }
            contestInfo[contestName] = mutableMapOf(
                "col_indices" to colIndices,
                "winning_col" to winningCol
            )
        }

        // Score each available ballot by how many problematic contests it votes against.
        val ballotScores: MutableList<Triple<Int, List<String>, List<String>>> = mutableListOf()
        for ((key, rows) in pool.styles()) {
            if (!pool.canDonate(key)) {
                continue
            }
            for (ballot in rows) {
                val satisfied: MutableList<String> = mutableListOf()
                for ((contestName, _, _) in problematicContests) {
                    if (!contestInfo.containsKey(contestName)) {
                        continue
                    }
                    val info = contestInfo[contestName]!!
                    val hasContest = info["col_indices"] as List<Int>
                    .any { ballot[it].trim() != "" }
                    if (!hasContest) {
                        continue
                    }
                    val winningCol = info["winning_col"] as Int?
                    if (winningCol != null && ballot[winningCol].trim() != "1") {
                        for (colIdx in info["col_indices"] as List<Int>) {
                            if (colIdx != winningCol && ballot[colIdx].trim() == "1") {
                                satisfied.add(contestName)
                                break
                            }
                        }
                    }
                }
                if (satisfied.isNotEmpty()) {
                    ballotScores.add(Triple(satisfied.size, satisfied, ballot))
                }
            }
        }

        ballotScores.sortByDescending { it.first }

        // Greedily select ballots until each problematic contest has enough contrast.
        val contestsNeeded: MutableSet<String> = problematicContests.map { it.first }.toMutableSet()
        val selected: MutableList<List<String>> = mutableListOf()
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

    fun _build_aggregate_row(
        ballots: List<List<String>>, db: CvrDatabase, aggregateId: String
    ): List<String> {
        /*
        Build the AGGREGATED CSV row by summing vote columns and blanking identifiers.

        Fixed header columns (TabulatorNum through ImprintedId) are blanked.
        CountingGroup, PrecinctPortion, and named_style are blanked.
        BallotType is set to "AGGREGATED".
        Vote columns are summed across all ballots in the aggregate.
        */
        if (ballots.isEmpty()) {
            return emptyList()
        }

        val result = ballots[0].subList(0, db.headerlen).toMutableList()
        result[0] = aggregateId
        if (result.size > 1) {
            result[1] = "" // TabulatorNum
        }
        if (result.size > 2) {
            result[2] = "" // BatchId
        }
        if (result.size > 3) {
            result[3] = "" // RecordId
        }
        if (result.size > 4) {
            result[4] = "" // ImprintedId
        }
        db.namedStyleCol?.let { result[it] = "" }
        // db.countingGroupIdx?.let { result[it] = "" }
        // db.precinctPortionIdx?.let { result[it] = "" }
        db.ballotTypeIdx?.let { result[it] = "AGGREGATED" }

        // Sum vote columns across all ballots.
        val numCols = ballots[0].size
        for (colIdx in db.headerlen until numCols) {
            var total = 0.0
            for (ballot in ballots) {
                val value = ballot[colIdx].trim()
                if (value.isNotEmpty() && value.replace(".", "").replace("-", "").all { it.isDigit() }) {
                    try {
                        total += value.toDouble()
                    } catch (e: NumberFormatException) {
                        // Ignore invalid values
                    }
                }
            }
            if (total == total.toInt().toDouble()) {
                result.add(total.toInt().toString())
            } else {
                result.add(total.toString())
            }
        }

        return result
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
    class RowIndex {

        // (style_string, precinct) -> list of row indices
        val rowsByPrivacyUnit: MutableMap<Pair<String, String>, MutableList<Int>> = mutableMapOf()

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
        val byPrivacyUnit = mutableMapOf<Pair<String, String>, MutableList<Int>>()
        val byNamedStyle = mutableMapOf<String, MutableList<Int>>()
        val byBallotType = mutableMapOf<String, MutableList<Int>>()

        // val expectedCols = db.contests.size

        var rowIdx = 0
        for (row in db.corlaCvrs.cvrs()) {
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

                val precinct = ""
                byPrivacyUnit.getOrPut(Pair(style, precinct)) { mutableListOf() }.add(rowIdx)

                //db.namedStyleCol?.let {
                //    val namedStyle = row[it].trim()
                //    byNamedStyle.getOrPut(namedStyle) { mutableListOf() }.add(rowIdx)
                //}

                db.ballotTypeIdx.let {
                    val ballotType = row.ballotType
                    if (ballotType.isNotEmpty()) {
                        byBallotType.getOrPut(ballotType) { mutableListOf() }.add(rowIdx)
                    }
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
        // Per C.R.S. 24-72-205.5, the privacy unit is the combination of contest
        // pattern and precinct, not each independently.
        // Key: (style string, PrecinctPortion value).  Value: ballot count.
        val rarePrivacyUnitPairs: MutableMap<Pair<String, String>, Int> = mutableMapOf()

        // Human-readable leakage warnings.  Leakage is reported but not corrected.
        val leakageWarnings: MutableList<String> = mutableListOf()

        fun needsRedaction(): Boolean {
            // Return True if any redaction work is required.
            return rareStyles.isNotEmpty() || rarePrivacyUnitPairs.isNotEmpty()
        }
    }

    class CvrDatabase(val corlaCvrs: CorlaCvrs) {
        val ballotTypeIdx = corlaCvrs.batchIdIdx
        val namedStyleCol = null

        /**
         * After construction the following are available:
         *
         *   contest_names         — ordered list of unique contest names
         *   contest_to_columns    — contest name -> list of column indices
         *   contest_choice_meta   — contest name -> {col_idx: choice_name}
         */
        // Ordered list of unique contest names (fines positions in style strings).
        val contestNames: List<String>

        // contest name -> list of column indices for that contest's choices
        val contestToColumns: Map<String, List<Int>>

        // contest name -> {col_idx: choice_name} (used for vote tallying)
        val contestChoiceMeta: Map<String, Map<Int, String>>

        init {
            contestNames = corlaCvrs.schema.contests.map { it.contestName }

            contestToColumns = corlaCvrs.schema.contests.map { scontest ->
                val colIndices = List<Int>(scontest.ncols) { scontest.startCol + it }
                Pair(scontest.contestName, colIndices)
            }.toMap()

            contestChoiceMeta = corlaCvrs.schema.contests.map { scontest ->
                val choices = corlaCvrs.schema.choices(scontest.contestIdx)
                val choiceNameMap: Map<Int, String> = List(scontest.ncols) {
                    Pair(it, choices.get(scontest.startCol+it))
                }.toMap()
                Pair(scontest.contestName, choiceNameMap)
            }.toMap()
        }
    }

    class Aggregate(
        initialBallots: List<List<String>>,
        private val rareContests: Set<String>,
        private val db: CvrDatabase,
        private val minBallots: Int
    ) {
        private val ballots: MutableList<List<String>> = mutableListOf()
        private val ballotIds: MutableSet<Int> = mutableSetOf()

        // For each contest, how many ballots in the aggregate include that contest.
        private val contestBallotCounts: MutableMap<String, Int> = mutableMapOf()

        // For each contest (the list of contests already exists in the db and will
        // not change), establish a dictionary which maps the contest name to an inner
        // dictionary (which will be filled in by add()) which will map the contest's
        // choices to the number of votes for each choice.
        private val contestChoiceCounts: MutableMap<String, MutableMap<String, Int>> = db.contestToColumns.keys.associateWith { mutableMapOf<String, Int>() }
            .toMutableMap()

        init {
            for (ballot in initialBallots) {
                add(ballot)
            }
        }

        fun add(ballot: List<String>) {
            // Add a ballot to the aggregate, updating all tracked counts.
            ballots.add(ballot)
            ballotIds.add(System.identityHashCode(ballot))

            for ((contestName, colIndices) in db.contestToColumns) {
                val present = colIndices.any { idx -> ballot[idx].trim().isNotEmpty() }
                if (present) {
                    contestBallotCounts[contestName] = contestBallotCounts.getOrDefault(contestName, 0) + 1
                }
            }

            for ((contestName, colMap) in db.contestChoiceMeta) {
                for ((colIdx, choiceName) in colMap) {
                    val value = ballot[colIdx].trim()
                    if (value.isEmpty() || value == "0") continue

                    val increment = value.toDoubleOrNull()?.toInt() ?: 1

                    // get the inner [choiceName:count] dict for the contest.
                    val counts = contestChoiceCounts.getOrPut(contestName) { mutableMapOf() }
                    counts[choiceName] = counts.getOrDefault(choiceName, 0) + increment
                }
            }
        }

        fun containsBallot(ballot: List<String>): Boolean {
            return ballotIds.contains(System.identityHashCode(ballot))
        }

        fun totalCount(): Int {
            return ballots.size
        }

        fun needsMoreTotalBallots(): Boolean {
            // True if the aggregate does not yet have min_ballots total (Rule a).
            return ballots.size < minBallots
        }

        fun contestsNeedingBallots(): Map<String, Int> {
            // Return {contest: ballots_still_needed} for Rule b (each rare contest
            // needs >= min_ballots).
            val result = mutableMapOf<String, Int>()
            for (contest in rareContests) {
                val count = contestBallotCounts.getOrDefault(contest, 0)
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

        fun choiceCounts(): Map<String, Map<String, Int>> {
            // Return per-contest per-choice vote counts (used for unanimity checking).
            return contestChoiceCounts
        }
    }

    class CommonPool(
        styles: Map<Pair<String, String>, List<List<String>>>,
        private val minBallots: Int,
        private val rowCountByPrivacyUnit: Map<Pair<String, String>, Int>,
        blockedStyles: Map<Pair<String, String>, List<List<String>>>? = null
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

        private val styles: MutableMap<Pair<String, String>, MutableList<List<String>>> = styles.mapValues { it.value.toMutableList() }.toMutableMap()
        private val removedCounts: MutableMap<Pair<String, String>, Int> = mutableMapOf()
        private val blockedStylesMap: MutableMap<Pair<String, String>, MutableList<List<String>>> = mutableMapOf()

        init {
            blockedStyles?.forEach { (key, rows) ->
                blockedStylesMap[key] = rows.toMutableList()
            }
        }

        private fun surplus(key: Pair<String, String>): Int {
            /**
             * How many more ballots this pair can donate while keeping
             * full_remaining >= min_ballots.  Positive means donating is allowed.
             */
            val full = rowCountByPrivacyUnit[key] ?: 0
            val removed = removedCounts[key] ?: 0
            return full - removed - minBallots
        }

        fun canDonate(key: Pair<String, String>): Boolean {
            /** True if this pair can still donate at least one ballot without violating Rule d. */
            return surplus(key) > 0
        }

        fun isEmpty(): Boolean {
            return styles.isEmpty()
        }

        fun getStyles(): Map<Pair<String, String>, List<List<String>>> {
            return styles
        }

        fun remove(key: Pair<String, String>, rowIdx: Int) {
            /** Remove one ballot; drop the pair from the pool if it can no longer donate. */
            val rows = styles[key] ?: return
            rows.removeAt(rowIdx)
            removedCounts[key] = (removedCounts[key] ?: 0) + 1
            if (rows.isEmpty() || surplus(key) <= 0) {
                styles.remove(key)
            }
        }

        fun removeRows(rowsToRemove: List<List<String>>) {
            /** Remove all ballots in rows_to_remove from the pool (matched by object identity). */
            val removeIds = rowsToRemove.map { System.identityHashCode(it) }.toSet()
            styles.keys.toList().forEach { key ->
                val rows = styles[key] ?: return@forEach
                val remaining = mutableListOf<List<String>>()
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
        ): Triple<Pair<String, String>, Int, List<String>>? {
            /**
             * Return (key, row_idx, ballot) for the best ballot to borrow, or None.
             *
             * Prioritizes ballots that cover the most contests still needing ballots
             * (Rule b), weighted by how much they reduce vote imbalance.  Falls back
             * to any ballot from the pair with the most surplus when only the total
             * count is short (Rule a).
             */
            val needed = aggregate.contestsNeedingBallots()
            return when {
                needed.isNotEmpty() -> bestForContests(aggregate, db, needed)
                aggregate.needsMoreTotalBallots() -> anyCandidate(aggregate)
                else -> null
            }
        }

        private fun bestForContests(
            aggregate: Aggregate,
            db: CvrDatabase,
            needed: Map<String, Int>
        ): Triple<Pair<String, String>, Int, List<String>>? {
            /**
             * Find the best ballot for bringing the aggregation into agreement with Rule b
             * (has at least min_ballots per contest).
             *
             * The arg "needed" is a dictionary: {contest:number of ballots needed for that contest}
             */
            val neededList = needed.filterValues { it > 0 }.keys.toList()
            var bestCandidate: Triple<Pair<String, String>, Int, List<String>>? = null
            var bestScore = -1.0

            styles.forEach { (key, rows) ->
                if (!canDonate(key)) return@forEach
                rows.forEachIndexed { idx, row ->
                    if (aggregate.containsBallot(row)) return@forEachIndexed
                    val covered = neededList.filter { contest ->
                        _ballot_has_contest(row, contest, db.contestToColumns)
                    }
                    if (covered.isEmpty()) return@forEachIndexed
                    // "gain" indicates an improvement in the score, even though it is
                    // accomplished by a "reduction" in the imbalance.
                    val gain = covered.sumOf {
                        _imbalance_reduction(it, row, aggregate.choiceCounts(), db.contestChoiceMeta)
                    }
                    val score = COVERAGE_WEIGHT * covered.size + gain
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = Triple(key, idx, row)
                    }
                }
            }

            return bestCandidate
        }

        private fun anyCandidate(
            aggregate: Aggregate
        ): Triple<Pair<String, String>, Int, List<String>>? {
            /** Pick any ballot from the pair with the most borrowing surplus. */
            var bestKey: Pair<String, String>? = null
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
                return Triple(key, idx, row)
            }
            return null
        }

        fun pullBlockedStyleFor(
            neededContests: Map<String, Int>,
            db: CvrDatabase
        ): Pair<Pair<String, String>, List<List<String>>>? {
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

            var bestKey: Pair<String, String>? = null
            var bestCoverage = 0

            blockedStylesMap.forEach { (key, rows) ->
                var coverage = 0
                for (contest in neededList) {
                    for (row in rows) {
                        if (_ballot_has_contest(row, contest, db.contestToColumns)) {
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



        fun _ballot_has_contest(
            ballot: List<String>,
            contest: String,
            contestToColumns: Map<String, List<Int>>
        ): Boolean {
            // Return True if the ballot has any non-empty column for the given contest.
            val colIndices = contestToColumns[contest] ?: emptyList()
            return colIndices.any { ballot[it].trim() != "" }
        }

        fun _imbalance_reduction(
            contest: String,
            ballot: List<String>,
            choiceCounts: Map<String, Map<String, Int>>,
            contestChoiceMeta: Map<String, Map<Int, String>>
        ): Double {
            /*
            Estimate how much adding this ballot reduces vote imbalance for a contest.

            Imbalance is max_choice_votes minus the sum of all other votes.
            Returns the improvement (positive = less imbalanced), or 0.0 if the ballot
            does not participate in the contest or makes imbalance worse.
            */

            // "current" is the dict that maps choices to votes for that contest.
            // "total" is the sum of all votes for all choices
            // current_max is the vote total for the choice with the highest number of votes.
            val current = choiceCounts[contest] ?: emptyMap()
            val currentTotal = current.values.sum()
            val currentMax = current.values.maxOrNull() ?: 0
            val currentGap = currentMax - (currentTotal - currentMax)

            // record the votes on this ballot for each of the choices available for this contest
            val contributions = mutableMapOf<String, Int>()
            for ((colIdx, choiceName) in contestChoiceMeta[contest] ?: emptyMap()) {
                val value = ballot[colIdx].trim()
                if (value.isEmpty() || value == "0") {
                    continue
                }
                contributions[choiceName] = 1
            }

            if (contributions.isEmpty()) {
                return 0.0
            }

            val newCounts = current.toMutableMap()
            for ((choiceName, inc) in contributions) {
                newCounts[choiceName] = newCounts.getOrDefault(choiceName, 0) + inc
            }
            val newTotal = currentTotal + contributions.values.sum()
            val newMax = newCounts.values.maxOrNull() ?: 0
            val newGap = newMax - (newTotal - newMax)

            return max(0.0, (currentGap - newGap).toDouble())
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////

    fun checkRedactionNeeds(
        index: RowIndex,
        db: CvrDatabase,
        minBallots: Int,
        redactOnPrecinct: Boolean,
    ): RedactionNeeds {
        /**
         * Examine the row index and return a description of what needs redacting.
         *
         * Args:
         *     index:              The row index built by build_row_index (pass 1).
         *     db:                 The CVR database (header and contest map only).
         *     min_ballots:        Minimum ballots required per style or precinct pair.
         *     redact_on_precinct: If True, also check individual (style, precinct) pairs.
         *
         * Returns:
         *     A RedactionNeeds object describing what must be done.
         */
        val needs = RedactionNeeds()

        // Compute per-style totals by summing across all (style, precinct) keys.
        // When redact_on_precinct is False, all precincts are "" so each style has
        // exactly one key and the sum equals the style's total ballot count.
        val styleTotals = mutableMapOf<String, Int>().withDefault { 0 }
        for ((stylePrecinctPair, rowIndices) in index.rowsByPrivacyUnit) {
            val style = stylePrecinctPair.first
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
        } else if (db.ballotTypeIdx != null) {
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

            for ((key, count) in needs.rarePrivacyUnitPairs.toSortedMap(compareBy({ it.second }))) {
                val (style, precinct) = key
                val ballotTypes = ballotTypesByStyle[style] ?: emptySet()
                val namedStylesForStyle = namedStylesByStyle[style] ?: emptySet()

                val parts = mutableListOf<String>()
                if (showPrecinct) {
                    parts.add("precinct \"$precinct\"")
                }
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

    fun executeCheck() {
        /*
        Run check mode: pass 1 only.  Reports whether the CVR needs redaction.
        All output goes to stdout/stderr (redirectable for GUI use).
        */
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

    fun execute_check(): Boolean {
        /** Print the rare-style report and redaction verdict to stdout. */
        // val showPrecinct = redactOnPrecinct && db.precinctPortionIdx != null

        if (rareStyleMap.isNotEmpty()) {
            println("  Rare ballot styles (${rareStyleMap.size} of $totalStyles total):")
            rareStyleMap.values.forEach { cardStyle ->
                // println("    ${cardStyle.countCards} ballot(s)  [${parts.joinToString(", ")}]")
                println("    ${cardStyle.countCards} ballot(s)  [${cardStyle.name}]")
            }
            println("\nRedaction is needed.")
            return true

            // }
            /* val styleToId: MutableMap<String, Int> = mutableMapOf()
        for ((i, s) in index.styleStrings.withIndex()) {
            styleToId[s] = i
        }

        for ((stylePrecinct, count) in needs.rarePrivacyUnitPairs.toList()
            .sortedBy { it.second }) {
            val (style, precinct) = stylePrecinct
            val ballotTypes = ballotTypesByStyle[style]?.toSortedSet() ?: setOf()
            val namedStylesForStyle = namedStylesByStyle[style]?.toSortedSet() ?: setOf()

            val parts = mutableListOf<String>()
            if (showPrecinct) parts.add("precinct \"$precinct\"")
            if (ballotTypes.isNotEmpty()) {
                parts.add("ballot type: " + ballotTypes.joinToString(", ") { "\"$it\"" })
            } else if (namedStylesForStyle.isNotEmpty()) {
                parts.add(
                    "named style: " +
                            namedStylesForStyle.joinToString(", ") { "\"$it\"" }
                )
            }
            parts.add("style #${styleToId[style]}")
            println("    $count ballot(s)  [${parts.joinToString(", ")}]")
        }
    } */
        } else {
            println("No redaction needed.")
            return false
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////

    fun performRedaction(
        csvPath: String,
        db: CvrDatabase,
        index: RowIndex,
        needs: RedactionNeeds,
        minBallots: Int,
        outputFile: String,
        redactOnPrecinct: Boolean,
        redactedListFile: String?,
        noContestBalancing: Boolean = false
    ) {
        // Orchestrate passes 2 and 3 to produce the anonymized CVR.
        val (redactedRowIndices: Set<Int>, aggregateRow: List<String>?)

        if (needs.needsRedaction()) {
            if (noContestBalancing) {
                val result = collectRareRows(csvPath, index, needs, db, redactOnPrecinct)
                redactedRowIndices = result.first
                aggregateRow = result.second
            } else {
                val result = loadDonorPool(csvPath, index, needs, db, minBallots, redactOnPrecinct)
                redactedRowIndices = result.first
                aggregateRow = result.second
            }

            var rareCount = 0
            for ((key, rowIndices) in index.rowsByPrivacyUnit) {
                if (key in needs.rarePrivacyUnitPairs) {
                    rareCount += rowIndices.size
                }
            }

            println("\n*** Pass 2 complete.")
            println("  Ballots from rare styles/precincts: $rareCount")
            if (!noContestBalancing) {
                val borrowedCount = redactedRowIndices.size - rareCount
                if (borrowedCount > 0) {
                    println("  Ballots borrowed from common styles: $borrowedCount")
                }
            }
            println("  Total ballots in aggregate: ${redactedRowIndices.size}")
        } else {
            redactedRowIndices = emptySet()
            aggregateRow = null
        }

        println("\n*** Pass 3: Writing output.")
        _streamRedactedOutput(
            csvPath,
            db,
            outputFile,
            redactedRowIndices,
            aggregateRow,
            redactOnPrecinct,
            redactedListFile
        )
        println("  Output written to $outputFile.")
        if (redactedListFile != null) {
            println("  Redacted ballot list written to $redactedListFile.")
        }
    }

    fun executeRedact(
        inputFile: String,
        outputFile: String,
        redactedListFile: String?,
        minBallots: Int,
        redactOnPrecinct: Boolean,
        styleCol: Int? = null,
        noContestBalancing: Boolean = false
    ) {
        val db = CvrDatabase(corlaCvrs)

        println("*** Pass 1: Looking for rare ballot styles.")
        println()
        val index = buildRowIndex(db, redactOnPrecinct, checkMode = true)

            val needs = checkRedactionNeeds(index, db, minBallots, redactOnPrecinct)

            for (warning in needs.leakageWarnings) {
                System.err.println("WARNING: $warning")
            }

            reportCheckResults(index, db, needs, redactOnPrecinct)

            performRedaction(
                csvPath,
                db,
                index,
                needs,
                minBallots,
                outputFile,
                redactOnPrecinct,
                redactedListFile,
                noContestBalancing
            )
    }

    // only does rare styles aggregation for now
    fun perform_redaction() {
        if (!execute_check()) {
            stream_redacted_output(emptyMap(), emptyList())
            return
        }

        //         if no_contest_balancing:
        //            result = collect_rare_rows(csv_path, index, needs, db, redact_on_precinct)
        //        else:
        //            result = load_donor_pool(
        //                csv_path, index, needs, db, min_ballots, redact_on_precinct
        //

        val styleMap: Map<Set<Int>, CvrCardStyle> = corlaCvrs.ballotStyles.cardStyleMap
        val rareStyleMap: Map<Set<Int>, CvrCardStyle> = styleMap.filter { it.value.countCards < minBallots }

        println("*** Pass 2: Collecting rare ballots (no contest balancing).")
        println("\n*** Pass 2 complete.")

        println("  Ballots from rare styles/precincts: ${redactedRowIndices.size}")
        println("  Total ballots in aggregate: ${redactedRowIndices.size}")

        val redactedAggregations: Map<CvrCardStyle, Map<Int, ContestTabulation>> =
            rareRowsMap.mapValues { (style, rowlist) -> tabulate(rowlist) }

        println("\n*** Pass 3: Writing output.")
        stream_redacted_output(redactedAggregations, redactedRowIndices)
        println("  Output written to ${outputFile}.")
    }

    fun tabulate(rows: List<CvrRow>): Map<Int, ContestTabulation> {
        val sum = mutableMapOf<Int, ContestTabulation>()
        rows.forEach { row ->
            row.contestVotes.forEach {
                sum.sumContestTabulationsFromVotes(infos[it.contestId]!!, it.candVotes())
            }
        }
        return sum
    }

    fun stream_redacted_output(
        redactedAggregations: Map<CvrCardStyle, Map<Int, ContestTabulation>>,
        redacted_row_indices: List<Int>
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
        // CountingGroup is always removed.  PrecinctPortion is removed when not redacting
        // on precinct, because every row has it blanked in that case.
        /* val colsToRemove = mutableListOf<Int>()
        db.countingGroupIdx?.let { colsToRemove.add(it) }
        if (!redactOnPrecinct && db.precinctPortionIdx != null) {
            colsToRemove.add(db.precinctPortionIdx!!)
        }
        colsToRemove.sort()

        val numVoteCols = db.contests.size - db.headerLen
        val preTally: MutableList<Double> = MutableList(numVoteCols) { 0.0 }
        val postTally: MutableList<Double> = MutableList(numVoteCols) { 0.0 }

        val listOut = redactedListFile?.let { File(it).bufferedWriter(Charsets.UTF_8) } */

        val redactedIdxIter = redacted_row_indices.iterator()
        var nextRedactedIdx: Int? = if (redactedIdxIter.hasNext()) redactedIdxIter.next() else null

        File(outputFile).bufferedWriter(Charsets.UTF_8).use { bwriter ->
            // write headers
            corlaCvrs.headers.forEach {
                bwriter.write(it)
                bwriter.newLine()
            }

            corlaCvrs.cvrs().forEachIndexed { idx, row ->
                if (idx == nextRedactedIdx) {
                    bwriter.write(writeRow(row, true))
                    nextRedactedIdx = if (redactedIdxIter.hasNext()) redactedIdxIter.next() else null
                } else {
                    // write unredacted
                    bwriter.write(writeRow(row, false))
                }
            }

            redactedAggregations.forEach { (style, agg) ->
                val aggrow = buildString {
                    append("AGGREGATED")
                    repeat(schema.nheaders - 1) { append(",") }
                    append("AGGREGATED,")
                    schema.contests.forEach { scontest ->
                        val tab = agg[scontest.contestIdx]
                        repeat(scontest.ncols) {
                            if (tab == null) append(",") else {
                                val vote = tab.votes[it] ?: 0
                                append("$vote,")
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
        append(row.csvHeader())
        if (redacted) {
            repeat(schema.nchoices) { append("*,") }
        } else {
            val rowMap: Map<Int, List<Int>> = row.contestVotes.map { Pair(it.contestId, it.votedFor) }.toMap()
            schema.contests.forEach { scontest ->
                val candVotes = rowMap[scontest.contestIdx]
                repeat(scontest.ncols) {
                    if (candVotes == null) append(",") else {
                        val vote = if (candVotes.contains(it)) 1 else 0
                        append("$vote,")
                    }
                }
            }
        }
        // TODO remove last ??
        appendLine()
    }
}

// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

/* (my notes)
Clikt: The undisputed industry standard for Kotlin CLI development.
    It features a highly extensible design, supports subcommands natively, offers great type safety via property delegates, and fully supports Kotlin Multiplatform (KMP).
Mordant: Built by the same author as Clikt, this library is specifically designed for styling text, handling ANSI escape codes, colors,
    and formatting terminal output nicely in KMP projects.
Kotter: An excellent choice if you want to build a fully interactive, dynamic terminal user interface (TUI) with support for rendering complex live UI components.
 */
enum class Mode2 { check, redact }

object AnonymizeCvrs2 {

    @JvmStatic
    fun main(args: Array<String>) {

        val parser = ArgParser("AnonymizeCvr")
        val mode by parser.option(
            ArgType.Choice<Mode> { it.name.lowercase() },
            shortName = "check",
            description = "Check mode: report whether redaction is needed without writing output."
        ).default(Mode.check)

        val input by parser.option(
            ArgType.String,
            shortName = "input",
            description = "Path to the input CVR file (CSV format)."
        ).required()

        val output by parser.option(
            ArgType.String,
            shortName = "output",
            description = "Path for the redacted output CVR file. Required in redact mode; not needed in --check mode."
        )

        val min_ballots by parser.option(
            ArgType.Int,
            shortName = "min-ballots",
            description = "Minimum ballots required per style or precinct."
        ).default(MIN_BALLOTS_DEFAULT)

        val redact_on_precinct by parser.option(
            ArgType.Boolean,
            shortName = "redact-on-precinct",
            description = """Treat precincts with fewer than --min-ballots ballots as rare 
            and aggregate them, instead of simply blanking the PrecinctPortion column.
        """.trimIndent()
        ).default(false)

        val no_contest_balancing by parser.option(
            ArgType.Boolean,
            shortName = "no-contest-balancing",
            description = """ Do not do contest balancing. Redact only the rare-style ballots 
            without borrowing from common styles to satisfy minimums or balance near-unanimous contests.
        """.trimIndent()
        ).default(false)

        val stylecol by parser.option(
            ArgType.Int,
            shortName = "stylecol",
            description = "Column index (0-based) of the named_style field, if present."
        )

        val redacted_list_filename by parser.option(
            ArgType.String,
            shortName = "redacted-list",
            description = """Write the ImprintedId of every redacted ballot to FILENAME,
            one per line.  Useful for identifying ballot images that also need redaction.
            """.trimIndent()
        )

        val version by parser.option(
            ArgType.Boolean,
            shortName = "version",
            description = "Show version."
        )

        val helps by parser.option(
            ArgType.Boolean,
            shortName = "description",
            description = """ Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. 
            Ballot styles with fewer than --min-ballots ballots are aggregated to protect voter privacy.
        """.trimIndent()
        )

        try {
            parser.parse(args)

            if (helps != null) {
                println(
                    """Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. 
                    Ballot styles with fewer than --min-ballots ballots are aggregated to protect voter privacy.
                """
                )
                return
            }

            if (version != null) {
                println("version = $VERSION")
                return
            }

            println("AnonymizeCvr ${mode} for $input")
            if (mode == Mode.redact) println(" output to $output")
            print(" min_ballots=$min_ballots,")
            print(" no_contest_balancing=$no_contest_balancing,")
            print(" redact_on_precinct=$redact_on_precinct,")
            if (stylecol != null) print(" stylecol=$stylecol,")
            if (redacted_list_filename != null) print(" redacted_list_filename=$redacted_list_filename,")
            print(" version=$VERSION")
            println()
            println()

            if (mode == Mode.redact && output == null) {
                println(" you must set an output file when redacting")
                return
            }

            execute(mode, input, min_ballots, output!!, redacted_list_filename, redact_on_precinct, stylecol, no_contest_balancing)

        } catch (t: Throwable) {
            println(t.message)
        }
    }

    fun execute(
        mode: Mode, input: String, min_ballots: Int, output: String?, redacted_list_filename: String?,
        redact_on_precinct: Boolean, stylecol: Int?, no_contest_balancing: Boolean
    ) {
        val anon = Anonymize(input, min_ballots, output!!, redact_on_precinct, stylecol, no_contest_balancing, redacted_list_filename)

        when (mode) {
            Mode.check -> anon.execute_check()
            Mode.redact -> anon.perform_redaction()
        }
    }
}


