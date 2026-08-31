package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.boulder.parseNCards
import java.io.File
import kotlin.math.abs
import kotlin.math.max


// port from github/nealmcb/anonymize_cvr/anonymize_cvr.py

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

private val logger = KotlinLogging.logger("AnonymizeCvr")

const val VERSION = "0.2"

const val MIN_BALLOTS_DEFAULT = 10
const val NEAR_UNANIMOUS_THRESHOLD = 2  // "all but N votes" triggers balancing (Rule c)
const val MIN_CONTRASTING_VOTES = 3  // contrasting votes needed per contest after balancing
const val COVERAGE_WEIGHT = 10.0  // weight for contest coverage vs. vote-balance score
const val DONOR_SURPLUS_THRESHOLD = 3  // minimum surplus above min_ballots for a style/precinct to donate freely

class CvrDatabase(
    val inputFile: String,
    val namedStyleCol: Int? = null
) {
    val corlaCvrs = readCorlaCvrsFromFile(inputFile, showHeaders = true)

    /**
     * Reads a CVR file header and builds contest/choice mappings.
     *
     * The Colorado CVR CSV format has four header rows, then one ballot per row:
     *   Row 1: version / election name
     *   Row 2: contest names (one name per choice column, repeated)
     *   Row 3: choice (candidate) names, one per column
     *   Row 4: column headers (CvrNumber, TabulatorNum, BatchId, RecordId,
     *           ImprintedId, CountingGroup, PrecinctPortion, BallotType, ...)
     *   Rows 5+: one ballot per row (not loaded here — see buildRowIndex)
     *
     * After construction the following are available:
     *
     *   contest_names         — ordered list of unique contest names
     *   contest_to_columns    — contest name -> list of column indices
     *   contest_choice_meta   — contest name -> {col_idx: choice_name}
     */

    /* Four header rows read from the file.
    val version = mutableListOf<String>()
    val contests = mutableListOf<String>()
    val choices = mutableListOf<String>()
    val headers = mutableListOf<String>() */

    // Column indices for named special columns.  None means not present.
    val ballotTypeIdx: Int? = corlaCvrs.schema.headerMap["ballottype"]
    val precinctPortionIdx: Int? = corlaCvrs.schema.headerMap["precinctportion"]
    var countingGroupIdx: Int? = corlaCvrs.schema.headerMap["countinggroup"]

    // list of unique contest names
    val contestNames = corlaCvrs.schema.contests.map { { it.contestName } }

    // contest name -> list of column indices for that contest's choices
    val contestToColumns = mutableMapOf<String, MutableList<Int>>()

    // contest name -> col_idx -> choice_name (used for vote tallying)
    val contestChoiceMeta = mutableMapOf<String, MutableMap<Int, String>>()
    // val contestChoices:  Map<String, List<String>> // contest -> List choices

    init {
        //// Check that the file structure is usable.
        //if (corlaCvrs.schema.nheaders >= corlaCvrs.schema.contests.size) {
        //    throw IllegalArgumentException("No contests / vote columns found")
        //}
        // TODO does this replace "ballotstyle" ?? NOT IMPLEMENTED YET
        if (namedStyleCol != null && namedStyleCol >= corlaCvrs.schema.nheaders) {
            throw IllegalArgumentException(
                "Named style column $namedStyleCol must be within " +
                        "the header columns (headerlen=$corlaCvrs.schema.nheaders)."
            )
        }

        /* Build choice metadata: for each contest, map column index to choice name.
        for ((contestName, colIndices) in contestToColumns) {
            val colMap: MutableMap<Int, String> = HashMap()
            for (colIdx in colIndices) {
                val choiceName = if (colIdx < choices.size) choices[colIdx].trim() else ""
                colMap[colIdx] = if (choiceName.isNotEmpty()) choiceName else "Choice$colIdx"
            }
            contestChoiceMeta[contestName] = colMap
        }

        contestChoices = contestChoiceMeta.mapValues { (key, value) -> value.map { it.value } } */
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /* fun _readFile() {

        File(inputFile).bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val lines = reader.lineSequence().iterator()

            if (lines.hasNext()) version.addAll(lines.next().split(","))
            if (lines.hasNext()) contests.addAll(lines.next().split(","))
            if (lines.hasNext()) choices.addAll(lines.next().split(","))
            if (lines.hasNext()) headers.addAll(lines.next().split(","))
        }

        if (headerLen == 0) {
            for (cell in contests) {
                if (cell.trim().isEmpty()) {
                    headerLen++
                } else {
                    break
                }
            }
            if (headerLen == 0) {
                throw IllegalArgumentException(
                    "Could not auto-detect headerlen: no empty cells at the start of the contests row."
                )
            }
        }

        headers.forEachIndexed { idx, name ->
            when (name.trim().lowercase()) {
                "ballottype" -> ballotTypeIdx = idx
                "precinctportion" -> precinctPortionIdx = idx
                "countinggroup" -> countingGroupIdx = idx
            }
        }
    }

    fun _validate() {
        /** Check that the file structure is usable. */
        if (headerLen >= contests.size) {
            throw IllegalArgumentException(
                "No vote columns found: headerlen=${headerLen} but " +
                        "contests row has only ${contests.size} columns."
            )
        }
        if (namedStyleCol != null && namedStyleCol >= headerLen) {
            throw IllegalArgumentException(
                "Named style column $namedStyleCol must be within " +
                        "the header columns (headerlen=$headerLen)."
            )
        }
    }

    fun _buildContestMap() {
        /** Build the contest name -> column indices mapping and ordered contest list. */
        val mapping: MutableMap<String, MutableList<Int>> = HashMap()

        for (colIdx in headerLen until contests.size) {
            val name = contests[colIdx].trim()
            if (name.isEmpty()) {
                throw IllegalArgumentException(
                    "Contest row column $colIdx is empty; expected a contest name."
                )
            }
            if (name !in mapping) {
                contestNames.add(name)
            }
            mapping.computeIfAbsent(name) { ArrayList() }.add(colIdx)
        }
        contestToColumns.putAll(mapping)

        // Build choice metadata: for each contest, map column index to choice name.
        for ((contestName, colIndices) in contestToColumns) {
            val colMap: MutableMap<Int, String> = HashMap()
            for (colIdx in colIndices) {
                val choiceName = if (colIdx < choices.size) choices[colIdx].trim() else ""
                colMap[colIdx] = if (choiceName.isNotEmpty()) choiceName else "Choice$colIdx"
            }
            contestChoiceMeta[contestName] = colMap
        }
    } */
}

