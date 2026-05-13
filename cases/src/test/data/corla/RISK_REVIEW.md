# Risk Calculation Review: Python vs Kotlin Implementations

**Date:** 2026-05-12  
**Context:** Comparing John Caron's Kotlin risk results (`output/risks-via-kotlin.csv`) against
the Python implementation (`src/auditcenter_analyze/calculate_opportunistic_risk.py`), for the
2024 Colorado General RLA, round 3.  Both implementations assume **zero errors** for this
initial analysis.  Both use the Kaplan-Markov comparison audit framework with γ = 1.03905 and
a 3% risk limit.

This document also synthesises prior notes from `docs/OPPORTUNISTIC_RISK_FINAL.md`,
`docs/BALLOT_CARD_COUNT_EXPLAINED.md`, `docs/VOTE_BASED_ESTIMATION_CASE_STUDY.md`, and the
October 2025 cursor chat transcripts, which established many of the correct algorithmic
conclusions that the code does not yet fully implement.

---

## 1. What John's CSV Contains

**File:** `output/risks-via-kotlin.csv`  
**Rows:** 609 contests (65 targeted, 544 opportunistic), derived from the canonical contest list
(`2024GeneralCanonicalList.csv`) plus two added Douglas County contests and one removed
(La Plata County Surveyor).

Key fields:

| Field            | Description                                                                                                                                   |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `npop`           | Total ballot cards in the sampling jurisdiction (county total or statewide) — denominator for diluted margin                                  |
| `nc`             | Trusted upper bound on contest ballot cards (the contest-specific count)                                                                      |
| `voteDiff`       | Winner minus loser vote count (smallest assertion)                                                                                            |
| `reportedMargin` | `voteDiff / nc`                                                                                                                               |
| `dilutedMargin`  | `voteDiff / npop`                                                                                                                             |
| `estMvrs`        | CORLA's estimated sample size for this contest                                                                                                |
| `haveMvrs`       | Actual MVR count from `round3/contestComparison.csv`: unique ballot IDs per county that never appear with `audit_reason = STATE_WIDE_CONTEST` |
| `estRisk`        | KM p-value using `haveMvrs` as n and `dilutedMargin`                                                                                          |

John's notes:
- Point 3: statewide contests also use the "minimum county sampling rate" (same algorithm as
  multi-county), because that came out better than the statewide-only sampling rate.  "Not yet
  sure how to combine the two."
- Point 4: `haveMvrs` is generally a bit higher than `audited_sample_count` in `contest.csv`
  — CORLA appears to have added extra ballots to avoid an additional round.

JOHN COMMENT 

my estRisk:

````
// marginUpper = margin/upper
fun estRiskFromMargin(bet:Double, marginUpper:Double, nsamples: Int): Double {
    val noerror = 1.0 / (2.0 - marginUpper)
    val payoff = 1.0 + bet * (noerror - 0.5)
    val payoffn = payoff.pow(nsamples.toDouble())
    val result =  1.0 / payoffn
    return result
}
````

src/auditcenter_analyze/calculate_opportunistic_risk.py:

````
      GAMMA = 1.03905
      RISK_LIMIT = 0.03

      risk = rlacalc.KM_P_value(n=n, gamma=GAMMA, margin=diluted_margin, o1=o1, o2=o2, u1=u1, u2=u2)

      def KM_P_value(n=95, gamma=1.03905, margin=0.05, o1=0, o2=0, u1=0, u2=0):
          """Return P-values (risk level achieved?) for given sample size n and discrepancy counts.
          n: sample size
          margin: diluted margin; 
          From https://github.com/pbstark/S157F17/blob/master/audit.ipynb
      
          >>> margin = (354040 - 337589)/(354040+337589+33234) # New Hampshire 2016
          >>> KM_P_value(200, 1.03905, margin, 1, 0, 0, 0)
          0.21438135077031842
          """
      
          return((1 - margin/(2*gamma))**n *\
                 (1 - 1/(2*gamma))**(-o1) *\
                 (1 - 1/gamma)**(-o2) *\
                 (1 + 1/(2*gamma))**(-u1) *\
                 (1 + 1/gamma)**(-u2))
````    

suppose o1=o2=u1=u2 = 0

then x **( 0) == 1, and we are left with

   (1 - margin/(2*gamma))**n 

   (1 - margin/(2*gamma)) is 1/payoff

