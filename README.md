**rlauxe ("r-lux")**

WORK IN PROGRESS
_last changed: 04/11/2026_

A library for [Risk Limiting Audits](https://en.wikipedia.org/wiki/Risk-limiting_audit) (RLA), based on Philip Stark's SHANGRLA framework and related code.
The Rlauxe library is an independent implementation of the SHANGRLA framework, based on the
[published papers](#reference-papers) of Stark et al.

The [SHANGRLA python library](https://github.com/pbstark/SHANGRLA) is the work of Philip Stark and collaborators, released under the AGPL-3.0 license.

Rlauxe uses the [Raire Java library](https://github.com/DemocracyDevelopers/raire-java) for Instant runoff Voting (IRV) contests.
Raire-Java is Copyright 2023-2025 Democracy Developers. It is based on software (c) Michelle Blom in C++
https://github.com/michelleblom/audit-irv-cp/tree/raire-branch, and released under the GNU General Public License v3.0.

See [Getting Started](docs/Developer.md#getting-started) if you are a developer wanting to compile and use the library.

**Table of Contents**
<!-- TOC -->
* [SHANGRLA framework](#shangrla-framework)
* [Rlauxe Workflow Overview](#rlauxe-workflow-overview)
* [Audit Types](#audit-types)
  * [Card Level Comparison Audits (CLCA)](#card-level-comparison-audits-clca)
  * [OneAudit CLCA](#oneaudit-clca)
  * [Polling Audits](#polling-audits)
* [Comparing Sample Sizes by Audit type](#comparing-sample-sizes-by-audit-type)
  * [Samples needed with no errors](#samples-needed-with-no-errors)
  * [Samples needed when there are errors](#samples-needed-when-there-are-errors)
    * [CLCA with errors](#clca-with-errors)
    * [Comparison of CLCA, Polling, and OneAudit](#comparison-of-clca-polling-and-oneaudit)
  * [Effect of Phantoms on Samples needed](#effect-of-phantoms-on-samples-needed)
* [Estimating Sample Batch sizes](#estimating-sample-batch-sizes)
  * [Simulating MVRs](#simulating-mvrs)
  * [Choosing ballots](#choosing-ballots)
  * [Estmation extra mvrs and number of rounds](#estmation-extra-mvrs-and-number-of-rounds)
* [Multiple Contest Auditing](#multiple-contest-auditing)
  * [Efficiency](#efficiency)
  * [Deterministic sampling order for each Contest](#deterministic-sampling-order-for-each-contest)
* [Reference Papers](#reference-papers)
* [Appendices](#appendices)
  * [Extensions of SHANGRLA](#extensions-of-shangrla)
  * [Unanswered Questions](#unanswered-questions)
  * [Also See](#also-see)
  * [Case Studies](#case-studies)
  * [Documentation Index](#documentation-index)
    * [Pending Review](#pending-review)
<!-- TOC -->

Click on plot images to get an interactive html plot. 

# SHANGRLA framework

SHANGRLA is a framework for running Risk Limiting Audits for elections.
It uses a _statistical risk testing function_ that allows an audit to statistically
prove that an election outcome is correct (or not) to within a _risk level α_. For example, a risk limit of 5% means that
the election outcome (i.e. the winner(s)) is correct with 95% probability.

It uses an _assorter_ to assign a number to each ballot, and checks outcomes by testing _half-average assertions_, 
each of which claims that the mean of a finite list of numbers is greater than 1/2. 
The complementary _null hypothesis_ is that the assorter mean is not greater than 1/2.
If that hypothesis is rejected for every assertion, the audit concludes that the outcome is correct.
Otherwise, the audit expands, potentially to a full hand count. If every assertion is tested at risk level α, this results 
in a risk-limiting audit with risk limit α:
**_if the election outcome is not correct, the chance the audit will stop shy of a full hand count is at most α_**.

| term      | definition                                                                                  |
|-----------|---------------------------------------------------------------------------------------------|
| audit     | iterative process of randomly sampling ballots and checking if all the assertions are true  |
| risk	     | confirm or reject with this "risk limit", for example risk = .05 (5%)                       |
| assorter  | assigns a number between 0 and upper to each card, chosen to make assertions "half average" |
| assertion | asserts that the average of the assorter values is > 1/2: "half-average assertion"          |
| riskFn    | the statistical method used to test if the assertion is true.                               |
| Nc        | a trusted, independent bound on the number of valid cards cast in the contest c.            |
| Ncast     | the number of cards validly cast in the contest that we have an accounting of.              |
| Npop      | the number of physical cards that might contain the contest.                                |


# Rlauxe Workflow Overview

**1. Election Creation**

Create the Card Manifest:
- Create a _Card Manifest_, in which every physical card (and any phantom cards) have a unique entry (called the _AuditableCard_ or _card_).
- If this is a CLCA, attach the Cast Vote Record (CVR) from the election vendor's tabulation system, to its AuditableCard.
- If this is a OneAudit CLCA, create OneAudit Pools that describe each unique "pool" of cards, and set the poolId for
  each card in the pool.

For each contest:
- Describe each contest name, candidates, contest type (eg Plurality, IRV, Dhondt, ...), etc. in the _ContestInfo_.
- Count the votes in the usual way. The reported winner(s) and the reported margins are based on this vote count.
  Use the votes, the number of valid cards (Nc), the number of votes cast (Ncast), and the ContestInfo to create the _Contest_.
- If this is a multicontest election, you may need to create _Batch_ objects that describe each contest's _sample population_, 
  the set of cards that might contain the contest. Count the number of cards in the population to get _Npop_ which is used to calculate
  the contest's _diluted margin_. See [SamplePopulations](docs/SamplePopulations.md) for details.
- The rlauxe software generates the assertions needed to prove if the winners are correct. Add the assertions and Npop
  to the Contest to get the  _ContestWithAssertions_.

Commitment:
- Configuration information is set in ElectionInfo.
- Write the electionInfo, cardManifest, contest, and if needed, the pools and batches to a publically accessible "bulletin board".
- Digitally sign these files; they constitute the "election commitment" and may not be altered once the seed is chosen.

Verify:
- Verify the election commitment

**2. Audit Creation**

Create the seed and the sorted CardManifest:
- Create a random 32-bit integer "seed" in a way that allows public observers to be confident that it is truly random.
- Publish the random seed to the bulletin board. It becomes part of the "audit commitment" and may not be altered once chosen.
- Use the PRNG (Psuedo Random Number Generator) with the random seed, and assign the generated PRNs (Psuedo Random Number), 
  in order, to the auditable cards. Sort the cards by PRN.
- Configuration information, including the seed, are set in AuditCreationConfig.
- The AuditCreationConfig and the sorted cards constitute the "audit commitment", and may not be altered once the seed is chosen.

Verify:
- Verify the audit commitment

**3. Starting the Audit**

The Election Auditors (EA) can examine the election and audit commitment files, and run simulations to estimate how many
cards will need to be sampled. The EA can choose:

* which contests will be included in the Audit
* the risk limit 
* other audit configuration parameters

The audit configuration parameters are written to _auditConfig.json_. 

**4. Audit Rounds**

The audit proceeds in rounds:

1. **Estimation**: for each contest, estimate how many samples are needed to satisfy the risk limit
2. **Choosing contests and sample sizes**: the EA decides which contests and how many samples will be audited.
   This may be done with an automated algorithm, or the Auditor may make individual contest choices.
3. **Audit Round committment**: Once the EA is satisfied with their chosen contests and estimates, the actual ballots to be sampled 
   are selected, in order, from the sorted Card Manifest until the estimated sizes are satisfied. These are then committed
   and may not be changed.
4. **Manual Audit**: the auditors find the physical ballots that were selected for auditing and do a manual audit of each.
5. **Create MVRs**: the auditors enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
6. **Run the audit**: For each contest, using the MVRs, the software calculates if the risk limit is satisfied.
7. **Decide on Next Round**: for each contest not satisfied, the auditors decide whether to continue to another round, or call for a hand recount.

(Is there an "audit round committment" ? )

**5. Verification**

Independently written verifiers can read the Audit Record and verify that the audit was correctly performed.
See [Verification](docs/Verification.md).

Also see [The Audit Record](https://github.com/JohnLCaron/rlauxe/blob/main/docs/AuditRecord.md) for more detail.

# Audit Types

Rlauxe supports three kinds of audits: CLCA, OneAudit, and Polling.

## Card Level Comparison Audits (CLCA)

When the election system produces an electronic record for each ballot card, known as a Cast Vote Record (CVR), then
Card Level Comparison Audits can be done that compare sampled CVRs with the corresponding hand audited ballot card (MVR). 
A CLCA typically needs many fewer sampled ballots to validate contest results than other methods, especially when the margins are small.

The requirements for CLCA audits:

* The election system must be able to generate machine-readable Cast Vote Records (CVRs) for each ballot.
* Unique identifiers must be assigned to each physical ballot, and recorded on the CVR, that allows finding the physical ballot that matches the sampled CVR.

For the _risk function_, rlauxe uses the **BettingMart** function with the **GeneralAdaptiveBetting** _betting function_.
GeneralAdaptiveBetting uses estimates/measurements of the error rates between the Cvrs and the Mvrs. 
If the error estimates are correct, one gets optimal "sample sizes", the number of ballots needed to prove the election is correct.

See [Betting risk function](docs/BettingRiskFunctions.md) for an overview of the risk and betting functions.

## Overstatement Net Equivalent Audit (OneAudit)

OneAudit is a type of CLCA audit. It deals with the cases where:

1. CVRS are available for some ballots, and the non-CVR ballots are in one or more _pools_ for which subtotals are available.
   (This is the Boulder "redacted votes" case.)
2. CVRS are available for all ballots, but some CVRs cannot be matched to physical ballots. The unmatched ballots
   are in one or more _pools_ for which subtotals are available. (This is the San Francisco case where
   mail-in ballots have matched CVRS, and in-person precinct votes have unmatched CVRs).

In both cases we use the average assorter value in a pool as the assort value of the (missing) CVR.
When a ballot has been chosen for hand audit:

1. If it has a CVR, use the standard CLCA overstatement assorter.
2. If it has no CVR, use the overstatement-net-equivalent (ONE) CVR from the pool that it belongs to.

Thus, all cards must either have a CVR or be contained in a pool.

See [Betting with OneAudit Pools](docs/BettingRiskFunctions.md#betting-with-oneaudit-pools) for an overview of OneAudit betting.

## Polling Audits

When CVRs are not available, a Polling audit can be done.

For the risk function, Rlaux uses the [AlphaMart risk function](docs/AlphaMart.md)  with the [ShrinkTrunkage estimation of the true
population mean](docs/AlphaMart.md#truncated-shrinkage-estimate-of-the-population-mean). 

# Comparing Sample Sizes by Audit type

Here  we characterize the number of samples needed for each audit type. For clarity of presentaton, we
assume we have only one contest, and ignore the need to estimate sizes for each audit round. This is a 
_one sample at a time_ audit, which terminates as soon as the risk limit is confirmed or rejected. 

In the section [Estimating Sample Batch sizes](#estimating-sample-batch-sizes) below, we deal with the 
need to estimate a batch size, and the extra overhead of audit rounds. In the section 
[Multiple Contest Auditing](#multiple-contest-auditing) below, we deal with the complexity of having multiple contests
on ballots.

In general, samplesNeeded is independent of the population size Npop, and depends only on the _diluted margin_ 
and the random sequence of ballots chosen to be hand audited (aka _sampled_). (Actually there is a slight dependence on N for 
_without replacement_ audits, that gets larger as the sample size approaches N.)

The following plots are simulations, averaging the results over the stated number of runs.

## Samples needed with no errors

The best case using the least samples is CLCA when there are no errors in the CVRs, and no phantom ballots. Then 
the samplesNeeded depend only on the margin, and is a straight line vs margin on a log-log plot. 

The smallest sample size for CLCA when there are no errors is when you always bet the maximum (1/µ_i, approximately 2). 
However, if there are errors and one of the assort value is 0.0, the maximum bet will "stall" the audit and the audit cant recover
([see details](docs/BettingRiskFunctions.md#stalled-audits-and-maximum-bets)). To avoid this, rlauxe limits how close the bet can be to the maximum. 
Here is a plot of CLCA no-error audits with the bet limited to 70, 80, 90, and 100% of the maximum
(click on plot images to get an interactive html plot):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaNoErrorsMaxRisk/clcaNoErrorsMaxRiskLogLog.html" rel="clcaNoErrorsMaxRiskLogLog">![clcaNoErrorsMaxRiskLogLog](docs/plots2/samplesNeeded/clcaNoErrorsMaxRisk/clcaNoErrorsMaxRiskLogLog.png)</a>

MaxRisk is the percent of the maximum bet allowed. The maximum bet = 1/µ_i (approximately 2), which varies slightly as the
sampling proceeds (see populationMeanIfH0 in org.cryptobiotic.rlauxe.betting.Utils).
At any setting of maximum bet, the CLCA assort value is always the same when there are no errors, and so there is no variance,
and the plot above shows the exact number of samples needed as a function of margin and maximum risk.

For polling, the assort values vary, and the number of samples needed depends on the order the samples are drawn.
Here are the average and standard deviation over 100 independent trials at each reported margin:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/pollingWithStdDev/pollingWithStdDevLinear.html" rel="pollingWithStdDev">![pollingWithStdDev](docs/plots2/samplesNeeded/pollingWithStdDev/pollingWithStdDevLinear.png)</a>

For OneAudit, results depend on the percent of CVRs vs pooled ballots, the pool averages, and if there are multiple card styles in the pools.
The following is a best case scenario with no errors in the CVRs, a single pool with the same margin as the CVRs, and a single card style, 
with several values of the CVR percentage, as a function of margin, and maxRisk = .9:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/oneaudit/OneAuditNoErrors/OneAuditNoErrorsLogLinear.html" rel="OneAuditNoErrorsLogLinear">![OneAuditNoErrorsLogLinear](docs/plots2/oneaudit/OneAuditNoErrors/OneAuditNoErrorsLogLinear.png)</a>

OneAudit has a large variance due to the random sequence of pool values. Here are the one sigma intervals for
the "best case" 90% CVR OneAudit. For example, a 2% margin contest has a one-sigma interval of (466, 2520) (click on the image
to get an interactive plot):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/oneaudit/OneAuditWithStdDev/OneAuditWithStdDevLinear.html" rel="OneAuditWithStdDevLinear">![OneAuditWithStdDevLinear](docs/plots2/oneaudit/OneAuditWithStdDev/OneAuditWithStdDevLinear.png)</a>

## Samples needed when there are errors

In the following simulations, errors are created between the CVRs and the MVRs, by taking _fuzzPct_ of the cards
and randomly changing the candidate that was voted for. When fuzzPct = 0.0, the CVRs and MVRs agree.
When fuzzPct = 0.01, 1% of the contest's votes were randomly changed, and so on. 

(This fuzzing mechanism is rather crude, and may not reflect any real-world pattern of errors. 
See [ClcaErrors](docs/ClcaErrors.md) for a deeper understanding of the effect of CLCA errors.)

### CLCA with errors

Here are the results of 1000 simulations of CLCA average samplesNeeded by margin for values of fuzzPct between 0 and 1%:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaFuzzByMarginLogLog.html" rel="clcaFuzzByMarginLogLog">![margin2WithStdDevLinear](docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaFuzzByMarginLogLog.png)</a>

The average samplesNeeded tell only half the story. Here is the standard deviation of the distributions of the previous plot
(there is no variation when fuzzPct = 0):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaFuzzByMarginStddevLogLog.html" rel="clcaFuzzByMarginStddevLogLog">![margin2WithStdDevLinear](docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaFuzzByMarginStddevLogLog.png)</a>

A plot of standard deviation against samples needed shows an approximate linear relationship, approximately independent
of fuzzPct when nsamples < 30,000:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaStddevVsSamplesNeededLinear.html" rel="clcaStddevVsSamplesNeededLinear">![clcaStddevVsSamplesNeededLinear](docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaStddevVsSamplesNeededLinear.png)</a>

A linear relationship implies 

    stddev = b + m * nsamples

where m is the slope of the line. Taking representative points (x0 = 68, y0 = 16) and (x1 = 37028, y1 = 21675)

    m = (y2 - y1) / (x2 - x1) 
      = (21675 - 16) / (37028 - 68) 
      = .586

    stddev = b + m * nsamples
    b = stddev - m * nsamples
    b = y0 - m * x0
    b = 16 - .586 * 68
    b = -23.85

so our approximate fit is:

    stddev = .586 * nsamples - 23.85

which we show on a log-log plot here:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaStddevVsSamplesModeledLogLog.html" rel="clcaFuzzByMarginStddevLogLog">![clcaStddevVsSamplesModeledLogLog](docs/plots2/samplesNeeded/clcaFuzzByMargin/clcaStddevVsSamplesModeledLogLog.png)</a>

These results are generated by our single round (no estimation phase) CLCA algorithm with maxRisk = .9, and would be different with other choices of algorithm parameters. (A least squares plot would be more accurate but we dont actually use this approximate fit in our algorithms.)
The salient point is that the variance is approximately linear with sample size, and so become more of a problem when margins are smaller and samples are bigger. 

### Comparison of CLCA, Polling, and OneAudit

With the margin fixed at 2%, this plot compares polling and CLCA audits and their variance:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/margin2WithStdDev/margin2WithStdDevLinear.html" rel="margin2WithStdDevLinear">![margin2WithStdDevLinear](docs/plots2/samplesNeeded/margin2WithStdDev/margin2WithStdDevLinear.png)</a>

Here are just CLCA audits with margins of .01, .02, and .04, over a range of fuzz errors from 0 to 1%:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/clcaAuditsWithFuzz/clcaAuditsWithFuzzLinear.html" rel="clcaAuditsWithFuzz">![clcaAuditsWithFuzz](docs/plots2/samplesNeeded/clcaAuditsWithFuzz/clcaAuditsWithFuzzLinear.png)</a>

* Polling audit sample sizes are all but impervious to errors, because the sample variance dominates the errors when the errors are small.
* As margins get smaller, the variance in CLCA audits increases. At .001 fuzz (1 in 1000), an audit with a margin of 1% has an
average sample size of 814, but the 1-sigma range goes from 472 to 1157. 
* A rule of thumb might be that if you want to do CLCA audits down to 1% margin, your error rate must be less than 1/1000.

Here are similar results for OneAudits with fuzz in their CVRs:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/oneaudit/OneAuditWithFuzz/OneAuditWithFuzzLinear.html" rel="OneAuditWithFuzz">![OneAuditWithFuzz](docs/plots2/oneaudit/OneAuditWithFuzz/OneAuditWithFuzzLinear.png)</a>

There's not much change as the CVR errors increase; the variance generated by the errors is small compared to the OneAudit variance from the pooled ballots.

## Effect of Phantoms on Samples needed

Varying phantom percent, up to and over the margin of 4.5%, with errors generated with 1% fuzz:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/samplesNeeded/AuditsWithPhantoms/AuditsWithPhantomsLinear.html" rel="AuditsWithPhantomsLinear">![AuditsWithPhantomsLinear](docs/plots2/samplesNeeded/AuditsWithPhantoms/AuditsWithPhantomsLinear.png)</a>

* Increased phantoms have a strong effect on sample size.
* All audits go to hand count when phantomPct gets close to the margin, as they should.
* See [effect of Phantoms on samples needed](docs/ClcaErrors.md#phantom-ballots) to see how many extra ballots are needed for each phantom ballot sampled.

# Estimating Sample Batch sizes

Audits are usually done in rounds. For each round, for each contest, we estimate the number of ballots needed to statistically prove the contest outcome.
This generates a specific set of ballots needed for the round. The auditors track down where those ballots are, hand audit them,
and enter the results into the system. The audit is then run, each contest is proven or not, and the unproven contests
(may) continue on to the next round.

Estimation is greatly complicated by the inherent variation in the random sample of ballots.
Overestimating sample sizes uses more MVRs than needed. Underestimating sample sizes forces more rounds than needed.

Before the audit rounds are done, the CardManifest has been created, the seed has been chosen which creates a PRNG, and the CardManifest
is sorted by the PRNG. Each contest then has a "canonical sequence" of ballots that will be used for all audit rounds,
namely the "first n ballots that contain the contest", where n is the number of samples estimated for the contest for that round.

The real audit uses the selected cards paired with the real MVRs that have been hand audited.
The estimation algorithm creates simulated audits using the selected cards paired with simulated MVRs. 
It runs _nsimTrials_ simulated audits with different simulated MVRs for each trial, and each trial finds the number of
samples needed for its randomly simulated MVRs. It forms the distribution of samples from the trials, and selects the _estPercentile_ percentile to use as the
estimated samples needed for the contest. If the simulation is accurate, the audit should succeed _estPercentile_ percent of the time.

For each contest we choose the assertion with the largest samples needed and use it for the simulation. For polling, this is the
assertion with the smallest margin. For CLCA/OneAudit this is the assertion with the smallest value of noerror, which accounts for
assorters that have an upperLimit != 1 (this assertion equals the smallest margin assertion when assorter.upperLimit = 1).

Using the actual cards for the simulation immediately eliminates some of the variance due to phantom cards and OneAudit pools,
because the card knows if its a phantom, or if it belongs to a OneAudit pool. Then:

1. For phantom cards, the common case is that the MVR will agree that it is a phantom, and so the simulated assort value will match the actual assort value.

2. For OneAudit pools, the common case is that the MVR will agree that it comes from the pool, and will contain the contest.
The simulated MVR will not know the actual vote, so the difference between the simulated and the actual overstatement will be
````
    simulated overstatement = (pool_average - mvr_sim.assort)
       actual overstatement = (pool_average - mvr_actual.assort) 
````

TODO...

## Simulating MVRs

Simulation is done with a _Vunder_ object for each contest that tracks the number of votes for each candidate, the number of undervotes, 
and the number of missing votes in a population. Each of those is a choice with a count.
A simulated choice is randomly made in proportion to the choices' counts; then the choice count is decremented. If one simulated all
of the cards in the population, the simulated totals would exactly match the populations totals.

The simulation of the MVR is done in the following way:

1. CLCA and OneAudit CVRs: the MVR equals the CVR with optional errors using the CLCA error rates from previous rounds.

2. OneAudit pools, and Polling with Pools: For each pool, Vunder tracks the pool's votes, undervotes, and missing votes, and choices are drawn from it.

3. Polling with Batches: The difference between a batch and a pool is that a batch doesn't have votes subtotals, it only knows which contests may be on a card in the batch. We use a single Vunder that tracks the contest's total votes, undervotes and missing votes, and choices are drawn from it for all batches. Only the contests that are in the batch are added to the simulated MVR.

4. Polling without Batches: We use a single Vunder that tracks the contest's total votes, undervotes and missing votes, and choices are drawn from it for all cards.

## Choosing ballots

Once all of the contests' sample sizes are estimated, we choose which ballots/cards to sample.
This step depends on the correctness of the CardManifest and the Batch/Pool information which tells which cards
may have which contests. The sampling must be uniform over the contest's populations for a statistically valid audit.

The _consistent sampling_ algorithm reads through the sorted Card Manifest and chooses the first cards which may contain one or more wanted contests. Once the contest's estimated sample size is satisfied, it drops out of the wanted list. This continues until the wanted list is empty. The set of selected cards is written to disk and used for the real audit.

Each round does its own consistent sampling without regard to the previous round's results. Previously audited MVRS are always used again in subsequent rounds, for contests that continue to the next round. The auditors only have to find and hand audit the _new mvrs_ that havent been audited before.

## Estimating extra mvrs and number of rounds

Using the above algorithm for estimating samples sizes, here are the extra samples and average number of rounds for the three audit types:

**CLCA**

CLCA at different values of fuzzed errors:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginLogLinear.html" rel="extraVsMargin">![extraVsMargin](docs/plots2/estimate/extraVsMarginLogLinear.png)</a>

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginNroundsLinear.html" rel="extraVsMarginNrounds">![extraVsMarginNrounds](docs/plots2/estimate/extraVsMarginNroundsLinear.png)</a>

* Extra Mvrs stay reasonable at these fuzz levels. 
* With no errors, the estimation is spot on so there are no extra mvrs and the audit always finishes in 1 round. This is true
  even when there are phantoms.
* Nrounds generally is 2 or less, which was the goal of the algorithm.

The effect of 1% phantoms:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginWithPhantomsLogLinear.html" rel="extraVsMarginWithPhantoms">![extraVsMarginWithPhantoms](docs/plots2/estimate/extraVsMarginWithPhantomsLogLinear.png)</a>

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginWithPhantomsNroundsLinear.html" rel="extraVsMarginWithPhantomsNrounds">![extraVsMarginWithPhantomsNrounds](docs/plots2/estimate/extraVsMarginWithPhantomsNroundsLinear.png)</a>

**OneAudit**

OneAudit at different percentages of CVR data:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginOALogLinear.html" rel="extraVsMarginOA">![extraVsMarginOA](docs/plots2/estimate/extraVsMarginOALogLinear.png)</a>

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginOANroundsLinear.html" rel="extraVsMarginOANrounds">![extraVsMarginOANrounds](docs/plots2/estimate/extraVsMarginOANroundsLinear.png)</a>

* Extra Mvrs are quite high at lower margins and as the percentage CVRs decreases.
* Nrounds averages are < 2. 
* Possibly we could tweak the parameters to reduce extra samples and still keep average rounds < 2.

**Polling**

Here are the extra samples and average number of rounds for Polling:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginPollingLinear.html" rel="extraVsMarginPolling">![extraVsMarginPolling](docs/plots2/estimate/extraVsMarginPollingLinear.png)</a>

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/estimate/extraVsMarginPollingNroundsLinear.html" rel="extraVsMarginPollingNrounds">![extraVsMarginPollingNrounds](docs/plots2/estimate/extraVsMarginPollingNroundsLinear.png)</a>

* Extra Mvrs are very high.
* Nrounds averages are < 2.
* Possibly we could tweak the parameters to reduce extra samples and still keep average rounds < 2.

# Multiple Contest Auditing

An (American) election typically consists of several or many contests, and it is likely to be more efficient to audit all the contests at once.
We have several mechanisms for choosing contests to remove from the audit to keep the sample sizes reasonable. However,
these choices are reviewed by the human auditors and may be overridden.

Before the audit begins:
1. Any contest whose margin in votes is less than its number of phantom cards is removed from the audit with failure code _TooManyPhantoms_.
2. If _minRecountMargin_ is set, a contest whose recount margin is less than _minRecountMargin_ is removed from the audit with failure code MinMargin.
3. If _minMargin_ is set, a contest whose diluted margin is less than _minMargin_ is removed from the audit with failure code MinMargin.

For each round:
1. If _maxSamplePct_ is set, a contest whose estimated sample size is greater than _maxSamplePct * Npop_ is removed from the audit with failure code FailMaxSamplesAllowed.
2. If _contestSampleCutoff_ is set, a contest whose estimated sample size is greater than _contestSampleCutoff_ is removed from the audit with failure code FailMaxSamplesAllowed.
3. If _auditSampleCutoff_ is set, if the audit round's sample size is greater than _auditSampleCutoff_, then the contest with the largest estimated sample size is removed from the audit with failure code FailMaxSamplesAllowed. The sampling is then redone without that contest, and the check on the total number of ballots is repeated, until the total sample size is less than auditSampleCutoff.

These rules are somewhat arbitrary but allow us to test audits without human intervention. In a real audit,
auditors would select which contests to audit, rerunning the estimation as needed,
to try out different scenarios before committing to which contests continue on to the next round. 

* See the prototype [rlauxe Viewer](https://github.com/JohnLCaron/rlauxe-viewer) to see how an auditor might control the estimation phase before committing to a sample for the round.
* See [Case Studies](#case-studies) for simulated audits on real election data.

## Efficiency

We assume that the cost of auditing a ballot is dominated by the cost of retrieving the ballot. So if two contests always 
appear together on a ballot, then auditing the second contest is "free". If the two contests appear on the same ballot some 
pct of the time, then the cost is reduced by that pct. More generally the reduction in cost of a multicontest audit depends
on the various percentages the contests appear on the ballot chosen for auditing.

## Deterministic sampling order for each Contest

For any given contest, the sequence of ballots/CVRS to be used by that contest is fixed when the PRNG is chosen. This 
is called the _canonical sequence_ for that contest.

In a multi-contest audit, at each round, the estimated sample size (n_est) of the number of cards needed for each contest is calculated, 
and the first n_est cards in the contest's sequence are sampled.
The total set of cards sampled in a round is the union of the individual contests' set. 
The added efficiency of a multi-contest audit comes when the same card is chosen for more than one contest.

It may happen that after a contest's estimated sample size has been satisfied, further cards are chosen because they contain a contest whose sample size has not been satisfied. Those extra cards can be used in the audit for a contest as long as the contest's canonical sequence in unbroken. If a card that contains the contest is not used in the sample, the canonical sequence is broken and any further cards that contain the contest are not used in the audit. This ensures that the audit only uses the canonical sequence for each contest, which ensures that the sample is random.

The set of contests that will continue to the next round is not known, so the set of ballots sampled at each round is not known in advance. Nonetheless, for each contest, and for each round, the sequence of ballots seen by the audit is fixed when the PRNG is chosen.


# Reference Papers
````
P2Z         Limiting Risk by Turning Manifest Phantoms into Evil Zombies. Banuelos and Stark. July 14, 2012
    https://arxiv.org/pdf/1207.3413
    
RAIRE        Risk-Limiting Audits for IRV Elections. Blom, Stucky, Teague 29 Oct 2019
    https://arxiv.org/abs/1903.08804

SHANGRLA     Sets of Half-Average Nulls Generate Risk-Limiting Audits: SHANGRLA.	Stark, 24 Mar 2020
    https://arxiv.org/pdf/1911.10035, https://github.com/pbstark/SHANGRLA

MoreStyle	More style, less work: card-style data decrease risk-limiting audit sample sizes. Glazer, Spertus, Stark; 6 Dec 2020
    https://arxiv.org/abs/2012.03371
    
Proportional  Assertion-Based Approaches to Auditing Complex Elections, with Application to Party-List Proportional Elections; 2 Oct, 2021
    Blom, Budurushi, Rivest, Stark, Stuckey, Teague, Vukcevic
    https://arxiv.org/abs/2107.11903v2
    
ALPHA:      Audit that Learns from Previously Hand-Audited Ballots. Stark, Jan 7, 2022
    https://arxiv.org/pdf/2201.02707, https://github.com/pbstark/alpha.

BETTING     Estimating means of bounded random variables by betting. Waudby-Smith and Ramdas, Aug 29, 2022
    https://arxiv.org/pdf/2010.09686, https://github.com/WannabeSmith/betting-paper-simulations

COBRA:      Comparison-Optimal Betting for Risk-limiting Audits. Jacob Spertus, 16 Mar 2023
    https://arxiv.org/pdf/2304.01010, https://github.com/spertus/comparison-RLA-betting/tree/main

ONEAudit:   Overstatement-Net-Equivalent Risk-Limiting Audit. Stark 6 Mar 2023.
    https://arxiv.org/pdf/2303.03335, https://github.com/pbstark/ONEAudit

STYLISH	    Stylish Risk-Limiting Audits in Practice. Glazer, Spertus, Stark  16 Sep 2023
    https://arxiv.org/pdf/2309.09081, https://github.com/pbstark/SHANGRLA

SliceDice   Dice, but don’t slice: Optimizing the efficiency of ONEAudit. Spertus, Glazer and Stark, Aug 18 2025
    https://arxiv.org/pdf/2507.22179; https://github.com/spertus/UI-TS
    
Verifiable  Risk-Limiting Audits Are Interactive Proofs — How Do We Guarantee They Are Sound?
    Blom, Caron, Ek, Ozdemir, Pereira, Stark, Teague, Vukcevic
    submitted to IEEE Symposium on Security and Privacy (S&P 2026) 

ConsistentSampling  Consistent Sampling with Replacement
    Rivest
    https://arxiv.org/abs/1808.10016
````
Also see [complete list of references](docs/papers/papers.txt).

# Appendices

## Extensions of SHANGRLA

**Populations and hasStyle**

Rlauxe uses Population objects as a way to capture the information about which cards are in which sample populations,
in order to set the diluted margins correctly.
This allows us to refine SHANGRLA's hasStyle flag. 
See [SamplePopulations](docs/SamplePopulations.md) for more explanation and current thinking.

**CardManifest**

Rlauxe uses a CardManifest, which consists of a canonical list of AuditableCards, one for each possible card in the election, 
and the list of Populations. OneAudit pools are subtypes of Populations. The CardManifest is one of the committments that
the Prover must make before the random seed can be generated.

**General Adaptive Betting**

SHANGRLA's Adaptive Betting has been generalized to work for both CLCA and OneAudit and for any assorter. 
It uses apriori and measured error rates as well as phantom ballot rates to set optimal betting values.
See [BettingRiskFunctions](docs/BettingRiskFunctions.md) for more info.

**OneAudit Betting strategy**

OneAudit uses GeneralizedAdaptiveBetting and includes the OneAudit assort values and their known
frequencies in computing the optimal betting values. See [Betting with OneAudit pools](docs/BettingRiskFunctions.md#betting-with-oneaudit-pools) 
to see how this improves OneAudit sample sizes.

**MaxRisk for Betting**

In order to prevent stalls in BettingMart, the maximum bet is bounded by a "maximum loss" value, which is the maximum
percent of your "winnings" you are willing to lose on any one bet. The maximum loss can also be thought of as the percent
of the maximum bet allowed.

**Additional assorters**

Dhondt, AboveThreshold and BelowThreshold assorters have been added to support Belgian elections using Dhondt proportional
scoring. These assorters have an upper bound != 1, so are an important generalization of the Plurality assorter.

**OneAudit Card Style Data**

Rlauxe adds the option that there may be Card Style Data (CSD) for OneAudit pooled data, in part to investigate the 
difference between having CSD and not. Specifically, different OneAudit pools may have different values of
_hasExactContests_ (aka _hasStyle_).  See [SamplePopulations](docs/SamplePopulations.md).

**Multicontest audits**

Each contest has a canonical sequence of sampled cards, namely all the cards sorted by PRN, that may contain that contest.
This sequence doesnt change when doing multicontest audits. 
Multicontest audits choose what cards are sampled based on each contests' estimated sample size. An audit can take advantage
of "extra" samples for a contest in the sample, as long as the canonical sequence is always used. Once a card in the sequence is skipped,
the audit cant use more cards in the sample for that contest.

## Unanswered Questions

**Contest is missing in the MVR**

The main use of _hasStyle_, aka _hasExactContests_ is when deciding the assort value when an MVR is missing a contest.
There are unanswered questions about if this allows attacks, and if it should be used for polling audits.
See [SamplePopulations](docs/SamplePopulations.md#contest-is-missing-in-the-mvr) and
[Issue 552](https://github.com/JohnLCaron/rlauxe/issues/552).

**Optimal value of MaxRisk**

How to set MaxRisk in an optimal way?

## Also See
* [Getting Started](docs/Developer.md#getting-started)
* [Issues](https://github.com/JohnLCaron/rlauxe/issues)
* [Rlauxe Viewer](https://github.com/JohnLCaron/rlauxe-viewer)
* [Reference papers](docs/papers/papers.txt)

## Case Studies
* [Belgium 2024](docs/cases/Belgium2024.md)
* [Boulder County 2024](docs/cases/Boulder2024.md)
* [Colorado Statewide Election 2024](docs/cases/Colorado2024.md)
* [San Francisco County 2024](docs/cases/SF2024.md)

## Documentation Index
* [AlphaMart](docs/AlphaMart.md)
* [Audit Record](docs/AuditRecord.md)
* [BettingRiskFunctions](docs/BettingRiskFunctions.md)
* [Clca Errors](docs/ClcaErrors.md)
* [Developer Notes](docs/Developer.md)
* [Dhondt](docs/Dhondt.md)
* [Instant Runoff Voting (Raire)](docs/Raire.md)
* [Sample Populations](docs/SamplePopulations.md)

### Pending Review
* [Attacks](docs/Attacks.md)
* [Estimation](docs/Estimation.md)
* [Rlauxe Implementation Specificaton](docs/RlauxeSpec.md)
* [Verification](docs/Verification.md)
* [VerifierSpec](docs/VerifierSpec.md)

