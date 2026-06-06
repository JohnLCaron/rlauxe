# SHANGRLA vs rlauxe: Comparison

*This review was originally produced by a GitHub Copilot agent session in the nealmcb/SHANGRLA repository and is preserved here for reference.*

---

## Overview

| | SHANGRLA (nealmcb/SHANGRLA) | rlauxe (JohnLCaron/rlauxe) |
|---|---|---|
| Language | Python | Kotlin (JVM) |
| Status | Research reference implementation | "Work in Progress" production-oriented library |
| Build system | requirements.txt / no formal build | Gradle (multi-module) |
| License | AGPL-3.0 | (uses AGPL via RAIRE-Java dependency) |

---

## Features Implemented

### Audit types

- **SHANGRLA**: Card-Level Comparison Audit (CLCA), Polling. No OneAudit.
- **rlauxe**: CLCA, OneAudit (for partial/non-matchable CVR pools; Boulder and San Francisco cases), Polling. OneAudit is a significant addition SHANGRLA lacks.

### Contest types / Social choice functions

- **SHANGRLA**: Plurality, Supermajority, IRV (via external RAIRE assertion generator).
- **rlauxe**: Plurality, IRV (via embedded RAIRE-Java library), D'Hondt (proportional representation — a major addition absent from SHANGRLA).

### Statistical methods / Risk functions

- **SHANGRLA**: Kaplan-Markov, Kaplan-Wald, Kaplan-Kolmogorov, Wald SPRT (with/without replacement), Kaplan-Martingale. These are the older generation of methods.
- **rlauxe**: AlphaMart (with truncated-shrinkage estimation for polling), BettingMart + GeneralAdaptiveBetting (for CLCA/OneAudit). These are the newer, more optimal betting-based methods from Stark et al.'s recent papers. SHANGRLA's older methods are not included (though rlauxe has a `port/` test package that cross-validates against SHANGRLA).

### Workflow completeness

- **SHANGRLA**: A math/statistics library. Provides the primitives (assorters, CVR structures, p-value computation, sample selection), connected via Jupyter notebooks. No persistence, no commitment model, no verification layer.
- **rlauxe**: Complete end-to-end workflow: election creation → audit commitment (signed, publishable) → audit round management → MVR entry → risk computation → next-round estimation → independent verification. Has persistence/serialization, a CLI interface, and a `verify` module for third-party audit checking.

### Sampling

- **SHANGRLA**: Basic sample-by-index utility; no explicit multi-contest consistent sampling.
- **rlauxe**: Consistent sampling across multiple contests (reads CardManifest in PRNG-sorted order); explicit multi-round estimation via simulation (runs `nsimTrials` simulated audits to estimate batch sizes at a target percentile).

### CVR format support

- **SHANGRLA**: Generic dict, RAIRE CSV format, Dominion CVR export (via `dominion_tools.py`), SUITE format (via `suite_tools.py`).
- **rlauxe**: Proprietary JSON-based AuditableCard/CVR model; RAIRE via raire-java integration; OneAudit pool subtotals.

### Phantom/missing ballot handling

Both support phantom CVRs and MVRs with appropriate assorter treatment. rlauxe has deeper analysis of phantom effects on sample size (documented in `ClcaErrors.md`).

---

## Implementation Quality

### SHANGRLA

- Compact (~3,200 lines of Python across 4 files), research-grade code.
- Core classes (`Assertion`, `Assorter`, `CVR`, `TestNonnegMean`) are clean, well-documented with docstrings.
- Some quirks: tests are ad-hoc functions at the bottom of `assertion_audit_utils.py`; `new_sample_size()` references an undefined `prng` variable (a latent bug); some methods have `# [FIX ME]` placeholders.
- Side-effect-heavy functions (e.g., `find_margins`, `find_p_values`, `summarize_status`) with printing mixed into logic — not ideal for library use.
- No type annotations.
- Older coding style: many getter/setter methods that Pythonistas would replace with properties.

### rlauxe

- Much larger (~tens of thousands of lines of Kotlin across a structured module hierarchy: `audit`, `betting`, `cli`, `core`, `dhondt`, `estimate`, `irv`, `oneaudit`, `persist`, `util`, `verify`, `workflow`).
- Idiomatic Kotlin: data classes, sealed types, extension functions, coroutine-friendly structure.
- Clear separation of concerns between layers.
- Has a CLI (`cli` package) for command-line use.
- Explicit audit commitment model with digital signing intent.
- More mature engineering overall, though still self-labeled "WORK IN PROGRESS."