/**
 * Lightweight index built by pass 1 (buildRowIndex).
 *
 * Stores row indices (integers) rather than ballot data, so memory cost
 * is proportional to row count, not ballot width.
 *
 * rows_by_style_precinct uses "" as the precinct value for every row when
 * --redact-on-precinct is False, so a single dict handles both cases.
 *
 * rows_by_ballot_type is always populated when the BallotType column exists.
 * rows_by_named_style is populated only when --stylecol is given.
 *
 * Only one of them is used for leakage detection (named_style takes priority).
 */
class RowIndex(val corlaCvrs: CorlaCvrs,
    // (style_string, precinct) -> list of row indices
    val rowsByPrivacyUnit: Map<Pair<String, String>, List<Int>>,

    // table of unique style strings; index into this list is the style ID
    val styleStrings: List<String>,

    // style ID (index into style_strings) per row index; None for skip rows
    val styleIdForRow: List<Int?>,

    // named_style value -> list of row indices (only when --stylecol is given)
    val rowsByNamedStyle: Map<String, List<Int>>,

    // ballot_type value -> list of row indices (only when BallotType column exists)
    val rowsByBallotType: Map<String, List<Int>>,

    // total non-empty rows counted (includes skip rows)
    val totalRows: Int
) {
    val wtf = corlaCvrs.ballotStyles.cardStyleMap // already has cardStyle counts

    // Return the style string for a row, or None if the row was skipped.
    fun styleForRow(rowIdx: Int): String? {
        val styleId = styleIdForRow[rowIdx]
        return if (styleId == null) null else styleStrings[styleId]
    }
}


fun buildRowIndex(db: CvrDatabase, redactOnPrecinct: Boolean, checkMode: Boolean = false): RowIndex {

    // table of unique style strings; index into this list is the style ID
    val styleStrings: MutableList<String> = mutableListOf()

    // style ID (index into style_strings) per row index; None for skip rows
    val styleIdForRow: MutableList<Int> = mutableListOf()

    /*
    Pass 1: read every ballot row and build the lightweight row index.

    Stores row indices, not ballot data.  Skip rows (redacted ballots and
    aggregate rows from a prior run) are counted in total_rows but excluded
    from all grouping dicts.

    Row indices are 0-based from the first ballot row (row 5 in the file).
    Every non-empty row increments the index, including skip rows, so that
    indices are consistent across all three passes.
    */
    //val index = RowIndex()
    val styleTable = mutableMapOf<String, Int>() // style_string -> style ID (index into style_strings)
    val byPrivacyUnit = mutableMapOf<Pair<String, String>, MutableList<Int>>()
    val byNamedStyle = mutableMapOf<String, MutableList<Int>>()
    val byBallotType = mutableMapOf<String, MutableList<Int>>()

    val expectedCols = db.corlaCvrs.schema.contests.size

    db.corlaCvrs.cvrs.forEachIndexed { rowIdx, row ->
        val style = styleForRow(row, db)
        //if (styleStr !in styleTable) {
         //   val styleId = styleStrings.size
         //   styleTable[styleStr] = styleId
           // styleStrings.add(styleStr)
        //}
        if (style != null) {
            // val styleId = styleTable[style.name]!!
            // styleIdForRow.add(style.id)
            // val style = styleStrings[styleId]

            val precinct = if (redactOnPrecinct && db.precinctPortionIdx != null) {
                row.precinctPortion!!
            } else {
                ""
            }
            byPrivacyUnit.getOrPut(Pair(style.name, precinct)) { mutableListOf() }.add(rowIdx)

            /* TODO
        if (db.namedStyleCol != null) {
            val namedStyle = row[db.namedStyleCol].trim()
            byNamedStyle.getOrPut(namedStyle) { mutableListOf() }.add(rowIdx)
        } */

            if (db.ballotTypeIdx != null) {
                val ballotType = row.ballotType
                if (ballotType.isNotEmpty()) {
                    byBallotType.getOrPut(ballotType) { mutableListOf() }.add(rowIdx)
                }
            }
        }
    }
    return RowIndex(db.corlaCvrs, byPrivacyUnit, styleStrings, styleIdForRow, byNamedStyle, byBallotType, styleIdForRow.size)
}

fun styleForRow(row: CvrRow, db: CvrDatabase): CvrCardStyle? {
    val cvrContestSet = row.contestVotes.map { it.contestId }.toSet()
    return db.corlaCvrs.ballotStyles.cardStyleMap[cvrContestSet]
}

/**
 * Describes what the CVR requires before it can be safely published.
 *
 * Populated by checkRedactionNeeds(). The redaction logic reads this to
 * decide what work to do.
 *
 * Rare styles and rare (style, precinct) pairs both require the same kind of
 * treatment: ballots must be aggregated so no individual voter can be identified.
 */
class RedactionNeeds {
    // Styles with too few ballots.
    // Key: style string. Value: ballot count.
    val rareStyles: MutableMap<String, Int> = mutableMapOf()

    // Privacy units (style, precinct) with too few ballots.
    // When --redact-on-precinct is False, precinct is always "" so each key
    // is (style, "") and the unit is equivalent to the style alone.
    // Per C.R.S. 24-72-205.5, the privacy unit is the combination of contest
    // pattern and precinct, not each independently.
    // Key: (style string, PrecinctPortion value). Value: ballot count.
    val rarePrivacyUnitPairs: MutableMap<Pair<String, String>, Int> = mutableMapOf()

    // Human-readable leakage warnings. Leakage is reported but not corrected.
    val leakageWarnings: MutableList<String> = mutableListOf()

    fun needsRedaction(): Boolean {
        /** Return True if any redaction work is required. */
        return rareStyles.isNotEmpty() || rarePrivacyUnitPairs.isNotEmpty()
    }
}

// ---------------------------------------------------------------------------
// checkRedactionNeeds
// ---------------------------------------------------------------------------

/**
 * Examine the row index and return a description of what needs redacting.
 *
 * Args:
 *     index:              The row index built by buildRowIndex (pass 1).
 *     db:                 The CVR database (header and contest map only).
 *     minBallots:         Minimum ballots required per style or precinct pair.
 *     redactOnPrecinct:   If True, also check individual (style, precinct) pairs.
 *
 * Returns:
 *     A RedactionNeeds object describing what must be done.
 */
