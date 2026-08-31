#!/usr/bin/env python3
"""
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
"""

import argparse
import csv
import io
import os
import queue as queue_module
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox
from collections import defaultdict
from typing import Dict, List, Optional, Set, Tuple

VERSION = "0.2"

MIN_BALLOTS_DEFAULT = 10
NEAR_UNANIMOUS_THRESHOLD = 2  # "all but N votes" triggers balancing (Rule c)
MIN_CONTRASTING_VOTES = 3  # contrasting votes needed per contest after balancing
COVERAGE_WEIGHT = 10.0  # weight for contest coverage vs. vote-balance score
DONOR_SURPLUS_THRESHOLD = (
    3  # minimum surplus above min_ballots for a style/precinct to donate freely
)


# ---------------------------------------------------------------------------
# _QueueWriter
# ---------------------------------------------------------------------------


class _QueueWriter:
    """File-like object that puts lines into a queue for GUI display."""

    def __init__(self, q: "queue_module.Queue[Optional[str]]") -> None:
        self._q = q
        self._buf = ""

    def write(self, text: str) -> None:
        self._buf += text
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            self._q.put(line)

    def flush(self) -> None:
        if self._buf:
            self._q.put(self._buf)
            self._buf = ""

    def fileno(self) -> int:
        raise io.UnsupportedOperation("fileno")


# ---------------------------------------------------------------------------
# TempCVRFile
# ---------------------------------------------------------------------------


class TempCVRFile:
    """
    Context manager for CVR input files.  Returns the input path directly;
    parquet conversion is not supported in this build.
    """

    def __init__(self, input_path: str) -> None:
        self.input_path = input_path

    def __enter__(self) -> str:
        return self.input_path

    def __exit__(
        self,
        exc_type: Optional[type],
        exc_val: Optional[BaseException],
        exc_tb: Optional[object],
    ) -> None:
        pass


# ---------------------------------------------------------------------------
# CvrDatabase
# ---------------------------------------------------------------------------