---

## Tests

### SHANGRLA

- Tests are standalone `test_*` functions at the bottom of `assertion_audit_utils.py`, run via `if __name__ == "__main__"`.
- No pytest integration, no test runner, no CI.
- Covers: plurality assorters, supermajority assorter, RCV/IRV assorters, CVR parsing (dict, RAIRE), overstatement computation, Kaplan-Markov, Kaplan-Wald, Kaplan-Martingale.
- The Jupyter notebooks serve as integration-level tests but are not automated.
- `test_assorter_mean()` has a `pass # [FIX ME]` — it does nothing.
- No mock/fixture infrastructure.

### rlauxe

- Full JUnit 5 test suite, organized by module matching production source (`audit`, `betting`, `cli`, `core`, `corla`, `dhondt`, `estimate`, `irv`, `oneaudit`, `persist`, `port`, `shangrla`, `strata`, `util`, `verify`, `workflow`).
- `testFixtures/` directory for shared test helpers.
- A `shangrla/` test package that explicitly cross-validates rlauxe results against SHANGRLA's Python implementation — important for correctness validation.
- A `port/` test package appears to contain ports of SHANGRLA's original tests.
- Also includes Python tests (`core/src/test/python`) for cross-language validation.
- Test data files in `core/src/test/data` and `core/src/test/resources`.
- Much wider coverage: simulation tests for sample estimation, betting functions, IRV assertions, OneAudit pools, D'Hondt, verification logic.
- CI is technically disabled (`.github_disable` directory), but the test infrastructure is there.

---

## Code Coverage

Neither repository has formal code coverage reporting (no `coverage.py`, no JaCoCo configuration visible).

- **SHANGRLA**: Coverage is low by modern standards. Only `assertion_audit_utils.py` has tests; `suite_tools.py` (~932 lines), `dominion_tools.py`, and `IRVVisualisationUtils.py` have no associated automated tests. The test functions themselves skip some cases (`test_assorter_mean` is empty).
- **rlauxe**: No coverage config found, but the test suite is structured to mirror every production module, and the cross-validation with SHANGRLA provides additional assurance on core statistical logic. Coverage is likely substantially higher, though not formally measured.

---

## Documentation

### SHANGRLA

- **README**: Step-by-step guide to reproducing the SF 2019 RLA pilot.
- **UsersGuide.md**: General usage guide.
- **Jupyter notebooks**: Executable worked examples (SF 2019 DA contest, RAIRE parsing/visualization).
- **Inline docstrings**: Thorough on core mathematical functions.

### rlauxe

- 18 markdown docs covering: algorithm design (`AlphaMart.md`, `BettingRiskFunctions.md`, `GeneralizedAdaptiveBetting.md`), audit spec (`RlauxeSpec.md`, 45 KB), case studies (`CaseStudies.md`), error analysis (`ClcaErrors.md`), D'Hondt (`Dhondt.md`, 34 KB), OneAudit use cases, sample populations, verification spec, attacks, developer guide, audit record format.
- Extensive plots with interactive HTML versions hosted on GitHub Pages.
- Much more comprehensive documentation, clearly aimed at implementors and auditors rather than just researchers.

---

## Summary Table

| Dimension | SHANGRLA | rlauxe |
|---|---|---|
| Audit types | CLCA, Polling | CLCA, OneAudit, Polling |
| Contest types | Plurality, Supermajority, IRV | Plurality, IRV, D'Hondt |
| Statistical methods | Kaplan family, Wald SPRT | AlphaMart, BettingMart (newer/more optimal) |
| Workflow completeness | Primitives only | End-to-end incl. verification |
| Consistent sampling | No | Yes |
| Multi-round estimation | Basic | Simulation-based |
| Tests | Ad-hoc functions, not automated | Full JUnit suite with cross-validation |
| Coverage | Low (partial, informal) | Better structured (no metrics) |
| Documentation | Good for research use | Extensive for implementors |
| Code size | ~3,200 lines | Substantially larger (10,000s of lines) |
| Production readiness | Research prototype | More mature, though still WIP |