close but not exact (see test_KMpayoff()), betting is 

val bet = 2/1.03905
val margin = .164003
         KMpayoff = 1.0856816556318722
   betting payoff = 1.085969297609816
             ratio = 1.0002649413632916

gets closer as margin gets smaller:

val margin = .01
KMpayoff = 1.0048353561239785
  payoff = 1.0048362693115256
   ratio = 1.0000009087932082

END COMMENT

---

## 2. What the Python Implementation Contains

**Script:** `src/auditcenter_analyze/calculate_opportunistic_risk.py`  
**Saved output:** `tests/baseline_output.txt`  
**Contests processed:** 725 total (2 state-wide targeted, 63 county-wide targeted, 660
opportunistic) — sourced from `round3/contest.csv` rather than the canonical list, so coverage
differs from John's.

The algorithm:
1. Load county ballot manifests → `ballot_card_count` per county.
2. For each contest, count observed ballots with the contest in `contestComparison.csv` per
   county.
3. Compute per-county sampling rate = `observed_with_contest / manifest_count`.
4. Find the minimum county sampling rate and downsample all other counties to that rate.
5. `n` = total contest-specific ballots in the downsampled uniform sample.
6. `diluted_margin = min_margin / contest_ballot_card_count` ← **bug, see §3.1**.
7. Risk = `rlacalc.KM_P_value(n, gamma, diluted_margin)`.

Steps 1–5 were established as correct in `OPPORTUNISTIC_RISK_FINAL.md` (October 24, 2025).
The bug entered in step 6, where the wrong field from `contest.csv` was used.

---

## 3. Algorithmic Differences

### 3.1 Diluted margin denominator — Python has a bug

The Python code computes:

```python
diluted_margin = min_margin / contest_ballot_card_count   # WRONG
```

