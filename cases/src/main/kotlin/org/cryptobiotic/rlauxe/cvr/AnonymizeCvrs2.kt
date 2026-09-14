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
   */
    fun load_donor_pool(
        csvPath: String,
        index: RowIndex,
        needs: RedactionNeeds,
        db: CvrDatabase,
        minBallots: Int,
        redactOnPrecinct: Boolean
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

        // Rare-ballot contests: any contest present on at least one rare ballot.
        val styleMap: Map<Set<Int>, CvrCardStyle> = corlaCvrs.ballotStyles.cardStyleMap
        val rareStyleMap: Map<Set<Int>, CvrCardStyle> = styleMap.filter { it.value.countCards < minBallots }
        val rareBallotContests = rareStyleMap.map{ it.key }.flatten().toSet()

        /*val rareBallotContests = mutableSetOf<String>()
        for ((style, _) in rarePrivacyUnitSet) {
            for ((i, contestName) in db.contestNames.withIndex()) {
                if (style[i] == '1') {
                    rareBallotContests.add(contestName)
                }
            }
        } */

        // Rare contests: rare-ballot contests with fewer than perContestPoolTarget total appearances in the full CVR.
        val rareContests = rareBallotContests.filterTo(mutableSetOf()) {
            contestTotalCounts.getValue(it) < perContestPoolTarget
        }

        // Row counts per privacy unit, passed to CommonPool for Rule d enforcement.
        val rowCountByPrivacyUnit = index.rowsByPrivacyUnit.mapValues { it.value.size }.toMutableMap()

        // Tracks how many donor rows have been loaded per rare-ballot contest.
        val donorCountByRareBallotContest = mutableMapOf<String, Int>().withDefault { 0 }

        // Tracks vote distribution of loaded donors per rare-ballot contest.
        // contest_name -> {choice_name: count of donors voting "1" for that choice}
        // Used to ensure we load enough contrasting donors to fix near-unanimity.
        val donorVotedTally = mutableMapOf<String, MutableMap<String, Int>>() // contest -> choice -> votes

        val donorByPrivacyUnit =
            mutableMapOf<Pair<String, String>, MutableList<CvrRow>>().withDefault { mutableListOf() }
        val blockedByPrivacyUnit =
            mutableMapOf<Pair<String, String>, MutableList<CvrRow>>().withDefault { mutableListOf() }

        // Maps id(row) to rowIdx for all loaded rows, used to identify aggregated rows.
        val rowToIdx = mutableMapOf<Int, Int>()

        println("*** Pass 2: Building the aggregate row.")
        println()

        // csv.py - read/write/investigate CSV files
        // Use CsvrReader

        corlaCvrs.cvrs.forEach { row ->
        File(csvPath).bufferedReader(Charsets.UTF_8).use { breader ->
            val rowStyle = index.styleForRow(rowIdx) ?: continue
            val precinct = if (redactOnPrecinct && db.precinctPortionIdx != null) {
                row[db.precinctPortionIdx!!].trim()
            } else {
                ""
            }
            val privacyUnit = rowStyle to precinct

            if (privacyUnit in rarePrivacyUnitSet) {
                rareRows.add(row)
                rowToIdx[row.hashCode()] = rowIdx
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
                val rowRareContests = rareBallotContests.filter {
                    ballotHasContest(row, it, db.contestToColumns)
                }

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
        }
        }

        val pool = CommonPool(
            donorByPrivacyUnit.mapValues { it.value.toList() },
            minBallots,
            rowCountByPrivacyUnit,
            blockedByPrivacyUnit.mapValues { it.value.toList() }
        )

        val aggregate = buildAggregate(rareRows, rareBallotContests, pool, db, minBallots)
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
    }

    fun execute_check(): Boolean {
        /** Print the rare-style report and redaction verdict to stdout. */
        // val showPrecinct = redactOnPrecinct && db.precinctPortionIdx != null

        if (rareStyleMap.isNotEmpty()) {
            /* if (showPrecinct) {
            val totalPairs = index.rowsByPrivacyUnit.size
            println(
                "  Rare ballot style/precinct combinations " +
                        "(${needs.rarePrivacyUnitPairs.size} of $totalPairs total):"
            )
        } else { */

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


// CVRDatabase -> CorlaCvrs
//       contest_names         — ordered list of unique contest names
//      contest_to_columns    — contest name -> list of column indices
//      contest_choice_meta   — contest name -> {col_idx: choice_name}

// RowIndex -> CorlaCvrs
//     Lightweight index built by pass 1 (build_row_index).
//
//    Stores row indices (integers) rather than ballot data, so memory cost
//    is proportional to row count, not ballot width.
//
//    rows_by_style_precinct uses "" as the precinct value for every row when
//    --redact-on-precinct is False, so a single dict handles both cases.
//
//    rows_by_ballot_type is always populated when the BallotType column exists.
//    rows_by_named_style is populated only when --stylecol is given.
//    Only one of them is used for leakage detection (named_style takes priority).

// class RedactionNeeds:
//    """
//    Describes what the CVR requires before it can be safely published.
//
//    Populated by check_redaction_needs().  The redaction logic reads this to
//    decide what work to do.
//
//    Rare styles and rare (style, precinct) pairs both require the same kind of
//    treatment: ballots must be aggregated so no individual voter can be identified.
//    """
//
//    def __init__(self) -> None:
//        # Styles with too few ballots.
//        # Key: style string.  Value: ballot count.
//        self.rare_styles: Dict[str, int] = {}
//
//        # Privacy units (style, precinct) with too few ballots.
//        # When --redact-on-precinct is False, precinct is always "" so each key
//        # is (style, "") and the unit is equivalent to the style alone.
//        # Per C.R.S. 24-72-205.5, the privacy unit is the combination of contest
//        # pattern and precinct, not each independently.
//        # Key: (style string, PrecinctPortion value).  Value: ballot count.
//        self.rare_privacy_unit_pairs: Dict[Tuple[str, str], int] = {}
//
//        # Human-readable leakage warnings.  Leakage is reported but not corrected.
//        self.leakage_warnings: List[str] = []
//
//    def needs_redaction(self) -> bool:
//        """Return True if any redaction work is required."""
//        return len(self.rare_styles) > 0 or len(self.rare_privacy_unit_pairs) > 0

// class Aggregate:
//    """
//    Accumulates ballots into the anonymized aggregate pool, tracking
//    counts needed to verify the redaction rules.
//
//    All four anonymization rules pertain to building the aggregate:
//
//    Rule a: The aggregate must contain at least min_ballots ballots in total.
//
//    Rule b: For each rare contest (one that appears on at least one rare
//            ballot), the aggregate must contain at least min_ballots ballots
//            that include that contest.
//
//    Rule c: No contest in the aggregate may be near-unanimous.  "Near-
//            unanimous" means all but NEAR_UNANIMOUS_THRESHOLD (default
//            value = 2) votes go to a single choice.  If a contest is
//            near-unanimous, contrasting ballots are borrowed from the
//            common pool until at least MIN_CONTRASTING_VOTES (default
//            value = 3) ballots vote for a non-leading choice. Only contests
//            on rare ballots are checked; near-unanimity in contests that
//            belong only to common styles is not a concern.
//
//    Rule d: A ballot may only be borrowed from a common style if that style
//            will still have at least min_ballots ballots remaining after the
//            borrow.  Enforced by CommonPool, which removes a style from the
//            pool entirely once it would drop below the minimum.
//    """
//        self.rare_contests = rare_contests
//        self.ballots: List[List[str]] = []
//        self._ballot_ids: Set[int] = set()
//
//        # For each contest, how many ballots in the aggregate include that contest.
//        self._contest_ballot_counts: Dict[str, int] = defaultdict(int)
//
//        # For each contest (the list of contests already exists in the db and will
//        # not change), establish a dictionary which maps the contest name to an inner
//        # dictionary (which will be filled in by add()) which will map the contest's
//        # choices to the number of votes for each choice.
//        self._contest_choice_counts: Dict[str, Dict[str, int]] = {
//            c: {} for c in db.contest_to_columns
//        }

// class CommonPool:
//    """
//    Pool of common-style ballots available for borrowing into the aggregate.
//
//    Enforces Rule d: borrowing a ballot from a pair never leaves that pair with
//    fewer than min_ballots ballots remaining in the full dataset.  Surplus is
//    tracked against the full dataset count from pass 1 (via full_counts), not
//    against the number of donor rows loaded into memory, so that loading only a
//    subset of a pair's ballots does not artificially restrict borrowing.
//
//    Keys are (style_string, precinct) tuples.  When --redact-on-precinct is
//    False, precinct is always "" so each key is (style_string, "").
//    """