**Bottom line:** SHANGRLA is the authoritative Python research prototype from Stark et al., excellent for understanding the mathematical foundations and for reproducing specific pilots. rlauxe is a more ambitious, production-oriented Kotlin reimplementation that adds OneAudit, D'Hondt, a complete audit workflow, and the newer betting-based risk functions — at the cost of being a much larger, more complex, still-evolving codebase. The two are complementary: rlauxe explicitly cross-validates against SHANGRLA in its test suite, treating SHANGRLA as the mathematical ground truth.

---

## Deep Dives

### Audit Commitment (Signed, Publishable)

#### What the problem is solving

An RLA's statistical guarantee only holds if the evidence being tested was committed to *before* the random seed was drawn. If an election authority could rearrange CVRs, adjust vote totals, or swap which ballots appear in a sample after seeing the seed, they could manipulate the audit to pass even when the election was wrong. Commitment is the mechanism that locks evidence in place and makes the process verifiable by anyone.

#### SHANGRLA's approach (none)

SHANGRLA has no commitment model at all. It's purely a math library — you pass it data and it returns p-values. It trusts that whoever is calling it has provided honest inputs. This is fine for a research tool and for pilots where the auditors are themselves trustworthy, but it doesn't give the public any independent assurance.

#### rlauxe's two-stage commitment protocol

rlauxe formalizes a two-stage commit sequence:

**Stage 1 — Election Commitment (before seed is drawn):** The election authority publishes and digitally signs:

- `cardManifest.csv` — the full list of physical ballot cards with unique identifiers; defines the entire population that can be sampled
- `contests.json` — the contest descriptions, reported vote totals, and Nc (the trusted bound on valid cards per contest)
- `electionInfo.json` — election-level metadata
- `cardPools.csv` / `cardStyles.json` — OneAudit pool descriptions and ballot style maps, if applicable
- Round 1 configuration and estimated sample sizes

Once signed and published, these cannot be changed. Specifically, the CardManifest can't be altered to add, remove, or swap ballot identifiers. The reported vote totals can't be adjusted. Any verifier can check these against their own copy.

**Stage 2 — Audit Commitment (immediately after seed is drawn):**

- The random seed is drawn in a publicly observable way (e.g., a dice roll at a public meeting, or a beacon value)
- The seed is published and becomes part of the signed audit record
- The PRNG (using the seed) assigns a pseudorandom number (PRN) to every card in the manifest in canonical order
- The cards are sorted by PRN and written to `sortedCards.csv`
- This sorted order is committed before any ballots are examined

The key point: the authority cannot choose which specific ballots appear in the sample. They can only choose *how many* ballots per contest to request in each round. The ballots themselves are determined entirely by the committed PRNG order — the first N cards in the sorted manifest that contain contest C.

#### Round-level commitments

Each audit round adds three sub-commits:

1. **Before sampling**: `auditRoundConfigX.json` (which contests, how many samples) + `samplePrnsX.json` (the specific PRNs chosen) — locked before anyone touches paper.
2. **After hand audit**: `sampleMvrsX.csv` (the human-read ballots) + `sampleCardsX.csv` (the matched manifest cards) — locked before the audit algorithm runs.
3. **After algorithm runs**: `auditStateX.json` (the p-values and which contests passed) — the public result.

#### Independent verification

Because every file is signed and published, anyone (opposition parties, journalists, academic researchers) can:

- Recompute the PRN sequence from the published seed using the specified PRNG and verify `sortedCards.csv`
- Verify that `samplePrnsX.json` contains the smallest PRNs for each contest (i.e., no cherry-picking)
- Re-run the p-value calculation from the committed MVRs and CVRs
- Confirm the audit conclusion is correct

The `VerifierSpec.md` formalizes exactly this: the verifier confirms (a) ballot IDs are unique, (b) PRNs match the PRNG output from the seed, (c) sampled ballots are the ones with the smallest PRNs for each contest, (d) p-value calculations are reproducible.

SHANGRLA lacks all of this. It's up to whoever runs the audit to establish commitment externally (e.g., by publishing files manually and having them hash-verified). Several real-world deployments have had to build this scaffolding outside SHANGRLA.

---