but `contest_ballot_card_count` (column 6 of `contest.csv`, John's `nc`) is the
**contest-specific** ballot count, not the total jurisdiction population.  The correct
denominator for the diluted margin under uniform sampling is `ballot_card_count` (column 5 of
`contest.csv`, John's `npop`) — the total ballot cards in the jurisdiction from which the
uniform sample is drawn.

This was established conclusively in the October 2025 cursor chat and documented in
`BALLOT_CARD_COUNT_EXPLAINED.md`: the CORLA software draws ballots from the **full county
manifest** regardless of which contest it is targeting.  A ballot selected for Adams County
Commissioner – District 5 is drawn from all 468,858 Adams cards, not just the 236,872 that
happen to be in District 5.  Therefore npop (the full manifest count) is the correct
denominator everywhere.

For a county-wide contest (nc = npop) the two fields are equal and the bug is invisible.  For
a sub-county district race, nc can be roughly half of npop, making Python's diluted margin ~2×
too large and the computed risk far too low.

**Fix (one line in `calculate_opportunistic_risk.py` around line 364):**
```python
ballot_card_count = int(contest_data["ballot_card_count"])   # npop — total jurisdiction
diluted_margin = min_margin / ballot_card_count
```

JOHN COMMENT 5/13/26 calculate_opportunistic_risk.py still has diluted_margin = min_margin / contest_ballot_card_count


#### Why the "doubly diluted" sampling rate makes this work without needing per-county nc

A concern sometimes raised is that when a contest appears on different fractions of ballots in
different counties (e.g., a district race covering 50% of one county and 80% of another), the
minimum-rate algorithm might need to know those per-county fractions.  It does not, and here
is why.

Define:
- `s_A` = true uniform sampling rate in county A (fraction of its cards drawn)
- `p_A` = contest prevalence in county A = `nc_A / npop_A`
- `observed_A` = contest ballots seen in county A ≈ `s_A × p_A × npop_A`

The rate we can compute from the data is:
```
rate_A = observed_A / npop_A  ≈  s_A × p_A
```

This is a product of sampling intensity and contest prevalence — two unknowns collapsed into
one observable.  We cannot disentangle them without CVR data.  But we do not need to, because
after downsampling all counties to `min_rate`:

```
n_total = min_rate × npop            (sum across all counties at min_rate)
         ≈ s_min × p_min × npop_min_county + s_min × p_other × npop_other + …
         = s_min × (nc_A + nc_B + …)
         = s_min × nc_total
```

So `n_total ≈ s_min × nc` — the expected contest ballots in a true uniform sample drawn at
rate `s_min`.  This is exactly the right n for the KM formula.  Different contest prevalences
across counties are automatically absorbed into the diluted rate and require no separate
treatment.

### 3.2 Definition of n — Kotlin formula confirmed by cross-checking results

| Implementation    | n used in KM formula                                                                                                                                |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **Python**        | Contest-specific ballots in the downsampled uniform sample                                                                                          |
| **John's Kotlin** | `haveMvrs` = unique non-statewide county ballot IDs in `contestComparison.csv` (all ballots drawn for the county, not only those with this contest) |

We can confirm exactly what the Kotlin formula does by predicting John's reported risks from
his own inputs.  If the Kotlin uses the standard closed-form `KM(n=haveMvrs, dm=min_margin/npop)`,
the predictions should match.  They do, across every case checked:

| Contest                       | nJ  | nPy | dm (npop) | KM(nJ, npop) | John reported | KM(nPy, nc) | Python reported | Correct% |
|-------------------------------|-----|----:|----------:|-------------:|--------------:|------------:|----------------:|---------:|
| Adams Cmr District 1 (opp)    | 214 | 119 |     2.79% |     **5.5%** |          5.5% |        4.1% |            4.1% |    20.0% |
| Adams Cmr District 5 (tgt)    | 214 | 119 |     3.79% |    **1.95%** |          1.9% |        1.3% |            1.3% |    11.2% |
| Arapahoe Cmr District 1 (opp) | 52  |  19 |     3.26% |    **43.9%** |         43.9% |       26.0% |           26.0% |    74.1% |
| 17th Judicial District (opp)  | 236 | 123 |     7.27% |    **0.02%** |           ~0% |       0.03% |           0.03% |    1.25% |
| Amendment 80 (opp)            | 706 | 372 |     0.88% |     **5.0%** |          5.0% |           — |          18.7%* |    20.5% |
| Chaffee Cmr District 2 (tgt)  | 89  | 89  |     8.34% |    **2.6%**  |        2.6%   |      2.6%   |          2.6%   |   2.6%   |

\* Amendment 80 Python prediction differs because contest.csv has a different `contest_ballot_card_count` than John's nc; Chaffee has nc = npop so all three columns agree.

**Conclusion:** The Kotlin uses the standard closed-form KM formula — `(1 − dm/(2γ))^n` — with
`n = haveMvrs` (total non-statewide county ballot IDs) and `dm = min_margin / npop`.  It does
**not** implement the "explicit factor of 1.0 per non-contest draw" variant.  It simply passes
the larger n directly to the formula, which gives those non-contest ballots unearned credit and
understates the risk.

In the KM test martingale, ballots that do **not** contain the contest contribute a factor of
exactly 1.0 — they carry no information about the contest and should not count toward n.
Therefore Python's definition (contest-specific ballots only) is mathematically correct for
`rlacalc.KM_P_value`, and John's larger n gives a result that is optimistic by a factor of
`(1 − dm/(2γ))^(nJ − nPy)` — the spurious contribution of the extra non-contest draws.

**Note on John's `haveMvrs` count:** For Adams County, `haveMvrs = 214`, not 231.  The 17
ballots excluded are those that appear in `contestComparison.csv` under **both**
`STATE_WIDE_CONTEST` and county-level reasons.  Python counts all 231 unique ballot IDs for
Adams total, and 119 with District 1 specifically.  John's choice to exclude statewide-mixed
ballots is defensible (those ballots were selected for statewide reasons, not county-uniform
reasons) but should be agreed upon explicitly.

### 3.3 Multi-sheet ballots — already handled

Some Colorado counties issue multiple ballot cards (sheets) per voter.  The concern is that
such counties see "a higher proportion of selections than by population."  This is real but
already handled: `ballot_card_count` in the ballot manifests is the count of **physical
ballot sheets**, not voters.  All denominators — manifest totals for sampling rates and npop
for the diluted margin — are consistently in units of ballot cards.  A voter who receives two
sheets counts as 2 in npop.  No correction is needed as long as everything stays in
card units, which it does.

### 3.4 Contest coverage

| | Targeted | Opportunistic | Total |
|---|---|---|---|
| Python (`contest.csv`) | 65 | 660 | 725 (+ 3 skipped/Moffat) |
| John's Kotlin (canonical list) | 65 | 544 | 609 |

The ~116-contest gap is the difference between the canonical list and everything in
`contest.csv`.  The canonical list is the more authoritative source for contest identity;
`contest.csv` may include entries for contests that were never truly active.

---

## 4. Statewide Contests: Minimum County Rate vs Statewide Rate

John uses the minimum county sampling rate for statewide contests and notes it "came out
better" than the statewide-only rate.  This requires some unpacking.

The minimum county rate is always ≤ the statewide average rate (minimum ≤ average), so it
gives *fewer* effective samples and *higher* (more conservative) risk — not a more favorable
outcome in the sense of more contests passing.  "Better" most likely means more defensible:
using county-level sampling avoids the CVR-mapping problem that arises when trying to use
statewide targeted samples directly (the CORLA statewide selection domain is nc, not npop,
which requires knowing which specific cards in each county carry the contest).

**The resolved answer:** No separate "statewide rate" needs to be combined with the minimum
county rate.  The minimum-county-rate algorithm applied uniformly — using all examined ballots
(statewide-targeted and county-targeted alike) to compute the per-county rate, then taking the
minimum across all involved counties — is the correct and sufficient approach for every contest
type.  It naturally incorporates the full examined sample without needing CVR mapping.  There
is no combination to perform; there is one algorithm.

Concretely, for a statewide contest spanning all 63 counties:
1. For each county: `rate_county = examined_in_county / ballot_card_count_county` (all
   examined ballots, regardless of why they were selected).
2. `min_rate = min(rate_county)` across all 63 counties.
3. `n = min_rate × npop_statewide`.
4. `diluted_margin = min_margin / npop_statewide`.

This is identical to the multi-county algorithm already implemented.  No special statewide
code path is needed.

---

## 5. Numerical Impact

The two errors partially cancel — Python uses a larger (wrong) diluted margin but a smaller
(correct) n — but the diluted margin error dominates for district races and statewide contests
where nc << npop.

### Example: Adams County Commissioner – District 1 (opportunistic)
- County total (`ballot_card_count` / npop) = 468,858
- Contest cards (`contest_ballot_card_count` / nc) = 236,872  ← district, ~half the county
- `min_margin` = 13,085

| Approach | n | diluted margin | computed risk |
|---|---|---|---|
| **John's Kotlin** | 214 (all county non-statewide) | 2.79% (npop) | **5.5%** |
| **Python (current — buggy)** | 119 (contest only) | 5.52% (nc) | **4.1%** |
| **Correct (Python n, fixed margin)** | 119 (contest only) | 2.79% (npop) | **~20%** |

### Example: Adams County Commissioner – District 5 (targeted, currently passing)
- `min_margin` = 17,761 | npop = 468,858 | nc = 236,872

| Approach | n | diluted margin | computed risk |
|---|---|---|---|
| **John's Kotlin** | 214 | 3.79% | **1.95%** ✓ |
| **Python (current — buggy)** | 119 | 7.50% | **0.08%** ✓ (false precision) |
| **Correct (Python n, fixed margin)** | 193 | 3.79% | **~2.87%** ✓ |

### Example: Arapahoe County Commissioner – District 1 (opportunistic)
- npop = 351,540 | nc = 80,484 (district, ~23% of county) | `min_margin` = 11,461

| Approach | n | diluted margin | computed risk |
|---|---|---|---|
| **John's Kotlin** | 52 (all county) | 3.26% | **43.9%** |
| **Python (current — buggy)** | ~11 (contest only) | 14.24% (nc) | **~46%** |
| **Correct (Python n, fixed margin)** | ~11 (contest only) | 3.26% (npop) | **~84%** |

### Statewide contests (npop ≠ nc)
For Amendment 80 (62 counties), npop = 4,763,790 and nc = 3,235,954.  John's
`dilutedMargin` = 0.88%, Python's = 1.30%.  At n = 372 (statewide minimum-rate sample), the
correct margin gives risk ≈ **20.5%** (well above limit); Python's inflated margin gives
**18.7%** — both fail, but Python understates the severity.  John's n = 706 gives **5.0%**,
which is much closer to the limit and likely overstates the sample's effectiveness.

---

## 6. Python Baseline Output Summary

From `tests/baseline_output.txt` — run of `calculate_opportunistic_risk.py` with the current
(buggy) diluted margin, zero errors assumed:

```
Statewide ballot cards:  4,740,167
Total MVR ballots:       4,897 across 63 counties

Contests processed: 725
  State-wide targeted:   2
  County-wide targeted:  63
  Opportunistic:         660

TARGETED (64 total):
  Pass (≤3%):  61/64
  FAIL:
    Dove Creek Ambulance District Ballot Issue 6A    n=117  risk=4.71%
    Routt County Commissioner - District 1           n= 77  risk=4.14%
    Sedgwick County Commissioner - District 3        n= 84  risk=3.05%

OPPORTUNISTIC (658 total, 3 contests skipped/Moffat data missing):
  Pass (≤3%):  223/658  (34%)
  Fail (>3%):  435/658  (66%)
```

Selected opportunistic results to compare with John's CSV:

```
✓ 17th Judicial District Ballot Question 7B         n=123  risk=0.027%
✗ Adams 12 Five Star Schools Ballot Issue 5D        n= 51  risk=6.60%
✗ Adams 12 Five Star Schools Ballot Issue 5E        n= 51  risk=10.58%
✗ Adams County Commissioner - District 1            n=119  risk=4.05%
✓ Adams County Commissioner - District 2            n=119  risk=0.00%
✓ Amendment 79 (CONSTITUTIONAL)                     n=372  risk=0.00%
✗ Amendment 80 (CONSTITUTIONAL)                     n=372  risk=18.69%
✓ Arapahoe County Ballot Issue 1A                   n= 69  risk=0.00%
✗ Arapahoe County Commissioner - District 1         n= 19  risk=25.96%
✗ Arapahoe County Commissioner - District 3         n= 21  risk=77.88%
```

The full per-contest listing (867 lines) is in `tests/baseline_output.txt`.

---

## 7. Open Questions

1. **Kotlin KM formula — now confirmed (§3.2).**  Cross-checking John's reported risks against
   `KM(haveMvrs, min_margin/npop)` reproduces his numbers exactly.  The Kotlin uses the
   standard closed-form KM with `n = haveMvrs` (all county non-statewide ballot IDs), which
   gives non-contest ballots unearned credit.  The correct n is contest-specific ballots only
   (Python's approach).  The Kotlin should be updated to use `n = observed_contest_ballots`
   after downsampling to minimum county rate.

2. **Contest list discrepancy (~116 contests).**  Python processes everything in
   `round3/contest.csv`; John uses the canonical list.  We should agree on the authoritative
   source and filter accordingly.

3. **Non-zero errors.**  Both implementations currently assume zero discrepancies.  The Python
   code already reads discrepancy counts from `contest.csv` for targeted contests; extending
   this to opportunistic contests requires determining winner/loser per choice for each contest.

4. **Statewide-selected ballot IDs in county samples.**  John excludes the 17 Adams ballots
   that appear under both `STATE_WIDE_CONTEST` and county reasons from `haveMvrs`.  Python
   counts all contest ballots regardless of selection reason.  Those 17 ballots were physically
   audited and their contest entries are valid comparisons; the question is whether their
   inclusion breaks the "uniform sample from county ballots" claim.

5. **Zero-ballot counties.**  The current Python code inserts a fake "1 ballot" when a county
   has the contest but zero examined contest ballots; `VOTE_BASED_ESTIMATION_CASE_STUDY.md`
   shows that using vote totals from `tabulateCounty.csv` as a prevalence proxy is more
   accurate and still conservative.  The fake-ballot approach is safe in practice because it
   always downsamples to zero, but the vote-based rate gives a better estimate of the true
   minimum county rate.

---

## 8. Recommended Next Steps

1. **Fix the one-line diluted margin bug** in `calculate_opportunistic_risk.py`:
   use `ballot_card_count` (npop) instead of `contest_ballot_card_count` (nc).

2. **Fix the Kotlin n** to use contest-specific ballots after downsampling rather than all
   county non-statewide ballot IDs.  The formula `KM(haveMvrs, dm_npop)` is confirmed to be
   what the Kotlin currently computes; `haveMvrs` should be replaced with the count of
   contest-specific ballot comparisons at the minimum county rate.

3. **Re-run both implementations after their respective fixes** and compare side-by-side.
   After both fixes, results should agree closely (differences only from integer truncation
   in downsampling and the statewide-mixed ballot exclusion question).

4. **Agree on the contest list** — canonical list vs `contest.csv` — before further
   cross-implementation comparison.