class CvrDatabase:
    """
    Reads a CVR file header and builds contest/choice mappings.

    The Colorado CVR CSV format has four header rows, then one ballot per row:
      Row 1: version / election name
      Row 2: contest names (one name per choice column, repeated)
      Row 3: choice (candidate) names, one per column
      Row 4: column headers (CvrNumber, TabulatorNum, BatchId, RecordId,
              ImprintedId, CountingGroup, PrecinctPortion, BallotType, ...)
      Rows 5+: one ballot per row (not loaded here — see build_row_index)

    After construction the following are available:

      contest_names         — ordered list of unique contest names
      contest_to_columns    — contest name -> list of column indices
      contest_choice_meta   — contest name -> {col_idx: choice_name}
    """

    def __init__(
        self,
        input_file: str,
        headerlen: int,
        named_style_col: Optional[int],
    ) -> None:
        """
        Read the CVR file header and build contest/choice mappings.

        Args:
            input_file:      Path to the CVR CSV file.
            headerlen:       Number of header columns before vote data begins.
                             Pass 0 to auto-detect from the contests row.
            named_style_col: Column index of the named_style field, or None.
        """
        self.input_file = input_file
        self.headerlen = headerlen
        self.named_style_col = named_style_col

        # Four header rows read from the file.
        self.version: List[str] = []
        self.contests: List[str] = []
        self.choices: List[str] = []
        self.headers: List[str] = []

        # Line terminator found in the file (needed when writing output later).
        self.lineterminator: str = ""

        # Column indices for named special columns.  None means not present.
        self.ballot_type_idx: Optional[int] = None
        self.precinct_portion_idx: Optional[int] = None
        self.counting_group_idx: Optional[int] = None

        # Ordered list of unique contest names (defines positions in style strings).
        self.contest_names: List[str] = []

        # contest name -> list of column indices for that contest's choices
        self.contest_to_columns: Dict[str, List[int]] = {}

        # contest name -> {col_idx: choice_name} (used for vote tallying)
        self.contest_choice_meta: Dict[str, Dict[int, str]] = {}

        self._read_file()
        self._validate()
        self._build_contest_map()

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _read_file(self) -> None:
        """Read the four header rows and detect the line terminator."""
        with open(self.input_file, "rb") as raw:
            chunk = raw.read(1024)
            if b"\r\n" in chunk:
                self.lineterminator = "\r\n"
            elif b"\r" in chunk:
                self.lineterminator = "\r"
            else:
                self.lineterminator = "\n"

        with open(self.input_file, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            self.version = next(reader)
            self.contests = next(reader)
            self.choices = next(reader)
            self.headers = next(reader)

        if self.headerlen == 0:
            for cell in self.contests:
                if cell.strip() == "":
                    self.headerlen += 1
                else:
                    break
            if self.headerlen == 0:
                raise ValueError(
                    "Could not auto-detect headerlen: no empty cells at the "
                    "start of the contests row."
                )

        for idx, name in enumerate(self.headers):
            lower = name.strip().lower()
            if lower == "ballottype":
                self.ballot_type_idx = idx
            elif lower == "precinctportion":
                self.precinct_portion_idx = idx
            elif lower == "countinggroup":
                self.counting_group_idx = idx

    def _validate(self) -> None:
        """Check that the file structure is usable."""
        if self.headerlen >= len(self.contests):
            raise ValueError(
                f"No vote columns found: headerlen={self.headerlen} but "
                f"contests row has only {len(self.contests)} columns."
            )
        if self.named_style_col is not None and self.named_style_col >= self.headerlen:
            raise ValueError(
                f"Named style column {self.named_style_col} must be within "
                f"the header columns (headerlen={self.headerlen})."
            )

    def _build_contest_map(self) -> None:
        """Build the contest name -> column indices mapping and ordered contest list."""
        mapping: Dict[str, List[int]] = defaultdict(list)
        for col_idx in range(self.headerlen, len(self.contests)):
            name = self.contests[col_idx].strip()
            if not name:
                raise ValueError(
                    f"Contest row column {col_idx} is empty; expected a contest name."
                )
            if name not in mapping:
                self.contest_names.append(name)
            mapping[name].append(col_idx)
        self.contest_to_columns = dict(mapping)

        # Build choice metadata: for each contest, map column index to choice name.
        self.contest_choice_meta = {}
        for contest_name, col_indices in self.contest_to_columns.items():
            col_map: Dict[int, str] = {}
            for col_idx in col_indices:
                choice_name = (
                    self.choices[col_idx].strip() if col_idx < len(self.choices) else ""
                )
                col_map[col_idx] = choice_name if choice_name else f"Choice{col_idx}"
            self.contest_choice_meta[contest_name] = col_map


# ---------------------------------------------------------------------------
# RowIndex
# ---------------------------------------------------------------------------


class RowIndex:
    """
    Lightweight index built by pass 1 (build_row_index).

    Stores row indices (integers) rather than ballot data, so memory cost
    is proportional to row count, not ballot width.

    rows_by_style_precinct uses "" as the precinct value for every row when
    --redact-on-precinct is False, so a single dict handles both cases.

    rows_by_ballot_type is always populated when the BallotType column exists.
    rows_by_named_style is populated only when --stylecol is given.
    Only one of them is used for leakage detection (named_style takes priority).
    """

    def __init__(self) -> None:
        # (style_string, precinct) -> list of row indices
        self.rows_by_privacy_unit: Dict[Tuple[str, str], List[int]] = {}
        # table of unique style strings; index into this list is the style ID
        self.style_strings: List[str] = []
        # style ID (index into style_strings) per row index; None for skip rows
        self.style_id_for_row: List[Optional[int]] = []
        # named_style value -> list of row indices (only when --stylecol is given)
        self.rows_by_named_style: Dict[str, List[int]] = {}
        # ballot_type value -> list of row indices (only when BallotType column exists)
        self.rows_by_ballot_type: Dict[str, List[int]] = {}
        # total non-empty rows counted (includes skip rows)
        self.total_rows: int = 0

    def style_for_row(self, row_idx: int) -> Optional[str]:
        """Return the style string for a row, or None if the row was skipped."""
        style_id = self.style_id_for_row[row_idx]
        return None if style_id is None else self.style_strings[style_id]


def _should_skip_row(row: List[str], db: CvrDatabase) -> bool:
    """
    Return True if this row is an artifact of a previous redaction pass
    and should be excluded from the row index.

    Skips redacted ballot rows (any vote column contains '*') and the
    aggregate summary row (BallotType == "AGGREGATED" or CvrNumber == "AGGREGATED").
    """
    for col_idx in range(db.headerlen, len(row)):
        if row[col_idx].strip() == "*":
            return True
    if db.ballot_type_idx is not None:
        if row[db.ballot_type_idx].strip() == "AGGREGATED":
            return True
    if row[0].strip() == "AGGREGATED":
        return True
    return False


def _style_for_row(row: List[str], db: CvrDatabase) -> str:
    """
    Compute the style string for one ballot row.

    Returns a string of '1' and '0' characters, one per contest in
    contest_names order.  '1' means the contest is present on the ballot.
    """
    parts = []
    for contest_name in db.contest_names:
        col_indices = db.contest_to_columns[contest_name]
        present = False
        for col_idx in col_indices:
            if row[col_idx].strip() != "":
                present = True
                break
        parts.append("1" if present else "0")
    return "".join(parts)


def build_row_index(
    csv_path: str, db: CvrDatabase, redact_on_precinct: bool, check_mode: bool = False
) -> RowIndex:
    """
    Pass 1: read every ballot row and build the lightweight row index.

    Stores row indices, not ballot data.  Skip rows (redacted ballots and
    aggregate rows from a prior run) are counted in total_rows but excluded
    from all grouping dicts.

    Row indices are 0-based from the first ballot row (row 5 in the file).
    Every non-empty row increments the index, including skip rows, so that
    indices are consistent across all three passes.
    """
    index = RowIndex()
    style_table: Dict[str, int] = (
        {}
    )  # style_string -> style ID (index into style_strings)
    by_privacy_unit: Dict[Tuple[str, str], List[int]] = defaultdict(list)
    by_named_style: Dict[str, List[int]] = defaultdict(list)
    by_ballot_type: Dict[str, List[int]] = defaultdict(list)

    expected_cols = len(db.contests)

    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for _ in range(4):
            next(reader)

        for row_idx, row in enumerate(
            row for row in reader if any(v.strip() for v in row)
        ):
            if row_idx > 0 and row_idx % 100000 == 0:
                print(f"  {row_idx:,} rows scanned...", flush=True)
            if len(row) != expected_cols:
                raise ValueError(
                    f"Row {row_idx + 5} has {len(row)} columns but contests row "
                    f"has {expected_cols}."
                )
            if _should_skip_row(row, db):
                if not check_mode:
                    raise ValueError(
                        "input file contains already-redacted rows; "
                        "cannot re-redact a previously anonymized file."
                    )
                index.style_id_for_row.append(None)
                continue
            # County pre-redacted rows: all vote columns blank.  Nothing to analyse or
            # anonymize; pass through unchanged in both check and redact modes.
            if all(row[i].strip() == "" for i in range(db.headerlen, len(row))):
                index.style_id_for_row.append(None)
                continue

            style_str = _style_for_row(row, db)
            if style_str not in style_table:
                style_id = len(index.style_strings)
                style_table[style_str] = style_id
                index.style_strings.append(style_str)
            style_id = style_table[style_str]
            index.style_id_for_row.append(style_id)
            style = index.style_strings[style_id]

            if redact_on_precinct and db.precinct_portion_idx is not None:
                precinct = row[db.precinct_portion_idx].strip()
            else:
                precinct = ""
            by_privacy_unit[(style, precinct)].append(row_idx)

            if db.named_style_col is not None:
                named_style = row[db.named_style_col].strip()
                by_named_style[named_style].append(row_idx)

            if db.ballot_type_idx is not None:
                ballot_type = row[db.ballot_type_idx].strip()
                if ballot_type:
                    by_ballot_type[ballot_type].append(row_idx)

    index.total_rows = len(index.style_id_for_row)
    index.rows_by_privacy_unit = dict(by_privacy_unit)
    index.rows_by_named_style = dict(by_named_style)
    index.rows_by_ballot_type = dict(by_ballot_type)
    return index


# ---------------------------------------------------------------------------
# RedactionNeeds
# ---------------------------------------------------------------------------


class RedactionNeeds:
    """
    Describes what the CVR requires before it can be safely published.

    Populated by check_redaction_needs().  The redaction logic reads this to
    decide what work to do.

    Rare styles and rare (style, precinct) pairs both require the same kind of
    treatment: ballots must be aggregated so no individual voter can be identified.
    """

    def __init__(self) -> None:
        # Styles with too few ballots.
        # Key: style string.  Value: ballot count.
        self.rare_styles: Dict[str, int] = {}

        # Privacy units (style, precinct) with too few ballots.
        # When --redact-on-precinct is False, precinct is always "" so each key
        # is (style, "") and the unit is equivalent to the style alone.
        # Per C.R.S. 24-72-205.5, the privacy unit is the combination of contest
        # pattern and precinct, not each independently.
        # Key: (style string, PrecinctPortion value).  Value: ballot count.
        self.rare_privacy_unit_pairs: Dict[Tuple[str, str], int] = {}

        # Human-readable leakage warnings.  Leakage is reported but not corrected.
        self.leakage_warnings: List[str] = []

    def needs_redaction(self) -> bool:
        """Return True if any redaction work is required."""
        return len(self.rare_styles) > 0 or len(self.rare_privacy_unit_pairs) > 0


# ---------------------------------------------------------------------------
# check_redaction_needs
# ---------------------------------------------------------------------------


def check_redaction_needs(
    index: RowIndex,
    db: CvrDatabase,
    min_ballots: int,
    redact_on_precinct: bool,
) -> RedactionNeeds:
    """
    Examine the row index and return a description of what needs redacting.

    Args:
        index:              The row index built by build_row_index (pass 1).
        db:                 The CVR database (header and contest map only).
        min_ballots:        Minimum ballots required per style or precinct pair.
        redact_on_precinct: If True, also check individual (style, precinct) pairs.

    Returns:
        A RedactionNeeds object describing what must be done.
    """
    needs = RedactionNeeds()

    # Compute per-style totals by summing across all (style, precinct) keys.
    # When redact_on_precinct is False, all precincts are "" so each style has
    # exactly one key and the sum equals the style's total ballot count.
    style_totals: Dict[str, int] = defaultdict(int)
    for (style, precinct), row_indices in index.rows_by_privacy_unit.items():
        style_totals[style] += len(row_indices)

    for style, total in style_totals.items():
        if total < min_ballots:
            needs.rare_styles[style] = total

    # Always populate rare_privacy_unit_pairs.
    # When redact_on_precinct is False, all precincts are "" so each key is
    # (style, "") and the count equals the style's total ballot count.
    for (style, precinct), row_indices in index.rows_by_privacy_unit.items():
        if len(row_indices) < min_ballots:
            needs.rare_privacy_unit_pairs[(style, precinct)] = len(row_indices)

    # Leakage detection (Rule 10): use named_style if --stylecol was given,
    # otherwise use ballot_type.  Only one check is run.
    if db.named_style_col is not None:
        named_styles_by_style: Dict[str, Set[str]] = defaultdict(set)
        for named_style, row_indices in index.rows_by_named_style.items():
            for row_idx in row_indices:
                row_style = index.style_for_row(row_idx)
                if row_style is not None:
                    named_styles_by_style[row_style].add(named_style)
        for style, named_styles in named_styles_by_style.items():
            if len(named_styles) > 1:
                names = ", ".join(sorted(named_styles))
                needs.leakage_warnings.append(
                    f"Leakage: named styles [{names}] all share the same contest pattern"
                )
    elif db.ballot_type_idx is not None:
        ballot_types_by_style: Dict[str, Set[str]] = defaultdict(set)
        for ballot_type, row_indices in index.rows_by_ballot_type.items():
            for row_idx in row_indices:
                row_style = index.style_for_row(row_idx)
                if row_style is not None:
                    ballot_types_by_style[row_style].add(ballot_type)
        for style, ballot_types in ballot_types_by_style.items():
            if len(ballot_types) > 1:
                types = ", ".join(sorted(ballot_types))
                needs.leakage_warnings.append(
                    f"Leakage: ballot types [{types}] all share the same contest pattern"
                )

    return needs


# ---------------------------------------------------------------------------
# Aggregate
# ---------------------------------------------------------------------------


class Aggregate:
    """
    Accumulates ballots into the anonymized aggregate pool, tracking
    counts needed to verify the redaction rules.

    All four anonymization rules pertain to building the aggregate:

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
            borrow.  Enforced by CommonPool, which removes a style from the
            pool entirely once it would drop below the minimum.
    """

    def __init__(
        self,
        initial_ballots: List[List[str]],
        rare_contests: Set[str],
        db: CvrDatabase,
        min_ballots: int,
    ) -> None:
        self._db = db
        self.min_ballots = min_ballots
        self.rare_contests = rare_contests
        self.ballots: List[List[str]] = []
        self._ballot_ids: Set[int] = set()

        # For each contest, how many ballots in the aggregate include that contest.
        self._contest_ballot_counts: Dict[str, int] = defaultdict(int)

        # For each contest (the list of contests already exists in the db and will
        # not change), establish a dictionary which maps the contest name to an inner
        # dictionary (which will be filled in by add()) which will map the contest's
        # choices to the number of votes for each choice.
        self._contest_choice_counts: Dict[str, Dict[str, int]] = {
            c: {} for c in db.contest_to_columns
        }
        for ballot in initial_ballots:
            self.add(ballot)

    def add(self, ballot: List[str]) -> None:
        """Add a ballot to the aggregate, updating all tracked counts."""
        self.ballots.append(ballot)
        self._ballot_ids.add(id(ballot))

        for contest_name, col_indices in self._db.contest_to_columns.items():
            present = any(ballot[col_idx].strip() != "" for col_idx in col_indices)
            if present:
                self._contest_ballot_counts[contest_name] += 1

        for contest_name, col_map in self._db.contest_choice_meta.items():
            for col_idx, choice_name in col_map.items():
                val = ballot[col_idx].strip()
                if not val or val == "0":
                    continue
                try:
                    increment = int(float(val))
                except ValueError:
                    increment = 1
                # get the inner [choice_name:count] dict for the contest.
                counts = self._contest_choice_counts[contest_name]
                counts[choice_name] = counts.get(choice_name, 0) + increment

    def contains_ballot(self, ballot: List[str]) -> bool:
        return id(ballot) in self._ballot_ids

    def total_count(self) -> int:
        return len(self.ballots)

    def needs_more_total_ballots(self) -> bool:
        """True if the aggregate does not yet have min_ballots total (Rule a)."""
        return len(self.ballots) < self.min_ballots

    def contests_needing_ballots(self) -> Dict[str, int]:
        """
        Return {contest: ballots_still_needed} for Rule b (each rare contest
        needs >= min_ballots).
        """
        result = {}
        for contest in self.rare_contests:
            count = self._contest_ballot_counts[contest]
            if count < self.min_ballots:
                result[contest] = self.min_ballots - count
        return result

    def satisfies_minimums(self) -> bool:
        """True when both Rule a and Rule b are satisfied."""
        return (
            not self.needs_more_total_ballots()
            and len(self.contests_needing_ballots()) == 0
        )

    def choice_counts(self) -> Dict[str, Dict[str, int]]:
        """Return per-contest per-choice vote counts (used for unanimity checking)."""
        return self._contest_choice_counts


# ---------------------------------------------------------------------------
# CommonPool
# ---------------------------------------------------------------------------


class CommonPool:
    """
    Pool of common-style ballots available for borrowing into the aggregate.

    Enforces Rule d: borrowing a ballot from a pair never leaves that pair with
    fewer than min_ballots ballots remaining in the full dataset.  Surplus is
    tracked against the full dataset count from pass 1 (via full_counts), not
    against the number of donor rows loaded into memory, so that loading only a
    subset of a pair's ballots does not artificially restrict borrowing.

    Keys are (style_string, precinct) tuples.  When --redact-on-precinct is
    False, precinct is always "" so each key is (style_string, "").
    """

    def __init__(
        self,
        styles: Dict[Tuple[str, str], List[List[str]]],
        min_ballots: int,
        rowcount_by_privacy_unit: Dict[Tuple[str, str], int],
        blocked_styles: Optional[Dict[Tuple[str, str], List[List[str]]]] = None,
    ) -> None:
        self._styles: Dict[Tuple[str, str], List[List[str]]] = {
            key: list(rows) for key, rows in styles.items()
        }
        self.min_ballots = min_ballots
        self._rowcount_by_privacy_unit = rowcount_by_privacy_unit
        self._removed_counts: Dict[Tuple[str, str], int] = {}
        self._blocked_styles: Dict[Tuple[str, str], List[List[str]]] = {}
        if blocked_styles is not None:
            for key, rows in blocked_styles.items():
                self._blocked_styles[key] = list(rows)

    def _surplus(self, key: Tuple[str, str]) -> int:
        """
        How many more ballots this pair can donate while keeping
        full_remaining >= min_ballots.  Positive means donating is allowed.
        """
        full = self._rowcount_by_privacy_unit.get(key, 0)
        removed = self._removed_counts.get(key, 0)
        return full - removed - self.min_ballots

    def can_donate(self, key: Tuple[str, str]) -> bool:
        """True if this pair can still donate at least one ballot without violating Rule d."""
        return self._surplus(key) > 0

    def is_empty(self) -> bool:
        return len(self._styles) == 0

    def styles(self) -> Dict[Tuple[str, str], List[List[str]]]:
        return self._styles

    def remove(self, key: Tuple[str, str], row_idx: int) -> None:
        """Remove one ballot; drop the pair from the pool if it can no longer donate."""
        rows = self._styles[key]
        rows.pop(row_idx)
        self._removed_counts[key] = self._removed_counts.get(key, 0) + 1
        if not rows or self._surplus(key) <= 0:
            del self._styles[key]

    def remove_rows(self, rows_to_remove: List[List[str]]) -> None:
        """Remove all ballots in rows_to_remove from the pool (matched by object identity)."""
        remove_ids = {id(r) for r in rows_to_remove}
        for key in list(self._styles.keys()):
            rows = self._styles[key]
            remaining = []
            removed_here = 0
            for row in rows:
                if id(row) in remove_ids:
                    removed_here += 1
                else:
                    remaining.append(row)
            if removed_here > 0:
                self._removed_counts[key] = (
                    self._removed_counts.get(key, 0) + removed_here
                )
            if not remaining or self._surplus(key) <= 0:
                del self._styles[key]
            else:
                self._styles[key] = remaining

    def best_candidate_for(
        self, aggregate: Aggregate, db: CvrDatabase
    ) -> Optional[Tuple[Tuple[str, str], int, List[str]]]:
        """
        Return (key, row_idx, ballot) for the best ballot to borrow, or None.

        Prioritizes ballots that cover the most contests still needing ballots
        (Rule b), weighted by how much they reduce vote imbalance.  Falls back
        to any ballot from the pair with the most surplus when only the total
        count is short (Rule a).
        """
        needed = aggregate.contests_needing_ballots()
        if needed:
            return self._best_for_contests(aggregate, db, needed)
        elif aggregate.needs_more_total_ballots():
            return self._any_candidate(aggregate)
        else:
            return None

    def _best_for_contests(
        self,
        aggregate: Aggregate,
        db: CvrDatabase,
        needed: Dict[str, int],
    ) -> Optional[Tuple[Tuple[str, str], int, List[str]]]:
        """
        Find the best ballot for bringing the aggregation into agreement with Rule b
        (has at least min_ballots per contest).

        The arg "needed" is a dictionary: {contest:number of ballots needed for that contest}
        """
        needed_list = [c for c, n in needed.items() if n > 0]
        best_candidate: Optional[Tuple[Tuple[str, str], int, List[str]]] = None
        best_score = -1.0

        for key, rows in self._styles.items():
            if not self.can_donate(key):
                continue
            for idx, row in enumerate(rows):
                if aggregate.contains_ballot(row):
                    continue
                covered = [
                    contest
                    for contest in needed_list
                    if _ballot_has_contest(row, contest, db.contest_to_columns)
                ]
                if not covered:
                    continue
                # "gain" indicates an improvement in the score, even though it is
                # accomplished by a "reduction" in the imbalance.
                gain = sum(
                    _imbalance_reduction(
                        c, row, aggregate.choice_counts(), db.contest_choice_meta
                    )
                    for c in covered
                )
                score = COVERAGE_WEIGHT * len(covered) + gain
                if score > best_score:
                    best_score = score
                    best_candidate = (key, idx, row)

        return best_candidate

    def _any_candidate(
        self, aggregate: Aggregate
    ) -> Optional[Tuple[Tuple[str, str], int, List[str]]]:
        """Pick any ballot from the pair with the most borrowing surplus."""
        best_key: Optional[Tuple[str, str]] = None
        best_surplus = 0
        for key in self._styles:
            s = self._surplus(key)
            if s > 0 and s > best_surplus:
                best_key = key
                best_surplus = s
        if best_key is None:
            return None
        for idx, row in enumerate(self._styles[best_key]):
            if aggregate.contains_ballot(row):
                continue
            return (best_key, idx, row)
        return None

    def pull_blocked_style_for(
        self,
        needed_contests: Dict[str, int],
        db: CvrDatabase,
    ) -> Optional[Tuple[Tuple[str, str], List[List[str]]]]:
        """
        Find the blocked style (at exactly min_ballots rows in the full CVR) that
        covers the most contests still needing ballots, pull all its rows, and return
        (key, rows).  Returns None if no blocked style covers any needed contest.

        A blocked style can't donate individual ballots without violating Rule d, but
        pulling the entire style into the aggregate is safe: all its voters become
        part of the aggregate, and none are left as an identifiable minority.
        """
        needed_list = []
        for contest, n in needed_contests.items():
            if n > 0:
                needed_list.append(contest)
        if not needed_list:
            return None

        best_key = None
        best_coverage = 0

        for key, rows in self._blocked_styles.items():
            coverage = 0
            for contest in needed_list:
                for row in rows:
                    if _ballot_has_contest(row, contest, db.contest_to_columns):
                        coverage += 1
                        break  # count each needed contest at most once
            if coverage > best_coverage:
                best_coverage = coverage
                best_key = key

        if best_key is None:
            return None
        rows = self._blocked_styles.pop(best_key)
        return (best_key, rows)


# ---------------------------------------------------------------------------
# Redaction helpers
# ---------------------------------------------------------------------------


def _ballot_has_contest(
    ballot: List[str], contest: str, contest_to_columns: Dict[str, List[int]]
) -> bool:
    """Return True if the ballot has any non-empty column for the given contest."""
    col_indices = contest_to_columns.get(contest, [])
    return any(ballot[col_idx].strip() != "" for col_idx in col_indices)


def _imbalance_reduction(
    contest: str,
    ballot: List[str],
    choice_counts: Dict[str, Dict[str, int]],
    contest_choice_meta: Dict[str, Dict[int, str]],
) -> float:
    """
    Estimate how much adding this ballot reduces vote imbalance for a contest.

    Imbalance is max_choice_votes minus the sum of all other votes.
    Returns the improvement (positive = less imbalanced), or 0.0 if the ballot
    does not participate in the contest or makes imbalance worse.
    """

    # "current" is the dict that maps choices to votes for that contest.
    # "total" is the sum of all votes for all choices
    # current_max is the vote total for the choice with the highest number of votes.
    current = choice_counts.get(contest, {})
    current_total = sum(current.values())
    current_max = max(current.values()) if current else 0
    current_gap = current_max - (current_total - current_max)

    # record the votes on this ballot for each of the choices available for this contest
    contributions: Dict[str, int] = {}
    for col_idx, choice_name in contest_choice_meta[contest].items():
        val = ballot[col_idx].strip()
        if not val or val == "0":
            continue
        contributions[choice_name] = 1

    if not contributions:
        return 0.0

    new_counts = dict(current)
    for choice_name, inc in contributions.items():
        new_counts[choice_name] = new_counts.get(choice_name, 0) + inc
    new_total = current_total + sum(contributions.values())
    new_max = max(new_counts.values()) if new_counts else 0
    new_gap = new_max - (new_total - new_max)

    return max(0.0, current_gap - new_gap)


def _build_aggregate_row(
    ballots: List[List[str]], db: CvrDatabase, aggregate_id: str
) -> List[str]:
    """
    Build the AGGREGATED CSV row by summing vote columns and blanking identifiers.

    Fixed header columns (TabulatorNum through ImprintedId) are blanked.
    CountingGroup, PrecinctPortion, and named_style are blanked.
    BallotType is set to "AGGREGATED".
    Vote columns are summed across all ballots in the aggregate.
    """
    if not ballots:
        return []

    result = ballots[0][: db.headerlen].copy()
    result[0] = aggregate_id
    if len(result) > 1:
        result[1] = ""  # TabulatorNum
    if len(result) > 2:
        result[2] = ""  # BatchId
    if len(result) > 3:
        result[3] = ""  # RecordId
    if len(result) > 4:
        result[4] = ""  # ImprintedId
    if db.named_style_col is not None:
        result[db.named_style_col] = ""
    if db.counting_group_idx is not None:
        result[db.counting_group_idx] = ""
    if db.precinct_portion_idx is not None:
        result[db.precinct_portion_idx] = ""
    if db.ballot_type_idx is not None:
        result[db.ballot_type_idx] = "AGGREGATED"

    # Sum vote columns across all ballots.
    num_cols = len(ballots[0])
    for col_idx in range(db.headerlen, num_cols):
        total = 0.0
        for ballot in ballots:
            val = ballot[col_idx].strip()
            if val and val.replace(".", "").replace("-", "").isdigit():
                try:
                    total += float(val)
                except ValueError:
                    pass
        if total == int(total):
            result.append(str(int(total)))
        else:
            result.append(str(total))

    return result


def _remove_columns(row: List[str], cols_to_remove: List[int]) -> List[str]:
    """Return a copy of row with the specified column indices omitted."""
    remove_set = set(cols_to_remove)
    result = []
    for i, val in enumerate(row):
        if i not in remove_set:
            result.append(val)
    return result


def _redact_ballot_row(
    ballot: List[str], db: CvrDatabase, redact_on_precinct: bool
) -> List[str]:
    """
    Return a copy of the ballot with all vote columns replaced by '*'.

    CountingGroup is always blanked.  PrecinctPortion is blanked only when
    redact_on_precinct is False; when True it is preserved so that the output
    matches non-redacted rows (rare precincts have already been aggregated).
    """
    result = ballot.copy()
    if db.counting_group_idx is not None:
        result[db.counting_group_idx] = ""
    if not redact_on_precinct and db.precinct_portion_idx is not None:
        result[db.precinct_portion_idx] = ""
    for col_idx in range(db.headerlen, len(result)):
        result[col_idx] = "*"
    return result


def _blank_geographic_fields(
    ballot: List[str], db: CvrDatabase, redact_on_precinct: bool
) -> List[str]:
    """
    Return a copy of the ballot with geographic fields blanked.

    CountingGroup is always blanked.

    PrecinctPortion handling:
      - If redact_on_precinct is False: blank it on all ballots, because
        precinct information is not safe to publish without per-precinct
        redaction.
      - If redact_on_precinct is True: keep it on non-redacted ballots,
        because rare precincts have already been aggregated and the
        remaining precinct values are safe.
    """
    result = ballot.copy()
    if db.counting_group_idx is not None:
        result[db.counting_group_idx] = ""
    if not redact_on_precinct and db.precinct_portion_idx is not None:
        result[db.precinct_portion_idx] = ""
    return result


def _print_borrowing_needs(aggregate: Aggregate, min_ballots: int) -> None:
    """Print a one-time summary of why ballot borrowing is needed."""
    if aggregate.needs_more_total_ballots():
        needed = min_ballots - aggregate.total_count()
        print(
            f"  Aggregate has {aggregate.total_count()} ballot(s); "
            f"need {needed} more to reach minimum of {min_ballots}."
        )
    contest_needs = aggregate.contests_needing_ballots()
    if contest_needs:
        print("  Contests below minimum:")
        for contest, needed in sorted(contest_needs.items()):
            print(f"    '{contest}': needs {needed} more ballot(s)")


def build_aggregate(
    rare_ballots: List[List[str]],
    rare_contests: Set[str],
    pool: CommonPool,
    db: CvrDatabase,
    min_ballots: int,
) -> Aggregate:
    """
    Build the aggregate from rare ballots, borrowing from the pool as needed.

    Satisfies Rules a and b simultaneously using a coverage-weighted ballot
    selection: each borrowed ballot is chosen to cover as many under-represented
    contests as possible while also reducing vote imbalance.  Rule d (only borrow
    from styles that remain common after borrowing) is enforced by CommonPool.
    """
    aggregate = Aggregate(rare_ballots, rare_contests, db, min_ballots)

    if not aggregate.satisfies_minimums():
        print(f"  Rare ballots: {aggregate.total_count()}.")
        print("  Borrowing from common styles to satisfy these requirements:")
        print(
            f"  - the ballot aggregation must contain at least {min_ballots} ballots."
        )
        print(
            f"  - every contest in the rare styles must appear on at least"
            f" {min_ballots} ballots in the aggregation."
        )
        print()
        _print_borrowing_needs(aggregate, min_ballots)
        print(
            "\n  Selecting ballots to borrow from common styles (this can take a few minutes)..."
        )

    borrowed_count = 0

    while not aggregate.satisfies_minimums():
        result = pool.best_candidate_for(aggregate, db)
        if result is None:
            blocked = pool.pull_blocked_style_for(
                aggregate.contests_needing_ballots(), db
            )
            if blocked is not None:
                _, blocked_rows = blocked
                for ballot in blocked_rows:
                    aggregate.add(ballot)
                continue
            for contest, needed in aggregate.contests_needing_ballots().items():
                print(
                    f"WARNING: could not find enough ballots for contest "
                    f"'{contest}' (still need {needed} more). "
                    f"This contest only appears on rare ballot styles.",
                    file=sys.stderr,
                )
            break
        style_sig, row_idx, ballot = result
        borrowed_count += 1
        aggregate.add(ballot)
        pool.remove(style_sig, row_idx)

    return aggregate


def _find_near_unanimous_contests(
    aggregate: Aggregate,
    db: CvrDatabase,
) -> List[Tuple[str, Optional[str], int, int]]:
    """
    Return (contest_name, max_choice, max_votes, total_votes) for every rare contest
    in the aggregate that is near-unanimous.

    Contests with only one available choice are excluded: they are always unanimous
    by definition and nothing can be done about it.  The check uses the number of
    choice columns in the CVR, not the number of choices that received votes, so a
    two-choice contest where all ballots voted for the same candidate is NOT excluded.
    """
    result = []
    for contest_name, choice_votes in aggregate.choice_counts().items():
        if contest_name not in aggregate.rare_contests:
            continue
        if not choice_votes:
            continue
        if len(db.contest_to_columns.get(contest_name, [])) <= 1:
            continue
        total_votes = sum(choice_votes.values())
        if total_votes == 0:
            continue
        max_choice = None
        max_votes = 0
        for choice, votes in choice_votes.items():
            if votes > max_votes:
                max_votes = votes
                max_choice = choice
        other_votes = total_votes - max_votes
        if other_votes <= NEAR_UNANIMOUS_THRESHOLD:
            result.append((contest_name, max_choice, max_votes, total_votes))
    return result


def balance_unanimity(
    aggregate: Aggregate,
    pool: CommonPool,
    db: CvrDatabase,
) -> None:
    """
    Add contrasting ballots to prevent near-unanimous vote patterns (Rule c).

    Only checks contests that appeared on rare ballots; near-unanimity in
    contests that belong only to common styles is not a concern here.
    """
    problematic = _find_near_unanimous_contests(aggregate, db)

    if not problematic:
        print("  There are no near-unanimous contests.")
        return

    for contest_name, max_choice, max_votes, total_votes in problematic:
        print(
            f"    '{contest_name}': '{max_choice}' has "
            f"{max_votes} out of {total_votes} votes"
        )

    contrasting = find_contrasting_ballots_multi(problematic, pool, db)
    if contrasting:
        pool.remove_rows(contrasting)
        for ballot in contrasting:
            aggregate.add(ballot)
        print()
        print(f"  Borrowed {len(contrasting)} contrasting ballot(s).")


def find_contrasting_ballots_multi(
    problematic_contests: List[Tuple[str, Optional[str], int, int]],
    pool: CommonPool,
    db: CvrDatabase,
) -> List[List[str]]:
    """
    Find ballots from the pool that vote differently in near-unanimous contests.

    Minimizes total ballots borrowed by preferring ballots that address multiple
    problematic contests at once.  Aims for MIN_CONTRASTING_VOTES differing
    ballots per contest.
    """
    if not problematic_contests:
        return []

    # For each problematic contest, find which column holds the leading choice.
    contest_info: Dict[str, Dict] = {}
    for contest_name, winning_choice, _, _ in problematic_contests:
        col_indices = db.contest_to_columns.get(contest_name, [])
        if not col_indices:
            continue
        col_map = db.contest_choice_meta.get(contest_name, {})
        winning_col = None
        for col_idx, choice_name in col_map.items():
            if choice_name == winning_choice:
                winning_col = col_idx
                break
        contest_info[contest_name] = {
            "col_indices": col_indices,
            "winning_col": winning_col,
        }

    # Score each available ballot by how many problematic contests it votes against.
    ballot_scores: List[Tuple[int, List[str], List[str]]] = []
    for key, rows in pool.styles().items():
        if not pool.can_donate(key):
            continue
        for ballot in rows:
            satisfied = []
            for contest_name, _, _, _ in problematic_contests:
                if contest_name not in contest_info:
                    continue
                info = contest_info[contest_name]
                has_contest = any(
                    ballot[col_idx].strip() != "" for col_idx in info["col_indices"]
                )
                if not has_contest:
                    continue
                winning_col = info["winning_col"]
                if winning_col is not None and ballot[winning_col].strip() != "1":
                    for col_idx in info["col_indices"]:
                        if col_idx != winning_col and ballot[col_idx].strip() == "1":
                            satisfied.append(contest_name)
                            break
            if satisfied:
                ballot_scores.append((len(satisfied), satisfied, ballot))

    ballot_scores.sort(key=lambda x: x[0], reverse=True)

    # Greedily select ballots until each problematic contest has enough contrast.
    contests_needed: Set[str] = {name for name, _, _, _ in problematic_contests}
    selected: List[List[str]] = []
    contrast_counts: Dict[str, int] = defaultdict(int)

    for score, satisfied, ballot in ballot_scores:
        if set(satisfied) & contests_needed:
            selected.append(ballot)
            for c in satisfied:
                contrast_counts[c] += 1
            for c in list(contests_needed):
                if contrast_counts[c] >= MIN_CONTRASTING_VOTES:
                    contests_needed.discard(c)
            if not contests_needed:
                break

    return selected


def _display_contest_name(name: str) -> str:
    """Strip trailing '(Vote For=N)' from a contest name for display."""
    idx = name.find(" (Vote For=")
    return name[:idx] if idx >= 0 else name


def load_donor_pool(
    csv_path: str,
    index: RowIndex,
    needs: RedactionNeeds,
    db: CvrDatabase,
    min_ballots: int,
    redact_on_precinct: bool,
) -> Tuple[Set[int], List[str]]:
    """
    Pass 2: stream the file, load rare rows and a targeted donor pool,
    build the aggregate, and return the set of aggregated row indices
    and the aggregate summary row.

    Returns:
        redacted_row_indices  — 0-based row indices of all ballots in the aggregate
        aggregate_row         — the AGGREGATED summary row to append to the output
    """
    per_contest_pool_target = 10 * min_ballots

    # Pre-compute how many rows contain each contest.
    # Each (style, precinct) key's style string encodes which contests are present.
    contest_total_counts: Dict[str, int] = defaultdict(int)
    for (style, _), row_indices in index.rows_by_privacy_unit.items():
        count = len(row_indices)
        for i, contest_name in enumerate(db.contest_names):
            if style[i] == "1":
                contest_total_counts[contest_name] += count

    rare_privacy_unit_set: Set[Tuple[str, str]] = set(
        needs.rare_privacy_unit_pairs.keys()
    )

    # Rare-ballot contests: any contest present on at least one rare ballot.
    rare_ballot_contests: Set[str] = set()
    for style, _ in rare_privacy_unit_set:
        for i, contest_name in enumerate(db.contest_names):
            if style[i] == "1":
                rare_ballot_contests.add(contest_name)

    # Rare contests: rare-ballot contests with fewer than per_contest_pool_target
    # total appearances in the full CVR.
    rare_contests: Set[str] = set()
    for contest_name in rare_ballot_contests:
        if contest_total_counts[contest_name] < per_contest_pool_target:
            rare_contests.add(contest_name)

    # Row counts per privacy unit, passed to CommonPool for Rule d enforcement.
    rowcount_by_privacy_unit: Dict[Tuple[str, str], int] = {}
    for key, row_indices in index.rows_by_privacy_unit.items():
        rowcount_by_privacy_unit[key] = len(row_indices)

    # Tracks how many donor rows have been loaded per rare-ballot contest.
    donor_count_by_rare_ballot_contest: Dict[str, int] = defaultdict(int)

    # Tracks vote distribution of loaded donors per rare-ballot contest.
    # contest_name -> {choice_name: count of donors voting "1" for that choice}
    # Used to ensure we load enough contrasting donors to fix near-unanimity.
    donor_voted_tally: Dict[str, Dict[str, int]] = {}

    rare_rows: List[List[str]] = []
    donor_by_privacy_unit: Dict[Tuple[str, str], List[List[str]]] = defaultdict(list)
    blocked_by_privacy_unit: Dict[Tuple[str, str], List[List[str]]] = defaultdict(list)

    # Maps id(row) to row_idx for all loaded rows, used to identify aggregated rows.
    row_to_idx: Dict[int, int] = {}

    print("*** Pass 2: Building the aggregate row.")
    print()

    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for _ in range(4):
            next(reader)

        for row_idx, row in enumerate(
            row for row in reader if any(v.strip() for v in row)
        ):
            if row_idx > 0 and row_idx % 100000 == 0:
                print(f"  {row_idx:,} rows scanned...", flush=True)
            row_style = index.style_for_row(row_idx)
            if row_style is None:
                continue

            if redact_on_precinct and db.precinct_portion_idx is not None:
                precinct = row[db.precinct_portion_idx].strip()
            else:
                precinct = ""
            privacy_unit = (row_style, precinct)

            if privacy_unit in rare_privacy_unit_set:
                rare_rows.append(row)
                row_to_idx[id(row)] = row_idx
            else:
                privacy_unit_count = rowcount_by_privacy_unit.get(privacy_unit, 0)
                if privacy_unit_count <= min_ballots:
                    if privacy_unit_count == min_ballots:
                        # Blocked style: at exactly min_ballots, can't donate
                        # individual ballots without violating Rule d.  Track for
                        # potential whole-style pull if individual borrowing stalls.
                        style_has_rare_contest = False
                        for i, contest_name in enumerate(db.contest_names):
                            if (
                                contest_name in rare_ballot_contests
                                and row_style[i] == "1"
                            ):
                                style_has_rare_contest = True
                                break
                        if style_has_rare_contest:
                            blocked_by_privacy_unit[privacy_unit].append(row)
                            row_to_idx[id(row)] = row_idx
                    continue

                # Find which rare-ballot contests are present on this row.
                row_rare_contests: List[str] = []
                for contest_name in rare_ballot_contests:
                    if _ballot_has_contest(row, contest_name, db.contest_to_columns):
                        row_rare_contests.append(contest_name)

                if not row_rare_contests:
                    continue

                if privacy_unit_count <= min_ballots + DONOR_SURPLUS_THRESHOLD:
                    # Tight surplus: only load if this row has a rare contest.
                    has_rare_contest = False
                    for contest_name in row_rare_contests:
                        if contest_name in rare_contests:
                            has_rare_contest = True
                            break
                    if not has_rare_contest:
                        continue
                else:
                    # Comfortable surplus: load if pool needs more donors for any
                    # rare-ballot contest, OR if this row provides contrasting votes
                    # for a contest where loaded donors are currently too one-sided.
                    pool_needs_more = False
                    for contest_name in row_rare_contests:
                        if (
                            donor_count_by_rare_ballot_contest[contest_name]
                            < per_contest_pool_target
                        ):
                            pool_needs_more = True
                            break

                    if not pool_needs_more:
                        needs_contrast = False
                        for contest_name in row_rare_contests:
                            tally = donor_voted_tally.get(contest_name, {})
                            if not tally:
                                continue

                            # For this contest, find the choice with the most votes.
                            max_choice = None
                            max_count = -1
                            for choice_name, count in tally.items():
                                if count > max_count:
                                    max_count = count
                                    max_choice = choice_name

                            row_voted = None
                            for col_idx, choice_name in db.contest_choice_meta.get(
                                contest_name, {}
                            ).items():
                                if row[col_idx].strip() == "1":
                                    row_voted = choice_name
                                    break
                            if row_voted is None or row_voted == max_choice:
                                continue
                            contrasting_so_far = 0
                            for c, v in tally.items():
                                if c != max_choice:
                                    contrasting_so_far += v
                            if contrasting_so_far < MIN_CONTRASTING_VOTES:
                                needs_contrast = True
                                break
                        if not needs_contrast:
                            continue

                donor_by_privacy_unit[privacy_unit].append(row)
                row_to_idx[id(row)] = row_idx
                for contest_name in row_rare_contests:
                    donor_count_by_rare_ballot_contest[contest_name] += 1
                    tally = donor_voted_tally.setdefault(contest_name, {})
                    for col_idx, choice_name in db.contest_choice_meta.get(
                        contest_name, {}
                    ).items():
                        if row[col_idx].strip() == "1":
                            tally[choice_name] = tally.get(choice_name, 0) + 1
                            break

    pool = CommonPool(
        dict(donor_by_privacy_unit),
        min_ballots,
        rowcount_by_privacy_unit,
        dict(blocked_by_privacy_unit),
    )

    aggregate = build_aggregate(rare_rows, rare_ballot_contests, pool, db, min_ballots)
    borrowed_after_ab = aggregate.total_count() - len(rare_rows)
    if borrowed_after_ab > 0:
        print(f"  Ballots borrowed for minimum counts: {borrowed_after_ab}")

    print("\n*** Balancing near-unanimous contests.\n")
    print("  Make sure that the following constraint is met:")
    print("  - No contest in the aggregate may be near-unanimous. 'Near-unanimous'")
    print(f"    means all but {NEAR_UNANIMOUS_THRESHOLD} votes go to a single choice.")
    print()
    # Loop until no near-unanimous contests remain or the pool is exhausted.
    # Each pass either adds at least one ballot (shrinking the finite pool) or
    # adds nothing, in which case we are stuck and exit.  Termination is guaranteed.
    still_problematic: List[Tuple[str, Optional[str], int, int]] = []
    while True:
        count_before = aggregate.total_count()
        balance_unanimity(aggregate, pool, db)
        still_problematic = _find_near_unanimous_contests(aggregate, db)
        if not still_problematic:
            break
        if aggregate.total_count() == count_before:
            break  # balance_unanimity could not add any ballots; no point looping

    borrowed_after_c = aggregate.total_count() - len(rare_rows)
    unanimity_borrowed = borrowed_after_c - borrowed_after_ab
    if unanimity_borrowed > 0:
        print(f"  Total ballots borrowed for unanimity balancing: {unanimity_borrowed}")

    if still_problematic:
        print()
        print(
            "  WARNING: the following contests remain near-unanimous after balancing."
        )
        print("  No contrasting ballots were available in the donor pool.")
        for contest_name, max_choice, max_votes, total_votes in still_problematic:
            print(
                f"    '{contest_name}': '{max_choice}' has "
                f"{max_votes} out of {total_votes} votes"
            )

    # Identify which row indices are in the aggregate by object identity.
    redacted_row_indices: Set[int] = set()
    for ballot in aggregate.ballots:
        idx = row_to_idx.get(id(ballot))
        if idx is not None:
            redacted_row_indices.add(idx)

    aggregate_row = _build_aggregate_row(aggregate.ballots, db, "AGGREGATED")

    return redacted_row_indices, aggregate_row


def collect_rare_rows(
    csv_path: str,
    index: RowIndex,
    needs: RedactionNeeds,
    db: CvrDatabase,
    redact_on_precinct: bool,
) -> Tuple[Set[int], List[str]]:
    """
    Pass 2 (no contest balancing): stream the file and collect only the rare rows.
    Builds the aggregate from those rows alone — no borrowing from common styles.
    Returns (redacted_row_indices, aggregate_row).
    """
    rare_privacy_unit_set: Set[Tuple[str, str]] = set(
        needs.rare_privacy_unit_pairs.keys()
    )

    rare_rows: List[List[str]] = []
    redacted_row_indices: Set[int] = set()

    print("*** Pass 2: Collecting rare ballots (no contest balancing).")
    print()

    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for _ in range(4):
            next(reader)

        for row_idx, row in enumerate(
            row for row in reader if any(v.strip() for v in row)
        ):
            if row_idx > 0 and row_idx % 100000 == 0:
                print(f"  {row_idx:,} rows scanned...", flush=True)

            row_style = index.style_for_row(row_idx)
            if row_style is None:
                continue

            if redact_on_precinct and db.precinct_portion_idx is not None:
                precinct = row[db.precinct_portion_idx].strip()
            else:
                precinct = ""
            privacy_unit = (row_style, precinct)

            if privacy_unit in rare_privacy_unit_set:
                rare_rows.append(row)
                redacted_row_indices.add(row_idx)

    aggregate_row = _build_aggregate_row(rare_rows, db, "AGGREGATED")
    return redacted_row_indices, aggregate_row


def _add_to_tally(tally: List[float], row: List[str], headerlen: int) -> None:
    """Add the vote column values from row into tally (in-place)."""
    for i in range(len(tally)):
        col_idx = headerlen + i
        if col_idx >= len(row):
            continue
        val = row[col_idx].strip()
        if val:
            try:
                tally[i] += float(val)
            except ValueError:
                pass


def _stream_redacted_output(
    csv_path: str,
    db: CvrDatabase,
    output_file: str,
    redacted_row_indices: Set[int],
    aggregate_row: Optional[List[str]],
    redact_on_precinct: bool,
    redacted_list_file: Optional[str],
) -> None:
    """
    Pass 3: stream input to output, redacting rows in redacted_row_indices,
    appending the aggregate row (if any), and verifying vote tallies match.

    pre_tally  — running sum of original vote columns for every input row.
    post_tally — running sum of vote columns for non-redacted rows, plus the
                 aggregate row.  Must equal pre_tally if redaction is correct.
    """
    # Columns that carry no information in the output and should be omitted entirely.
    # CountingGroup is always removed.  PrecinctPortion is removed when not redacting
    # on precinct, because every row has it blanked in that case.
    cols_to_remove: List[int] = []
    if db.counting_group_idx is not None:
        cols_to_remove.append(db.counting_group_idx)
    if not redact_on_precinct and db.precinct_portion_idx is not None:
        cols_to_remove.append(db.precinct_portion_idx)
    cols_to_remove.sort()

    num_vote_cols = len(db.contests) - db.headerlen
    pre_tally: List[float] = [0.0] * num_vote_cols
    post_tally: List[float] = [0.0] * num_vote_cols

    list_out = (
        open(redacted_list_file, "w", encoding="utf-8")
        if redacted_list_file is not None
        else None
    )
    try:
        with open(csv_path, "r", encoding="utf-8") as f_in:
            with open(output_file, "w", encoding="utf-8", newline="") as f_out:
                reader = csv.reader(f_in)
                writer = csv.writer(f_out, lineterminator=db.lineterminator)

                for _ in range(4):
                    writer.writerow(_remove_columns(next(reader), cols_to_remove))

                for row_idx, row in enumerate(
                    r for r in reader if any(v.strip() for v in r)
                ):
                    if row_idx > 0 and row_idx % 100000 == 0:
                        print(f"  {row_idx:,} rows written...", flush=True)
                    _add_to_tally(pre_tally, row, db.headerlen)
                    if row_idx in redacted_row_indices:
                        if list_out is not None and len(row) > 4:
                            list_out.write(row[4] + "\n")
                        writer.writerow(
                            _remove_columns(
                                _redact_ballot_row(row, db, redact_on_precinct),
                                cols_to_remove,
                            )
                        )
                    else:
                        out_row = _blank_geographic_fields(row, db, redact_on_precinct)
                        writer.writerow(_remove_columns(out_row, cols_to_remove))
                        _add_to_tally(post_tally, row, db.headerlen)

                if aggregate_row is not None:
                    writer.writerow(_remove_columns(aggregate_row, cols_to_remove))
                    _add_to_tally(post_tally, aggregate_row, db.headerlen)
    finally:
        if list_out is not None:
            list_out.close()

    if aggregate_row is not None:
        mismatches = 0
        for i in range(num_vote_cols):
            if abs(pre_tally[i] - post_tally[i]) > 0.001:
                mismatches += 1
        if mismatches:
            print(
                f"WARNING: tally mismatch in {mismatches} vote column(s) — "
                f"redacted output may be incorrect.",
                file=sys.stderr,
            )
        else:
            print("  Tally verification passed.")


def perform_redaction(
    csv_path: str,
    db: CvrDatabase,
    index: RowIndex,
    needs: RedactionNeeds,
    min_ballots: int,
    output_file: str,
    redact_on_precinct: bool,
    redacted_list_file: Optional[str],
    no_contest_balancing: bool = False,
) -> None:
    """Orchestrate passes 2 and 3 to produce the anonymized CVR."""
    if needs.needs_redaction():
        if no_contest_balancing:
            result = collect_rare_rows(csv_path, index, needs, db, redact_on_precinct)
        else:
            result = load_donor_pool(
                csv_path, index, needs, db, min_ballots, redact_on_precinct
            )
        redacted_row_indices: Set[int] = result[0]
        aggregate_row: Optional[List[str]] = result[1]

        rare_count = 0
        for key, row_indices in index.rows_by_privacy_unit.items():
            if key in needs.rare_privacy_unit_pairs:
                rare_count += len(row_indices)

        print("\n*** Pass 2 complete.")
        print(f"  Ballots from rare styles/precincts: {rare_count}")
        if not no_contest_balancing:
            borrowed_count = len(redacted_row_indices) - rare_count
            if borrowed_count > 0:
                print(f"  Ballots borrowed from common styles: {borrowed_count}")
        print(f"  Total ballots in aggregate: {len(redacted_row_indices)}")
    else:
        redacted_row_indices = set()
        aggregate_row = None

    print("\n*** Pass 3: Writing output.")
    _stream_redacted_output(
        csv_path,
        db,
        output_file,
        redacted_row_indices,
        aggregate_row,
        redact_on_precinct,
        redacted_list_file,
    )
    print(f"  Output written to {output_file}.")
    if redacted_list_file is not None:
        print(f"  Redacted ballot list written to {redacted_list_file}.")


# ---------------------------------------------------------------------------
# High-level execution functions (shared by CLI and GUI)
# ---------------------------------------------------------------------------


def _report_check_results(
    index: RowIndex,
    db: CvrDatabase,
    needs: RedactionNeeds,
    redact_on_precinct: bool,
) -> None:
    """Print the rare-style report and redaction verdict to stdout."""
    ballot_types_by_style: Dict[str, Set[str]] = defaultdict(set)
    for ballot_type, row_indices in index.rows_by_ballot_type.items():
        for row_idx in row_indices:
            style = index.style_for_row(row_idx)
            if style is not None:
                ballot_types_by_style[style].add(ballot_type)

    named_styles_by_style: Dict[str, Set[str]] = defaultdict(set)
    for named_style, row_indices in index.rows_by_named_style.items():
        for row_idx in row_indices:
            style = index.style_for_row(row_idx)
            if style is not None:
                named_styles_by_style[style].add(named_style)

    total_styles = len(index.style_strings)
    show_precinct = redact_on_precinct and db.precinct_portion_idx is not None

    if needs.rare_privacy_unit_pairs:
        if show_precinct:
            total_pairs = len(index.rows_by_privacy_unit)
            print(
                f"  Rare ballot style/precinct combinations "
                f"({len(needs.rare_privacy_unit_pairs)} of {total_pairs} total):"
            )
        else:
            print(
                f"  Rare ballot styles "
                f"({len(needs.rare_privacy_unit_pairs)} of {total_styles} total):"
            )
        style_to_id: Dict[str, int] = {}
        for i, s in enumerate(index.style_strings):
            style_to_id[s] = i

        for (style, precinct), count in sorted(
            needs.rare_privacy_unit_pairs.items(), key=lambda item: item[1]
        ):
            ballot_types = ballot_types_by_style.get(style, set())
            named_styles_for_style = named_styles_by_style.get(style, set())

            parts = []
            if show_precinct:
                parts.append(f'precinct "{precinct}"')
            if ballot_types:
                parts.append(
                    "ballot type: " + ", ".join(f'"{t}"' for t in sorted(ballot_types))
                )
            elif named_styles_for_style:
                parts.append(
                    "named style: "
                    + ", ".join(f'"{n}"' for n in sorted(named_styles_for_style))
                )
            parts.append(f"style #{style_to_id[style]}")
            print(f"    {count} ballot(s)  [{', '.join(parts)}]")

    if needs.needs_redaction():
        print("\nRedaction is needed.")
    else:
        print("No redaction needed.")


def execute_check(
    input_file: str,
    min_ballots: int,
    redact_on_precinct: bool,
    style_col: Optional[int] = None,
) -> None:
    """
    Run check mode: pass 1 only.  Reports whether the CVR needs redaction.
    All output goes to stdout/stderr (redirectable for GUI use).
    """
    with TempCVRFile(input_file) as csv_path:
        try:
            db = CvrDatabase(csv_path, 0, style_col)
        except (ValueError, OSError) as e:
            print(f"Error reading CVR file: {e}", file=sys.stderr)
            return

        if redact_on_precinct and db.precinct_portion_idx is None:
            print(
                "WARNING: --redact-on-precinct was requested but the CVR has no "
                "PrecinctPortion column. The option will have no effect.",
                file=sys.stderr,
            )

        print("*** Pass 1: Looking for rare ballot styles.")
        print()
        try:
            index = build_row_index(csv_path, db, redact_on_precinct, check_mode=True)
        except (ValueError, OSError) as e:
            print(f"Error building row index: {e}", file=sys.stderr)
            return
        print()

        needs = check_redaction_needs(index, db, min_ballots, redact_on_precinct)

        for warning in needs.leakage_warnings:
            print(f"WARNING: {warning}", file=sys.stderr)

        _report_check_results(index, db, needs, redact_on_precinct)


def execute_redact(
    input_file: str,
    output_file: str,
    redacted_list_file: Optional[str],
    min_ballots: int,
    redact_on_precinct: bool,
    style_col: Optional[int] = None,
    no_contest_balancing: bool = False,
) -> None:
    """
    Run full redaction: passes 1, 2, and 3.  Writes the anonymized CVR.
    All output goes to stdout/stderr (redirectable for GUI use).
    """
    with TempCVRFile(input_file) as csv_path:
        try:
            db = CvrDatabase(csv_path, 0, style_col)
        except (ValueError, OSError) as e:
            print(f"Error reading CVR file: {e}", file=sys.stderr)
            return

        if redact_on_precinct and db.precinct_portion_idx is None:
            print(
                "WARNING: --redact-on-precinct was requested but the CVR has no "
                "PrecinctPortion column. The option will have no effect.",
                file=sys.stderr,
            )

        print("*** Pass 1: Looking for rare ballot styles.")
        print()
        try:
            index = build_row_index(csv_path, db, redact_on_precinct, check_mode=False)
        except (ValueError, OSError) as e:
            print(f"Error building row index: {e}", file=sys.stderr)
            return
        print()

        needs = check_redaction_needs(index, db, min_ballots, redact_on_precinct)

        for warning in needs.leakage_warnings:
            print(f"WARNING: {warning}", file=sys.stderr)

        _report_check_results(index, db, needs, redact_on_precinct)

        perform_redaction(
            csv_path,
            db,
            index,
            needs,
            min_ballots,
            output_file,
            redact_on_precinct,
            redacted_list_file,
            no_contest_balancing,
        )


# ---------------------------------------------------------------------------
# Argument parsing (CLI)
# ---------------------------------------------------------------------------


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. "
            "Ballot styles with fewer than --min-ballots ballots are aggregated "
            "to protect voter privacy."
        )
    )
    parser.add_argument(
        "--version",
        action="version",
        version=f"anonymize_cvr {VERSION}",
    )
    parser.add_argument(
        "input_file",
        help="Path to the input CVR file (CSV format).",
    )
    parser.add_argument(
        "output_file",
        nargs="?",
        default=None,
        help=(
            "Path for the redacted output CVR file. "
            "Required in redact mode; not needed in --check mode."
        ),
    )
    parser.add_argument(
        "--check",
        "-c",
        action="store_true",
        help="Check mode: report whether redaction is needed without writing output.",
    )
    parser.add_argument(
        "--redact-on-precinct",
        action="store_true",
        help=(
            "Treat precincts with fewer than --min-ballots ballots as rare "
            "and aggregate them, instead of simply blanking the PrecinctPortion column."
        ),
    )
    parser.add_argument(
        "--min-ballots",
        type=int,
        default=MIN_BALLOTS_DEFAULT,
        metavar="N",
        help=f"Minimum ballots required per style or precinct (default: {MIN_BALLOTS_DEFAULT}).",
    )
    parser.add_argument(
        "--stylecol",
        type=int,
        default=None,
        metavar="N",
        help="Column index (0-based) of the named_style field, if present.",
    )
    parser.add_argument(
        "--no-contest-balancing",
        action="store_true",
        help=(
            "Do not do contest balancing. Redact only the rare-style ballots "
            "without borrowing from common styles to satisfy minimums or "
            "balance near-unanimous contests."
        ),
    )
    parser.add_argument(
        "--redacted-list",
        metavar="FILENAME",
        default=None,
        help=(
            "Write the ImprintedId of every redacted ballot to FILENAME, "
            "one per line.  Useful for identifying ballot images that also need redaction."
        ),
    )
    args = parser.parse_args()
    if args.check and args.output_file is not None:
        parser.error("output_file cannot be specified in --check mode.")
    if not args.check and args.output_file is None:
        parser.error("output_file is required when not in --check mode.")
    return args


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------