fun checkRedactionNeeds(
    index: RowIndex,
    db: CvrDatabase,
    minBallots: Int,
    redactOnPrecinct: Boolean
): RedactionNeeds {
    val needs = RedactionNeeds()

    // Compute per-style totals by summing across all (style, precinct) keys.
    // When redactOnPrecinct is False, all precincts are "" so each style has
    /* exactly one key and the sum equals the style's total ballot count.
    val styleTotals: MutableMap<String, Int> = mutableMapOf()
    for ((privacyUnit, rowIndices) in index.rowsByPrivacyUnit) {
        val (style, _) = privacyUnit
        styleTotals[style] = styleTotals.getOrDefault(style, 0) + rowIndices.size
    }
    for ((style, total) in styleTotals) {
        if (total < minBallots) {
            needs.rareStyles[style] = total
        }
    } */

    db.corlaCvrs.ballotStyles.cardStyleMap.values.filter { it.countCards  < minBallots }.forEach {
        needs.rareStyles[it.name] = it.countCards
    }


    // Always populate rarePrivacyUnitPairs.
    // When redactOnPrecinct is False, all precincts are "" so each key is
    // (style, "") and the count equals the style's total ballot count.
    for ((privacyUnit, rowIndices) in index.rowsByPrivacyUnit) {
        val (style, precinct) = privacyUnit
        if (rowIndices.size < minBallots) {
            needs.rarePrivacyUnitPairs[Pair(style, precinct)] = rowIndices.size
        }
    }

    // Leakage detection (Rule 10): use namedStyle if --stylecol was given,
    // otherwise use ballotType. Only one check is run.
    if (db.namedStyleCol != null) {
        val namedStylesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
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
        val ballotTypesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
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

/**
 * Accumulates ballots into the anonymized aggregate pool, tracking
 * counts needed to verify the redaction rules.
 *
 * All four anonymization rules pertain to building the aggregate:
 *
 * Rule a: The aggregate must contain at least min_ballots ballots in total.
 *
 * Rule b: For each rare contest (one that appears on at least one rare
 *         ballot), the aggregate must contain at least min_ballots ballots
 *         that include that contest.
 *
 * Rule c: No contest in the aggregate may be near-unanimous.  "Near-
 *         unanimous" means all but NEAR_UNANIMOUS_THRESHOLD (default
 *         value = 2) votes go to a single choice.  If a contest is
 *         near-unanimous, contrasting ballots are borrowed from the
 *         common pool until at least MIN_CONTRASTING_VOTES (default
 *         value = 3) ballots vote for a non-leading choice. Only contests
 *         on rare ballots are checked; near-unanimity in contests that
 *         belong only to common styles is not a concern.
 *
 * Rule d: A ballot may only be borrowed from a common style if that style
 *         will still have at least min_ballots ballots remaining after the
 *         borrow.  Enforced by CommonPool, which removes a style from the
 *         pool entirely once it would drop below the minimum.
 */
class Aggregate(
    initialBallots: List<CSVRecord>,
    val rareContests: Set<String>,
    val db: CvrDatabase,
    val minBallots: Int
) {

    val ballots: MutableList<CSVRecord> = mutableListOf()
    val ballotIds: MutableSet<Int> = mutableSetOf()

    // For each contest, how many ballots in the aggregate include that contest.
    val contestBallotCounts: MutableMap<String, Int> = mutableMapOf()

    // For each contest (the list of contests already exists in the db and will
    // not change), establish a dictionary which maps the contest name to an inner
    // dictionary (which will be filled in by add()) which will map the contest's
    // choices to the number of votes for each choice.
    val contestChoiceCounts: MutableMap<String, MutableMap<String, Int>> =
        db.contestToColumns.keys.associateWith { mutableMapOf<String, Int>() }.toMutableMap()

    init {
        for (ballot in initialBallots) {
            add(ballot)
        }
    }

    fun add(ballot: CSVRecord) {
        // Add a ballot to the aggregate, updating all tracked counts.
        ballots.add(ballot)
        ballotIds.add(System.identityHashCode(ballot))

        for ((contestName, colIndices) in db.contestToColumns) {
            val present = colIndices.any { ballot[it].trim().isNotEmpty() }
            if (present) {
                contestBallotCounts[contestName] = (contestBallotCounts[contestName] ?: 0) + 1
            }
        }

        for ((contestName, colMap) in db.contestChoiceMeta) {
            for ((colIdx, choiceName) in colMap) {
                val value = ballot[colIdx].trim()
                if (value.isEmpty() || value == "0") continue

                val increment = try {
                    value.toDouble().toInt()
                } catch (e: NumberFormatException) {
                    1
                }

                // get the inner [choiceName:count] map for the contest.
                val counts = contestChoiceCounts[contestName]!!
                counts[choiceName] = counts.getOrDefault(choiceName, 0) + increment
            }
        }
    }

    fun containsBallot(ballot: CSVRecord): Boolean {
        return System.identityHashCode(ballot) in ballotIds
    }

    fun totalCount(): Int {
        return ballots.size
    }

    fun needsMoreTotalBallots(): Boolean {
        // True if the aggregate does not yet have min_ballots total (Rule a).
        return ballots.size < minBallots
    }

    fun contestsNeedingBallots(): Map<String, Int> {
        /**
         * Return {contest: ballots_still_needed} for Rule b (each rare contest
         * needs >= min_ballots).
         */
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
    styles: Map<Pair<String, String>, List<CSVRecord>>,
    val minBallots: Int,
    val rowcountByPrivacyUnit: Map<Pair<String, String>, Int>,
    blockedStyles: Map<Pair<String, String>, List<CSVRecord>>? = null
) {
    val _styles: MutableMap<Pair<String, String>, MutableList<CSVRecord>> =
        styles.mapValues { it.value.toMutableList() }.toMutableMap()
    val _removedCounts: MutableMap<Pair<String, String>, Int> = mutableMapOf()
    val _blockedStyles: MutableMap<Pair<String, String>, MutableList<CSVRecord>> = mutableMapOf()

    init {
        blockedStyles?.forEach { (key, rows) ->
            _blockedStyles[key] = rows.toMutableList()
        }
    }

    fun _surplus(key: Pair<String, String>): Int {
        /**
         * How many more ballots this pair can donate while keeping
         * full_remaining >= min_ballots.  Positive means donating is allowed.
         */
        val full = rowcountByPrivacyUnit[key] ?: 0
        val removed = _removedCounts[key] ?: 0
        return full - removed - minBallots
    }

    fun canDonate(key: Pair<String, String>): Boolean {
        /** True if this pair can still donate at least one ballot without violating Rule d. */
        return _surplus(key) > 0
    }

    fun isEmpty(): Boolean {
        return _styles.isEmpty()
    }

    fun styles(): Map<Pair<String, String>, List<CSVRecord>> {
        return _styles.toMap()
    }

    fun remove(key: Pair<String, String>, rowIdx: Int) {
        /** Remove one ballot; drop the pair from the pool if it can no longer donate. */
        val rows = _styles[key] ?: return
        rows.removeAt(rowIdx)
        _removedCounts[key] = (_removedCounts[key] ?: 0) + 1
        if (rows.isEmpty() || _surplus(key) <= 0) {
            _styles.remove(key)
        }
    }

    fun removeRows(rowsToRemove: List<CSVRecord>) {
        /** Remove all ballots in rows_to_remove from the pool (matched by object identity). */
        val removeIds = rowsToRemove.map { System.identityHashCode(it) }.toSet()
        val keysToRemove = mutableListOf<Pair<String, String>>()

        _styles.forEach { (key, rows) ->
            val remaining = rows.filter { System.identityHashCode(it) !in removeIds }
            val removedHere = rows.size - remaining.size
            if (removedHere > 0) {
                _removedCounts[key] = (_removedCounts[key] ?: 0) + removedHere
            }
            if (remaining.isEmpty() || _surplus(key) <= 0) {
                keysToRemove.add(key)
            } else {
                _styles[key] = remaining.toMutableList()
            }
        }

        keysToRemove.forEach { _styles.remove(it) }
    }

    fun bestCandidateFor(
        aggregate: Aggregate,
        db: CvrDatabase
    ): Triple<Pair<String, String>, Int, CSVRecord>? {
        /**
         * Return (key, row_idx, ballot) for the best ballot to borrow, or None.
         *
         * Prioritizes ballots that cover the most contests still needing ballots
         * (Rule b), weighted by how much they reduce vote imbalance.  Falls back
         * to any ballot from the pair with the most surplus when only the total
         * count is short (Rule a).
         */
        val needed = aggregate.contestsNeedingBallots()
        return if (needed.isNotEmpty()) {
            bestForContests(aggregate, db, needed)
        } else if (aggregate.needsMoreTotalBallots()) {
            _anyCandidate(aggregate)
        } else null
    }

    fun bestForContests(
        aggregate: Aggregate,
        db: CvrDatabase,
        needed: Map<String, Int>
    ): Triple<Pair<String, String>, Int, CSVRecord>? {
        /**
         * Find the best ballot for bringing the aggregation into agreement with Rule b
         * (has at least min_ballots per contest).
         *
         * The arg "needed" is a dictionary: {contest:number of ballots needed for that contest}
         */
        val neededList = needed.filter { it.value > 0 }.keys.toList()
        var bestCandidate: Triple<Pair<String, String>, Int, CSVRecord>? = null
        var bestScore = -1.0

        for ((key, rows) in _styles) {
            if (!canDonate(key)) continue
            for ((idx, row) in rows.withIndex()) {
                if (aggregate.containsBallot(row)) continue

                val covered = neededList.filter { contest ->
                    ballotHasContest(row, contest, db.contestToColumns)
                }
                if (covered.isEmpty()) continue

                val gain = covered.sumOf { contest ->
                    imbalanceReduction(contest, row, aggregate.choiceCounts(), db.contestChoiceMeta)
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

    fun _anyCandidate(aggregate: Aggregate): Triple<Pair<String, String>, Int, CSVRecord>? {
        /** Pick any ballot from the pair with the most borrowing surplus. */
        val bestKey = _styles.keys.maxByOrNull { key ->
            _surplus(key).takeIf { it > 0 } ?: Int.MIN_VALUE
        } ?: return null

        for ((idx, row) in _styles[bestKey]?.withIndex() ?: emptyList()) {
            if (!aggregate.containsBallot(row)) {
                return Triple(bestKey, idx, row)
            }
        }
        return null
    }

    fun pullBlockedStyleFor(
        neededContests: Map<String, Int>,
        db: CvrDatabase
    ): Pair<Pair<String, String>, List<CSVRecord>>? {
        /**
         * Find the blocked style (at exactly min_ballots rows in the full CVR) that
         * covers the most contests still needing ballots, pull all its rows, and return
         * (key, rows).  Returns None if no blocked style covers any needed contest.
         *
         * A blocked style can't donate individual ballots without violating Rule d, but
         * pulling the entire style into the aggregate is safe: all its voters become
         * part of the aggregate, and none are left as an identifiable minority.
         */
        val neededList = neededContests.filter { it.value > 0 }.keys.toList()
        if (neededList.isEmpty()) return null

        val (bestKey, _) = _blockedStyles.maxByOrNull { (key, rows) ->
            neededList.sumOf { contest ->
                rows.count { row ->
                    ballotHasContest(row, contest, db.contestToColumns)
                }.coerceAtMost(1) // count each needed contest at most once
            }
        } ?: return null

        val rows = _blockedStyles.remove(bestKey) ?: return null
        return bestKey to rows
    }
}

fun ballotHasContest(
    ballot: CSVRecord, contest: String, contestToColumns: Map<String, List<Int>>
): Boolean {
    // Return True if the ballot has any non-empty column for the given contest.
    val colIndices = contestToColumns[contest] ?: emptyList()
    return colIndices.any { ballot[it].trim() != "" }
}

fun imbalanceReduction(
    contest: String,
    ballot: CSVRecord,
    choiceCounts: Map<String, Map<String, Int>>,
    contestChoiceMeta: Map<String, Map<Int, String>>
): Int {
    /*
    Estimate how much adding this ballot reduces vote imbalance for a contest.

    Imbalance is max_choice_votes minus the sum of all other votes.
    Returns the improvement (positive = less imbalanced), or 0.0 if the ballot
    does not participate in the contest or makes imbalance worse.
    */

    // "current" is the dict that maps choices to votes for that contest.
    // "total" is the sum of all votes for all choices
    // current_max is the vote total for the choice with the highest number of votes.
    val current = choiceCounts[contest] ?: mutableMapOf()
    val currentTotal = current.values.sum()
    val currentMax = current.values.maxOrNull() ?: 0
    val currentGap = currentMax - (currentTotal - currentMax)

    // record the votes on this ballot for each of the choices available for this contest
    val contributions = mutableMapOf<String, Int>()
    for ((colIdx, choiceName) in contestChoiceMeta[contest] ?: emptyMap()) {
        val value = ballot[colIdx].trim()
        if (value.isEmpty() || value == "0") continue
        contributions[choiceName] = 1
    }

    if (contributions.isEmpty()) {
        return 0
    }

    val newCounts = current.toMutableMap()
    for ((choiceName, inc) in contributions) {
        newCounts[choiceName] = newCounts.getOrDefault(choiceName, 0) + inc
    }
    val newTotal = currentTotal + contributions.values.sum()
    val newMax = newCounts.values.maxOrNull() ?: 0
    val newGap = newMax - (newTotal - newMax)

    return max(0, currentGap - newGap)
}

fun buildAggregateRow(
    aggregateBallots: List<CvrRow>, db: CvrDatabase, aggregateId: String
): List<String> {
    /*
    Build the AGGREGATED CSV row by summing vote columns and blanking identifiers.

    Fixed header columns (TabulatorNum through ImprintedId) are blanked.
    CountingGroup, PrecinctPortion, and named_style are blanked.
    BallotType is set to "AGGREGATED".
    Vote columns are summed across all ballots in the aggregate.
    */
    if (aggregateBallots.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    result.add(aggregateId)
    repeat(4)  { result.add("") }
    result.add("AGGREGATED")

    /*val result = aggregateBallots[0].subList(0, db.headerLen).toMutableList()
    //result[0] = aggregateId
    if (result.size > 1) result[1] = ""  // TabulatorNum
    if (result.size > 2) result[2] = ""  // BatchId
    if (result.size > 3) result[3] = ""  // RecordId
    if (result.size > 4) result[4] = ""  // ImprintedId
    db.namedStyleCol?.let { result[it] = "" }
    db.countingGroupIdx?.let { result[it] = "" }
    db.precinctPortionIdx?.let { result[it] = "" }
    db.ballotTypeIdx?.let { result[it] = "AGGREGATED" } */

    /* Sum vote columns across all ballots.
    val numCols = aggregateBallots[0].size()
    for (colIdx in db.headerLen until numCols) {
        var total = 0.0
        for (ballot in aggregateBallots) {
            val value = ballot[colIdx].trim()
            if (value.isNotEmpty() && value.replace(".", "").replace("-", "").all { it.isDigit() }) {
                try {
                    total += value.toDouble()
                } catch (e: NumberFormatException) {
                    // Ignore parse errors
                }
            }
        }
        result.add(if (total == total.toInt().toDouble()) total.toInt().toString() else total.toString())
    } */

    return result
}

fun removeColumns(row: List<String>, colsToRemove: List<Int>): List<String> {
    // Return a copy of row with the specified column indices omitted.
    val removeSet = colsToRemove.toSet()
    val result = mutableListOf<String>()
    for ((i, value) in row.withIndex()) {
        if (i !in removeSet) {
            result.add(value)
        }
    }
    return result
}

fun redactBallotRow(
    ballot: CSVRecord, db: CvrDatabase, redactOnPrecinct: Boolean
): List<String> {
    /*
    Return a copy of the ballot with all vote columns replaced by '*'.

    CountingGroup is always blanked.  PrecinctPortion is blanked only when
    redact_on_precinct is False; when True it is preserved so that the output
    matches non-redacted rows (rare precincts have already been aggregated).
    */
    val result = ballot.toMutableList()
    db.countingGroupIdx?.let { result[it] = "" }
    if (!redactOnPrecinct && db.precinctPortionIdx != null) {
        result[db.precinctPortionIdx!!] = ""
    }
    /* for (colIdx in db.headerLen until result.size) {
        result[colIdx] = "*"
    } */
    return result
}

fun blankGeographicFields(
    ballot: CSVRecord, db: CvrDatabase, redactOnPrecinct: Boolean
): List<String> {
    /*
    Return a copy of the ballot with geographic fields blanked.

    CountingGroup is always blanked.

    PrecinctPortion handling:
      - If redact_on_precinct is False: blank it on all ballots, because
        precinct information is not safe to publish without per-precinct
        redaction.
      - If redact_on_precinct is True: keep it on non-redacted ballots,
        because rare precincts have already been aggregated and the
        remaining precinct values are safe.
    */
    val result = ballot.toMutableList()
    db.countingGroupIdx?.let { result[it] = "" }
    if (!redactOnPrecinct && db.precinctPortionIdx != null) {
        result[db.precinctPortionIdx!!] = ""
    }
    return result
}

    fun _print_borrowing_needs(aggregate: Aggregate, minBallots: Int) {
        // Print a one-time summary of why ballot borrowing is needed.
        if (aggregate.needsMoreTotalBallots()) {
            val needed = minBallots - aggregate.totalCount()
            println("  Aggregate has ${aggregate.totalCount()} ballot(s); need $needed more to reach minimum of $minBallots.")
        }
        val contestNeeds = aggregate.contestsNeedingBallots()
        if (contestNeeds.isNotEmpty()) {
            println("  Contests below minimum:")
            contestNeeds.toSortedMap().forEach { (contest, needed) ->
                println("    '$contest': needs $needed more ballot(s)")
            }
        }
    }

    fun buildAggregate(
        rareBallots: List<CSVRecord>,
        rareContests: Set<String>,
        pool: CommonPool,
        db: CvrDatabase,
        minBallots: Int
    ): Aggregate {
        // Build the aggregate from rare ballots, borrowing from the pool as needed.
        // Satisfies Rules a and b simultaneously using a coverage-weighted ballot
        // selection: each borrowed ballot is chosen to cover as many under-represented
        // contests as possible while also reducing vote imbalance.  Rule d (only borrow
        // from styles that remain common after borrowing) is enforced by CommonPool.
        val aggregate = Aggregate(rareBallots, rareContests, db, minBallots)

        if (!aggregate.satisfiesMinimums()) {
            println("  Rare ballots: ${aggregate.totalCount()}.")
            println("  Borrowing from common styles to satisfy these requirements:")
            println("  - the ballot aggregation must contain at least $minBallots ballots.")
            println("  - every contest in the rare styles must appear on at least $minBallots ballots in the aggregation.")
            println()
            _print_borrowing_needs(aggregate, minBallots)
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
            borrowedCount++
            aggregate.add(ballot)
            pool.remove(styleSig, rowIdx)
        }

        return aggregate
    }

data class NearUnanimousContest(
    val contestName: String,
    val leadingChoice: String?,
    val leadingVotes: Int,
    val totalVotes: Int
)

fun findNearUnanimousContests(
    aggregate: Aggregate,
    db: CvrDatabase
): List<NearUnanimousContest> {
    // Return near-unanimous rare contests.
    //
    // Contests with only one available choice are excluded: they are always unanimous
    // by definition and nothing can be done about it. The check uses the number of
    // choice columns in the CVR, not the number of choices that received votes.
    return aggregate.choiceCounts().mapNotNull { (contestName, choiceVotes) ->
        if (!isRareContestWithMultipleChoices(contestName, choiceVotes, aggregate, db)) {
            return@mapNotNull null
        }

        val totalVotes = choiceVotes.values.sum()
        if (totalVotes == 0) {
            return@mapNotNull null
        }

        val (leadingChoice, leadingChoiceVotes) = choiceVotes.maxByOrNull { (_, votes) -> votes }
            ?: return@mapNotNull null

        val nonLeadingVotes = totalVotes - leadingChoiceVotes
        if (nonLeadingVotes > NEAR_UNANIMOUS_THRESHOLD) {
            return@mapNotNull null
        }

        NearUnanimousContest(
            contestName = contestName,
            leadingChoice = leadingChoice,
            leadingVotes = leadingChoiceVotes,
            totalVotes = totalVotes
        )
    }
}

private fun isRareContestWithMultipleChoices(
    contestName: String,
    choiceVotes: Map<String, Int>,
    aggregate: Aggregate,
    db: CvrDatabase
): Boolean {
    val availableChoiceCount = db.contestToColumns[contestName]?.size ?: 0

    return contestName in aggregate.rareContests &&
            choiceVotes.isNotEmpty() &&
            availableChoiceCount > 1
}

fun balanceUnanimity(
    aggregate: Aggregate,
    pool: CommonPool,
    db: CvrDatabase
) {
    // Add contrasting ballots to prevent near-unanimous vote patterns (Rule c).
    // Only checks contests that appeared on rare ballots; near-unanimity in
    // contests that belong only to common styles is not a concern here.
    val problematic = findNearUnanimousContests(aggregate, db)

    if (problematic.isEmpty()) {
        println("  There are no near-unanimous contests.")
        return
    }

    for ((contestName, maxChoice, maxVotes, totalVotes) in problematic) {
        println("    '$contestName': '$maxChoice' has $maxVotes out of $totalVotes votes")
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
    problematicContests: List<NearUnanimousContest>,
    pool: CommonPool,
    db: CvrDatabase
): List<CSVRecord> {
    // Find ballots from the pool that vote differently in near-unanimous contests.
    // Minimizes total ballots borrowed by preferring ballots that address multiple
    // problematic contests at once.  Aims for MIN_CONTRASTING_VOTES differing
    // ballots per contest.
    if (problematicContests.isEmpty()) return emptyList()

    // For each problematic contest, find which column holds the leading choice.
    val contestInfo = mutableMapOf<String, Map<String, Any?>>()
    for ((contestName, winningChoice, _, _) in problematicContests) {
        val colIndices = db.contestToColumns[contestName] ?: continue
        val colMap = db.contestChoiceMeta[contestName] ?: emptyMap()
        val winningCol = colMap.entries.find { it.value == winningChoice }?.key

        contestInfo[contestName] = mapOf(
            "colIndices" to colIndices,
            "winningCol" to winningCol
        )
    }

    // Score each available ballot by how many problematic contests it votes against.
    val ballotScores = mutableListOf<Triple<Int, List<String>, CSVRecord>>()
    for ((key, rows) in pool.styles()) {
        if (!pool.canDonate(key)) continue
        for (ballot in rows) {
            val satisfied = mutableListOf<String>()
            for ((contestName, _, _, _) in problematicContests) {
                if (contestName !in contestInfo) continue
                val info = contestInfo[contestName] ?: continue
                val hasContest = (info["colIndices"] as? List<Int>)?.any { ballot[it].trim() != "" } ?: false
                if (!hasContest) continue
                val winningCol = info["winningCol"] as? Int
                if (winningCol != null && ballot[winningCol].trim() != "1") {
                    (info["colIndices"] as? List<Int>)?.forEach { colIdx ->
                        if (colIdx != winningCol && ballot[colIdx].trim() == "1") {
                            satisfied.add(contestName)
                            return@forEach
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
    val contestsNeeded = problematicContests.map { it.contestName }.toMutableSet()
    val selected = mutableListOf<CSVRecord>()
    val contrastCounts = mutableMapOf<String, Int>()

    for ((_, satisfied, ballot) in ballotScores) {
        if (satisfied.any { it in contestsNeeded }) {
            selected.add(ballot)
            for (c in satisfied) {
                contrastCounts[c] = contrastCounts.getOrDefault(c, 0) + 1
            }
            contestsNeeded.removeIf { (contrastCounts[it] ?: 0) >= MIN_CONTRASTING_VOTES }
            if (contestsNeeded.isEmpty()) break
        }
    }

    return selected
}

fun _display_contest_name(name: String): String {
    // Strip trailing '(Vote For=N)' from a contest name for display.
    val idx = name.indexOf(" (Vote For=")
    return if (idx >= 0) name.substring(0, idx) else name
}

/*
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
    val contestTotalCounts = mutableMapOf<String, Int>().withDefault { 0 }
    for ((stylePrecinct, rowIndices) in index.rowsByPrivacyUnit) {
        val count = rowIndices.size
        for ((i, contestName) in db.contestNames.withIndex()) {
            if (stylePrecinct.first[i] == '1') {
                contestTotalCounts[contestName] = contestTotalCounts.getValue(contestName) + count
            }
        }
    }

    val rarePrivacyUnitSet = needs.rarePrivacyUnitPairs.keys.toSet()

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

    val rareRows = mutableListOf<CSVRecord>()
    val donorByPrivacyUnit =
        mutableMapOf<Pair<String, String>, MutableList<CSVRecord>>().withDefault { mutableListOf() }
    val blockedByPrivacyUnit =
        mutableMapOf<Pair<String, String>, MutableList<CSVRecord>>().withDefault { mutableListOf() }

    // Maps id(row) to rowIdx for all loaded rows, used to identify aggregated rows.
    val rowToIdx = mutableMapOf<Int, Int>()

    println("*** Pass 2: Building the aggregate row.")
    println()

    // csv.py - read/write/investigate CSV files
    // Use CsvrReader

    File(csvPath).bufferedReader(Charsets.UTF_8).use { breader ->
        val parser =  CSVParser.parse(breader, CSVFormat.DEFAULT)
        val records = parser.iterator()
        repeat(4) { records.next() } // skip headers
        var rowIdx = 4

        while (records.hasNext()) {
            val row: CSVRecord = records.next()
            // reader.lineSequence().filter { row -> row.any { it.isNotBlank() } }.forEachIndexed { rowIdx, row ->
            if (rowIdx > 0 && rowIdx % 100000 == 0) {
                println("  ${String.format("%,d", rowIdx)} rows scanned...")
            }
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
                            val maxChoiceName : String? = tally.maxByOrNull { it.value }?.key

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
        parser.close()
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
*/

fun collectRareRows(
    csvPath: String,
    index: RowIndex,
    needs: RedactionNeeds,
    db: CvrDatabase,
    redactOnPrecinct: Boolean
): Pair<Set<Int>, List<String>> {
    /**
     * Pass 2 (no contest balancing): stream the file and collect only the rare rows.
     * Builds the aggregate from those rows alone — no borrowing from common styles.
     * Returns (redacted_row_indices, aggregate_row).
     */
    val rarePrivacyUnitSet: Set<Pair<String, String>> = needs.rarePrivacyUnitPairs.keys.toSet()

    val rareRows: MutableList<CvrRow> = mutableListOf()
    val redactedRowIndices: MutableSet<Int> = mutableSetOf()

    println("*** Pass 2: Collecting rare ballots (no contest balancing).")
    println()

    db.corlaCvrs.cvrs.forEachIndexed { rowIdx, row ->
        val style = styleForRow(row, db)
        if (style != null) {
            //val rowStyle = index.styleForRow(rowIdx)
            //if (rowStyle == null) continue

            val precinct = if (redactOnPrecinct && row.precinctPortion != null) row.precinctPortion else ""

            val privacyUnit: Pair<String,  String> = Pair(style.name, precinct)
            if (rarePrivacyUnitSet.contains(privacyUnit)) {
                rareRows.add(row)
                redactedRowIndices.add(rowIdx)
            }
        }
    }


    val aggregateRow = buildAggregateRow(rareRows, db, "AGGREGATED")
    return Pair(redactedRowIndices, aggregateRow)
}

fun addToTally(tally: MutableList<Double>, row: List<String>, headerLen: Int) {
    /** Add the vote column values from row into tally (in-place). */
    for (i in tally.indices) {
        val colIdx = headerLen + i
        if (colIdx >= row.size) {
            continue
        }
        val valStr = row[colIdx].trim()
        if (valStr.isNotEmpty()) {
            try {
                tally[i] += valStr.toDouble()
            } catch (_: NumberFormatException) {
            }
        }
    }
}

/*
fun streamRedactedOutput(
    csvPath: String,
    db: CvrDatabase,
    outputFile: String,
    redactedRowIndices: Set<Int>,
    aggregateRow: List<String>?,
    redactOnPrecinct: Boolean,
    redactedListFile: String?
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
    val colsToRemove = mutableListOf<Int>()
    db.countingGroupIdx?.let { colsToRemove.add(it) }
    if (!redactOnPrecinct && db.precinctPortionIdx != null) {
        colsToRemove.add(db.precinctPortionIdx!!)
    }
    colsToRemove.sort()

    val numVoteCols = db.contests.size - db.headerLen
    val preTally: MutableList<Double> = MutableList(numVoteCols) { 0.0 }
    val postTally: MutableList<Double> = MutableList(numVoteCols) { 0.0 }

    val listOut = redactedListFile?.let { File(it).bufferedWriter(Charsets.UTF_8) }

    try {
        File(csvPath).bufferedReader(Charsets.UTF_8).use { breader ->
            File(outputFile).bufferedWriter(Charsets.UTF_8).use { bwriter ->

                val parser =  CSVParser.parse(breader, CSVFormat.DEFAULT)
                val records = parser.iterator()
                repeat(4) { records.next() } // skip headers
                var rowIdx = 4

                while (records.hasNext()) {
                    val row: CSVRecord = records.next()

        /* Csv Reader
        File(csvPath).bufferedReader(Charsets.UTF_8).use { fIn ->
            File(outputFile).bufferedWriter(Charsets.UTF_8).use { fOut ->
                val reader = fIn.lineSequence().iterator()
                val writer = fOut.bufferedWriter()

                repeat(4) {
                    val line = reader.next()
                    writer.write(removeColumns(line.split(","), colsToRemove).joinToString(","))
                    writer.newLine()
                }

                reader.forEachIndexed { rowIdx, line -> */

                    if (rowIdx > 0 && rowIdx % 100000 == 0) {
                        println("  ${rowIdx.toString()} rows written...")
                        System.out.flush()
                    }

                    addToTally(preTally, row.toList(), db.headerLen)
                    if (rowIdx in redactedRowIndices) {
                        listOut?.let {
                            if (row.size() > 4) {
                                it.write(row[4])
                                it.newLine()
                            }
                        }
                        bwriter.write(
                            removeColumns(
                                redactBallotRow(row, db, redactOnPrecinct),
                                colsToRemove
                            ).joinToString(",")
                        )
                        bwriter.newLine()
                    } else {
                        val outRow = blankGeographicFields(row, db, redactOnPrecinct)
                        bwriter.write(removeColumns(outRow, colsToRemove).joinToString(","))
                        bwriter.newLine()
                        addToTally(postTally, row.toList(), db.headerLen)
                    }
                }

                aggregateRow?.let {
                    bwriter.write(removeColumns(it.toList(), colsToRemove).joinToString(","))
                    bwriter.newLine()
                    addToTally(postTally, it, db.headerLen)
                }
            }
        }
    } finally {
        listOut?.close()
    }

    aggregateRow?.let {
        var mismatches = 0
        for (i in 0 until numVoteCols) {
            if (abs(preTally[i] - postTally[i]) > 0.001) {
                mismatches++
            }
        }
        if (mismatches > 0) {
            System.err.println(
                "WARNING: tally mismatch in $mismatches vote column(s) — " +
                        "redacted output may be incorrect."
            )
        } else {
            println("  Tally verification passed.")
        }
    }
}
*/

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
    var redactedRowIndices = emptySet<Int>()
    var aggregateRow: List<String>? = null

    /** Orchestrate passes 2 and 3 to produce the anonymized CVR. */
    if (needs.needsRedaction()) {
        val result = if (noContestBalancing) {
            collectRareRows(csvPath, index, needs, db, redactOnPrecinct)
        } else {
            throw Exception( " loadDonorPool not ready")
            // loadDonorPool(csvPath, index, needs, db, minBallots, redactOnPrecinct)
        }
        redactedRowIndices = result.first
        aggregateRow = result.second

        var rareCount = 0
        for ((key, rowIndices) in index.rowsByPrivacyUnit) {
            if (key in needs.rarePrivacyUnitPairs.keys) {
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
    }

    println("\n*** Pass 3: Writing output.")
    /* streamRedactedOutput(
        csvPath,
        db,
        outputFile,
        redactedRowIndices,
        aggregateRow,
        redactOnPrecinct,
        redactedListFile
    ) */
    println("  Output written to $outputFile.")
    if (redactedListFile != null) {
        println("  Redacted ballot list written to $redactedListFile.")
    }
}

fun reportCheckResults(
    index: RowIndex,
    db: CvrDatabase,
    needs: RedactionNeeds,
    redactOnPrecinct: Boolean
) {
    /** Print the rare-style report and redaction verdict to stdout. */
    val ballotTypesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
    for ((ballotType, rowIndices) in index.rowsByBallotType) {
        for (rowIdx in rowIndices) {
            val style = index.styleForRow(rowIdx)
            if (style != null) {
                ballotTypesByStyle.computeIfAbsent(style) { mutableSetOf() }.add(ballotType)
            }
        }
    }

    val namedStylesByStyle: MutableMap<String, MutableSet<String>> = mutableMapOf()
    for ((namedStyle, rowIndices) in index.rowsByNamedStyle) {
        for (rowIdx in rowIndices) {
            val style = index.styleForRow(rowIdx)
            if (style != null) {
                namedStylesByStyle.computeIfAbsent(style) { mutableSetOf() }.add(namedStyle)
            }
        }
    }

    val totalStyles = index.styleStrings.size
    val showPrecinct = redactOnPrecinct && db.precinctPortionIdx != null

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
    }

    if (needs.needsRedaction()) {
        println("\nRedaction is needed.")
    } else {
        println("No redaction needed.")
    }
}

/**
 * Run check mode: pass 1 only.  Reports whether the CVR needs redaction.
 * All output goes to stdout/stderr (redirectable for GUI use).
 */
fun executeCheck(
    inputFile: String,
    minBallots: Int,
    redactOnPrecinct: Boolean,
    styleCol: Int? = null
) {
        val db = try {
            CvrDatabase(inputFile, styleCol)
        } catch (e: Exception) {
            logger.error{"Error reading CVR file: $e"}
            return
        }

        if (redactOnPrecinct && db.precinctPortionIdx == null) {
            logger.warn{
                "WARNING: --redact-on-precinct was requested but the CVR has no " +
                        "PrecinctPortion column. The option will have no effect." }
        }

        println("*** Pass 1: Looking for rare ballot styles.")
        println()
        val index = try {
            buildRowIndex( db, redactOnPrecinct, checkMode = true)
        } catch (e: Exception) {
            logger.error{"Error building row index: $e"}
            return
        }
        println()

        val needs = checkRedactionNeeds(index, db, minBallots, redactOnPrecinct)

        for (warning in needs.leakageWarnings) {
            logger.warn{warning}
        }

        reportCheckResults(index, db, needs, redactOnPrecinct)
    // }
}

fun executeRedact(
    inputFile: String,
    outputFile: String,
    redactedListFile: String?,
    minBallots: Int,
    redactOnPrecinct: Boolean,
    styleCol: Int?, // = null,
    noContestBalancing: Boolean, // = false
) {
    /**
     * Run full redaction: passes 1, 2, and 3.  Writes the anonymized CVR.
     * All output goes to stdout/stderr (redirectable for GUI use).
     */
    // TempCVRFile(inputFile).use { csvPath ->
        val db = try {
            CvrDatabase(inputFile, styleCol)
        } catch (e: Exception) {
            logger.error{"Error reading CVR file: $e"}
            return
        }

        if (redactOnPrecinct && db.precinctPortionIdx == null) {
            logger.warn{
                "WARNING: --redact-on-precinct was requested but the CVR has no " +
                        "PrecinctPortion column. The option will have no effect."}
        }

        println("*** Pass 1: Looking for rare ballot styles.")
        println()
        val index = try {
            buildRowIndex( db, redactOnPrecinct, checkMode = false)
        } catch (e: Exception) {
            logger.error{"Error building row index: $e"}
            return
        }
        println()

        val needs = checkRedactionNeeds(index, db, minBallots, redactOnPrecinct)

        for (warning in needs.leakageWarnings) {
            logger.warn{"WARNING: $warning"}
        }

        reportCheckResults(index, db, needs, redactOnPrecinct)

        performRedaction(
            inputFile,
            db,
            index,
            needs,
            minBallots,
            outputFile,
            redactOnPrecinct,
            redactedListFile,
            noContestBalancing
        )
    // }
}


// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

/*
Clikt: The undisputed industry standard for Kotlin CLI development.
    It features a highly extensible design, supports subcommands natively, offers great type safety via property delegates, and fully supports Kotlin Multiplatform (KMP).
Mordant: Built by the same author as Clikt, this library is specifically designed for styling text, handling ANSI escape codes, colors,
    and formatting terminal output nicely in KMP projects.
Kotter: An excellent choice if you want to build a fully interactive, dynamic terminal user interface (TUI) with support for rendering complex live UI components.
 */
enum class Mode {check, redact}

object AnonymizeCvrs {

    @JvmStatic
    fun main(args: Array<String>) {

        val parser = ArgParser("AnonymizeCvr")
        val mode by parser.option(
            ArgType.Choice<Mode>  { it.name.lowercase() },
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
            without borrowing from common styles to satisfy minimums or 
            balance near-unanimous contests.
        """.trimIndent()
        ).default(true)

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
                println("""Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. 
                    Ballot styles with fewer than --min-ballots ballots are aggregated to protect voter privacy.
                """)
                return
            }

            if (version != null) {
                println("version = $VERSION")
                return
            }
            
            print("AnonymizeCvr ${mode} for $input, min_ballots=$min_ballots")
            if (mode == Mode.redact) print(" output to $output")
            print(" no_contest_balancing=$no_contest_balancing")
            print(" redact_on_precinct=$redact_on_precinct")
            if (stylecol != null) print(" stylecol=$stylecol")
            if (redacted_list_filename != null) print(" redacted_list_filename=$redacted_list_filename")
            print(" version=$version")
            println()
            
            if (mode == Mode.redact && output == null) {
                println(" you must set an output file ehen redacting")
                return
            }

            when (mode) {
                Mode.check  -> executeCheck(input, min_ballots, redact_on_precinct, stylecol)
                else -> executeRedact(input, output!!, redacted_list_filename, min_ballots, 
                    redact_on_precinct, stylecol, no_contest_balancing)
            }
            
        } catch (t: Throwable) {
            println(t.message)
        }
    }
}