### Multi-Round Estimation: Practical Uses Beyond Research

#### What the problem is

In a real audit, you can't sample one ballot at a time: auditors need to physically locate ballots in storage boxes, transport them, hand them to adjudicators, enter results. That's expensive per-trip. So audits run in rounds — you commit to a batch size, go get those ballots, audit them all, then decide whether you're done or need more.

The estimation problem is: how big should the batch be? Underestimate and you need more rounds (more logistics). Overestimate and you hand-audit more ballots than needed (more cost, more time, more auditor fatigue). The "right" answer depends on the true error rate between CVRs and hand-read ballots, which you don't know before the audit — you only learn it as you go.

#### Why it matters in real operations (not just research)

**Logistics and scheduling:** Audit rounds have fixed overhead. In Colorado, batches of ballots must be pulled from county vaults, transported to a central location, and auditors must be scheduled days in advance. An underestimated round means a second trip to the vault. An overestimated round means paying auditors for ballot-reading they didn't need to do. The cost difference between 1 round vs. 3 rounds for a close contest can be tens of thousands of dollars.

**Close-margin contests are highly sensitive:** For a 1% margin contest (which is common in down-ballot races), rlauxe's data shows that the 1-sigma sample-size range might span from ~500 to ~1,200 ballots. If you commit to 500 and the random sample happened to hit slightly more errors than average, you'll need a second round. If you plan for 1,200 to "be safe," you audit 700 extra ballots for no benefit. The estimation algorithm attempts to hit the 50th percentile on round 1 (accept ~50% chance of needing another round) and the 80th on subsequent rounds.

**Multi-contest elections:** When 10–30 contests span a single ballot, different contests reach their risk limits at different times. Some contests with wide margins might satisfy risk after 50 ballots; a tight IRV contest might need 5,000. The estimation must handle each contest separately but consistent sampling means you're pulling one set of physical ballots that serves all contests simultaneously. Getting the batch size wrong for any single contest cascades into the physical logistics for all the others.

**Updating error rates between rounds:** After round 1, you have measured data on the actual CVR-vs-MVR error rate. The rlauxe strategy uses round-1 errors to re-estimate for round 2, but (counterintuitively, from their empirical data) using 0% simulated errors for round 1 is actually optimal for minimizing total extra ballots — because the variance is so large that you're better off starting conservative, measuring actual errors, then recalculating. The practical implication: auditors should not be told "enter your expected error rate" on round 1; they should just use the optimistic default.

**Percentile choice is a policy decision:** The estimation asks: what percentile of the simulated sample-size distribution should we use? The 50th percentile means the audit will need a second round about half the time. The 90th percentile means it almost always finishes in one round, but uses more ballots. This is not a statistical question — it's a resource and schedule management decision that election officials need to make based on their operational constraints (how expensive are additional rounds vs. extra ballot-reading?). rlauxe makes this explicit and configurable; SHANGRLA has no such machinery.

**Legal/political time pressure:** Many jurisdictions have certification deadlines. If the audit of a close contest isn't complete by the deadline, there can be real consequences — the audit is abandoned, or the result is certified without conclusive audit evidence. Accurate multi-round estimation lets the canvassing board make an informed decision early: "based on round 1 errors, we need approximately 2 more rounds; our deadline is in 8 days; here is whether that's feasible."

#### Concrete rlauxe behavior

- **Round 1**: simulate assuming no errors (0% fuzz), take the 50th percentile → commit to a batch size
- If round 1 doesn't satisfy all contests, measure the actual error rates from round 1 MVRs
- **Round 2+**: simulate using measured error rates, take the 80th percentile → commit to a larger batch size
- Each round, the simulation runs thousands of trials on the actual cards already selected (not a synthetic population) — so phantom cards and OneAudit pool cards are simulated accurately based on what's already in the sample

#### What SHANGRLA does

`new_sample_size()` in `assertion_audit_utils.py` has a rudimentary extrapolation: given the current sequence of observed overstatement values, it bootstraps by sampling from the already-observed values to project how many more would be needed. This is useful for understanding how an ongoing audit is tracking, but it: (a) references an undefined `prng` variable (a bug), (b) has no simulation infrastructure, (c) doesn't account for multi-contest consistent sampling, and (d) produces no percentile-based planning estimates.