def cli_main() -> None:
    args = parse_args()
    if args.check:
        execute_check(
            args.input_file,
            args.min_ballots,
            args.redact_on_precinct,
            args.stylecol,
        )
    else:
        execute_redact(
            args.input_file,
            args.output_file,
            args.redacted_list,
            args.min_ballots,
            args.redact_on_precinct,
            args.stylecol,
            args.no_contest_balancing,
        )


# ---------------------------------------------------------------------------
# GUI
# ---------------------------------------------------------------------------


class CvrAnonymizerApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("CVR Anonymizer")
        self.root.geometry("700x540")
        self.root.resizable(True, True)
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

        self.config_redact_on_precinct: bool = False
        self.config_no_contest_balancing: bool = False
        self.config_min_ballots: int = MIN_BALLOTS_DEFAULT
        self.config_style_col: Optional[int] = None

        self._output_queue: "queue_module.Queue[Optional[str]]" = queue_module.Queue()
        self._run_button: Optional[tk.Button] = None
        self._status_text: Optional[tk.Text] = None

        self._show_main_menu()

    def _on_close(self) -> None:
        if messagebox.askokcancel("Quit", "Are you sure you want to exit?"):
            self.root.destroy()

    def _clear_screen(self) -> None:
        for widget in self.root.winfo_children():
            widget.destroy()

    # ------------------------------------------------------------------
    # Main menu
    # ------------------------------------------------------------------

    def _show_main_menu(self) -> None:
        self._clear_screen()

        tk.Label(
            self.root, text="CVR Anonymizer", font=("TkDefaultFont", 14, "bold")
        ).pack(pady=20)

        tk.Label(
            self.root,
            text="Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5",
            font=("TkDefaultFont", 9),
            fg="gray",
        ).pack(pady=(0, 2))

        tk.Label(
            self.root,
            text=f"Version {VERSION}",
            font=("TkDefaultFont", 9),
            fg="gray",
        ).pack(pady=(0, 15))

        btn_frame = tk.Frame(self.root, padx=10, pady=5)
        btn_frame.pack()

        tk.Button(
            btn_frame,
            text="(1) Configure Redaction Options",
            command=self._show_config_screen,
            width=42,
        ).pack(pady=5)
        tk.Button(
            btn_frame,
            text="(2) Produce Redacted CVR",
            command=self._show_redact_screen,
            width=42,
        ).pack(pady=5)
        tk.Button(
            btn_frame,
            text="(3) Check CVR File",
            command=self._show_check_screen,
            width=42,
        ).pack(pady=5)
        tk.Button(
            btn_frame,
            text="Quit",
            command=self._on_close,
            width=42,
        ).pack(pady=(15, 5))

        parts = [f"Min ballots: {self.config_min_ballots}"]
        if self.config_redact_on_precinct:
            parts.append("Redact on precinct: yes")
        if self.config_no_contest_balancing:
            parts.append("No contest balancing")
        if self.config_style_col is not None:
            parts.append(f"Style column: {self.config_style_col}")
        tk.Label(
            self.root,
            text="  |  ".join(parts),
            font=("TkDefaultFont", 9),
            fg="gray",
        ).pack(pady=(20, 0))

    # ------------------------------------------------------------------
    # Configure options screen
    # ------------------------------------------------------------------

    def _show_config_screen(self) -> None:
        self._clear_screen()

        tk.Label(
            self.root,
            text="Configure Redaction Options",
            font=("TkDefaultFont", 14, "bold"),
        ).pack(pady=15)

        frame = tk.Frame(self.root, padx=20, pady=5)
        frame.pack(fill=tk.X)

        self._redact_on_precinct_var = tk.BooleanVar(
            value=self.config_redact_on_precinct
        )
        tk.Checkbutton(
            frame,
            text=(
                "Redact on Precinct\n"
                "Treat [ballot style, precinct] combinations with fewer than\n"
                "the minimum ballots as rare, in addition to rare styles alone."
            ),
            variable=self._redact_on_precinct_var,
            justify=tk.LEFT,
            anchor=tk.W,
        ).pack(anchor=tk.W, pady=(5, 12))

        self._no_contest_balancing_var = tk.BooleanVar(
            value=self.config_no_contest_balancing
        )
        tk.Checkbutton(
            frame,
            text="Do not do contest balancing",
            variable=self._no_contest_balancing_var,
            justify=tk.LEFT,
            anchor=tk.W,
        ).pack(anchor=tk.W, pady=(0, 12))

        min_row = tk.Frame(frame)
        min_row.pack(anchor=tk.W, pady=4)
        tk.Label(min_row, text="Minimum Ballots:").pack(side=tk.LEFT)
        self._min_ballots_var = tk.StringVar(value=str(self.config_min_ballots))
        tk.Entry(min_row, textvariable=self._min_ballots_var, width=8).pack(
            side=tk.LEFT, padx=(8, 0)
        )
        tk.Label(
            min_row,
            text="  (minimum ballots required per style or precinct)",
            fg="gray",
            font=("TkDefaultFont", 9),
        ).pack(side=tk.LEFT, padx=(5, 0))

        style_row = tk.Frame(frame)
        style_row.pack(anchor=tk.W, pady=4)
        tk.Label(style_row, text="Style Column:").pack(side=tk.LEFT)
        self._style_col_var = tk.StringVar(
            value="" if self.config_style_col is None else str(self.config_style_col)
        )
        tk.Entry(style_row, textvariable=self._style_col_var, width=8).pack(
            side=tk.LEFT, padx=(8, 0)
        )
        tk.Label(
            style_row,
            text="  (optional: 0-based column index of the named style field)",
            fg="gray",
            font=("TkDefaultFont", 9),
        ).pack(side=tk.LEFT, padx=(5, 0))

        btn_frame = tk.Frame(self.root, padx=10, pady=15)
        btn_frame.pack()
        tk.Button(btn_frame, text="Save", command=self._save_config, width=14).pack(
            side=tk.LEFT, padx=8
        )
        tk.Button(
            btn_frame, text="Cancel", command=self._show_main_menu, width=14
        ).pack(side=tk.LEFT, padx=8)

    def _save_config(self) -> None:
        try:
            min_b = int(self._min_ballots_var.get().strip())
            if min_b < 1:
                raise ValueError
        except ValueError:
            messagebox.showerror("Error", "Minimum Ballots must be a positive integer.")
            return

        style_col_str = self._style_col_var.get().strip()
        style_col: Optional[int]
        if style_col_str:
            try:
                style_col = int(style_col_str)
                if style_col < 0:
                    raise ValueError
            except ValueError:
                messagebox.showerror(
                    "Error",
                    "Style Column must be a non-negative integer or left blank.",
                )
                return
        else:
            style_col = None

        self.config_redact_on_precinct = self._redact_on_precinct_var.get()
        self.config_no_contest_balancing = self._no_contest_balancing_var.get()
        self.config_min_ballots = min_b
        self.config_style_col = style_col
        self._show_main_menu()

    # ------------------------------------------------------------------
    # Produce redacted CVR screen
    # ------------------------------------------------------------------

    def _show_redact_screen(self) -> None:
        self._clear_screen()

        tk.Label(
            self.root, text="Produce Redacted CVR", font=("TkDefaultFont", 14, "bold")
        ).pack(pady=15)

        file_frame = tk.Frame(self.root, padx=15, pady=5)
        file_frame.pack(fill=tk.X)

        self._redact_input_var = tk.StringVar()
        self._redact_output_var = tk.StringVar()
        self._redact_list_var = tk.StringVar()

        self._add_file_row(
            file_frame,
            "Input CVR File:",
            self._redact_input_var,
            lambda: self._browse_open(self._redact_input_var, "Select input CVR file"),
        )
        self._add_file_row(
            file_frame,
            "Output CVR File:",
            self._redact_output_var,
            lambda: self._browse_save(
                self._redact_output_var, "Save redacted CVR as", ".csv"
            ),
        )
        self._add_file_row(
            file_frame,
            "Redacted Ballot List\n(optional):",
            self._redact_list_var,
            lambda: self._browse_save(
                self._redact_list_var, "Save redacted ballot list as", ".txt"
            ),
        )

        self._run_button = tk.Button(
            self.root, text="Run Redaction", command=self._run_redaction, width=18
        )
        self._run_button.pack(pady=8)

        self._status_text = self._add_status_area(self.root, height=10)

        tk.Button(
            self.root, text="Back to Menu", command=self._show_main_menu, width=18
        ).pack(pady=5)

    def _run_redaction(self) -> None:
        input_file = self._redact_input_var.get().strip()
        output_file = self._redact_output_var.get().strip()
        redacted_list = self._redact_list_var.get().strip() or None

        if not input_file:
            messagebox.showerror("Error", "Please select an input CVR file.")
            return
        if not output_file:
            messagebox.showerror("Error", "Please select an output CVR file path.")
            return

        self._run_in_thread(
            execute_redact,
            input_file,
            output_file,
            redacted_list,
            self.config_min_ballots,
            self.config_redact_on_precinct,
            self.config_style_col,
            self.config_no_contest_balancing,
        )

    # ------------------------------------------------------------------
    # Check CVR screen
    # ------------------------------------------------------------------

    def _show_check_screen(self) -> None:
        self._clear_screen()

        tk.Label(
            self.root, text="Check CVR File", font=("TkDefaultFont", 14, "bold")
        ).pack(pady=15)

        file_frame = tk.Frame(self.root, padx=15, pady=5)
        file_frame.pack(fill=tk.X)

        self._check_input_var = tk.StringVar()

        self._add_file_row(
            file_frame,
            "CVR File:",
            self._check_input_var,
            lambda: self._browse_open(
                self._check_input_var, "Select CVR file to check"
            ),
        )

        self._run_button = tk.Button(
            self.root, text="Check File", command=self._run_check, width=18
        )
        self._run_button.pack(pady=8)

        self._status_text = self._add_status_area(self.root, height=14)

        tk.Button(
            self.root, text="Back to Menu", command=self._show_main_menu, width=18
        ).pack(pady=5)

    def _run_check(self) -> None:
        input_file = self._check_input_var.get().strip()

        if not input_file:
            messagebox.showerror("Error", "Please select a CVR file.")
            return

        self._run_in_thread(
            execute_check,
            input_file,
            self.config_min_ballots,
            self.config_redact_on_precinct,
            self.config_style_col,
        )

    # ------------------------------------------------------------------
    # Shared UI helpers
    # ------------------------------------------------------------------

    def _add_file_row(
        self,
        parent: tk.Frame,
        label: str,
        var: tk.StringVar,
        browse_cmd: object,
    ) -> None:
        row = tk.Frame(parent)
        row.pack(fill=tk.X, pady=3)
        tk.Label(row, text=label, width=24, anchor=tk.E, justify=tk.RIGHT).pack(
            side=tk.LEFT
        )
        tk.Entry(row, textvariable=var, width=40).pack(
            side=tk.LEFT, padx=(5, 5), fill=tk.X, expand=True
        )
        tk.Button(row, text="Browse...", command=browse_cmd).pack(side=tk.LEFT)  # type: ignore[arg-type]

    def _add_status_area(self, parent: tk.Misc, height: int) -> tk.Text:
        status_frame = tk.Frame(parent, padx=10, pady=5)
        status_frame.pack(fill=tk.BOTH, expand=True)
        tk.Label(status_frame, text="Status:").pack(anchor=tk.W)

        scroll_frame = tk.Frame(status_frame)
        scroll_frame.pack(fill=tk.BOTH, expand=True)
        scrollbar = tk.Scrollbar(scroll_frame)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        text = tk.Text(
            scroll_frame,
            height=height,
            state=tk.DISABLED,
            yscrollcommand=scrollbar.set,
            wrap=tk.CHAR,
        )
        text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, pady=(5, 5))
        scrollbar.config(command=text.yview)
        return text

    def _browse_open(self, var: tk.StringVar, title: str) -> None:
        path = filedialog.askopenfilename(
            title=title, filetypes=[("CSV files", "*.csv"), ("All files", "*.*")]
        )
        if path:
            var.set(path)

    def _browse_save(self, var: tk.StringVar, title: str, default_ext: str) -> None:
        if default_ext == ".txt":
            filetypes = [("Text files", "*.txt"), ("All files", "*.*")]
        else:
            filetypes = [
                ("CSV files", "*.csv"),
                ("Text files", "*.txt"),
                ("All files", "*.*"),
            ]
        path = filedialog.asksaveasfilename(
            title=title,
            filetypes=filetypes,
            defaultextension=default_ext,
        )
        if path:
            if not path.lower().endswith(default_ext.lower()):
                path = path + default_ext
            var.set(path)

    def _append_status(self, text: str) -> None:
        assert self._status_text is not None
        self._status_text.config(state=tk.NORMAL)
        self._status_text.insert(tk.END, text)
        self._status_text.see(tk.END)
        self._status_text.config(state=tk.DISABLED)

    def _poll_output_queue(self) -> None:
        try:
            while True:
                item = self._output_queue.get_nowait()
                if item is None:
                    if self._run_button is not None:
                        self._run_button.config(state=tk.NORMAL)
                    return
                self._append_status(item + "\n")
        except queue_module.Empty:
            pass
        self.root.after(50, self._poll_output_queue)

    def _run_in_thread(self, func: object, *args: object) -> None:
        assert self._run_button is not None
        assert self._status_text is not None
        self._run_button.config(state=tk.DISABLED)
        self._status_text.config(state=tk.NORMAL)
        self._status_text.delete(1.0, tk.END)
        self._status_text.config(state=tk.DISABLED)
        self._output_queue = queue_module.Queue()

        def worker() -> None:
            old_stdout = sys.stdout
            old_stderr = sys.stderr
            writer = _QueueWriter(self._output_queue)
            sys.stdout = writer  # type: ignore[assignment]
            sys.stderr = writer  # type: ignore[assignment]
            try:
                func(*args)  # type: ignore[operator]
            except Exception as e:
                print(f"\nUnexpected error: {e}")
            finally:
                writer.flush()
                sys.stdout = old_stdout
                sys.stderr = old_stderr
                self._output_queue.put(None)

        threading.Thread(target=worker, daemon=True).start()
        self.root.after(50, self._poll_output_queue)


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------


def main() -> None:
    if len(sys.argv) > 1:
        cli_main()
    else:
        root = tk.Tk()
        app = CvrAnonymizerApp(root)
        root.mainloop()


if __name__ == "__main__":
    main()
