# rlauxe
last update: 12/09/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

**WORK IN PROGRESS**

You can also read this on [github.io](https://johnlcaron.github.io/rlauxe/).

Table of Contents
<!-- TOC -->
* [rlauxe](#rlauxe)
  * [Reference Papers](#reference-papers)
  * [SHANGRLA framework](#shangrla-framework)
    * [Assorters and supported SocialChoices](#assorters-and-supported-socialchoices)
      * [PLURALITY](#plurality)
      * [APPROVAL](#approval)
      * [SUPERMAJORITY](#supermajority)
      * [IRV](#irv)
    * [Betting martingales](#betting-martingales)
    * [Polling audits](#polling-audits)
    * [Comparison audits](#comparison-audits)
      * [Comparison Betting Payoffs](#comparison-betting-payoffs)
      * [Comparison error rates](#comparison-error-rates)
    * [Estimating Error](#estimating-error)
      * [Polling Vs Comparison Estimated Sample sizes with no errors](#polling-vs-comparison-estimated-sample-sizes-with-no-errors)
      * [Estimating Sample sizes and error rates with fuzz](#estimating-sample-sizes-and-error-rates-with-fuzz)
  * [Sampling](#sampling)
    * [Estimating Sample sizes](#estimating-sample-sizes)
    * [Choosing which ballots/cards to sample](#choosing-which-ballotscards-to-sample)
      * [Comparison audits and CSDs](#comparison-audits-and-csds)
      * [Polling audits and CSDs](#polling-audits-and-csds)
      * [Polling Vs Comparison with/out CSD Estimated Sample sizes](#polling-vs-comparison-without-csd-estimated-sample-sizes)
    * [Missing Ballots (aka phantoms-to-evil zombies) (TODO)](#missing-ballots-aka-phantoms-to-evil-zombies-todo)
  * [Stratified audits using OneAudit (TODO)](#stratified-audits-using-oneaudit-todo)
  * [Differences with SHANGRLA](#differences-with-shangrla)
    * [Limit audit to estimated samples](#limit-audit-to-estimated-samples)
    * [compute sample size](#compute-sample-size)
    * [estimate comparison error rates](#estimate-comparison-error-rates)
    * [use of previous round's sampled_cvr_indices](#use-of-previous-rounds-sampled_cvr_indices)
  * [Other Notes](#other-notes)
  * [Development Notes](#development-notes)
<!-- TOC -->

## Reference Papers

    P2Z         Limiting Risk by Turning Manifest Phantoms into Evil Zombies. Banuelos and Stark. July 14, 2012

    RAIRE       Risk-Limiting Audits for IRV Elections.			Blom, Stucky, Teague    29 Oct 2019
        https://arxiv.org/abs/1903.08804

    SHANGRLA	Sets of Half-Average Nulls Generate Risk-Limiting Audits: SHANGRLA.	Stark, 24 Mar 2020
        https://github.com/pbstark/SHANGRLA

    MoreStyle	More style, less work: card-style data decrease risk-limiting audit sample sizes	Glazer, Spertus, Stark; 6 Dec 2020

    ALPHA:      Audit that Learns from Previously Hand-Audited Ballots. Stark, Jan 7, 2022
        https://github.com/pbstark/alpha.

    BETTING     Estimating means of bounded random variables by betting. Waudby-Smith and Ramdas, Aug 29, 2022
        https://github.com/WannabeSmith/betting-paper-simulations

    COBRA:      Comparison-Optimal Betting for Risk-limiting Audits. Jacob Spertus, 16 Mar 2023
        https://github.com/spertus/comparison-RLA-betting/tree/main

    ONEAudit:   Overstatement-Net-Equivalent Risk-Limiting Audit. Stark   6 Mar 2023.
        https://github.com/pbstark/ONEAudit

    STYLISH	    Stylish Risk-Limiting Audits in Practice.		Glazer, Spertus, Stark  16 Sep 2023
      https://github.com/pbstark/SHANGRLA

    VERIFIABLE  Publicly Verifiable RLAs.     Alexander Ek, Aresh Mirzaei, Alex Ozdemir, Olivier Pereira, Philip Stark, Vanessa Teague


## SHANGRLA framework

SHANGRLA is a framework for running [Risk Limiting Audits](https://en.wikipedia.org/wiki/Risk-limiting_audit) (RLA) for elections.
It uses an _assorter_ to assign a number to each ballot, and a _statistical risk testing function_ that allows an audit to statistically
prove that an election outcome is correct (or not) to within a _risk level α_, for example,  risk limit = 5% means that
the election is correct with 95% probability.

It checks outcomes by testing _half-average assertions_, each of which claims that the mean of a finite list of numbers 
between 0 and upper is greater than 1/2. The complementary _null hypothesis_ is that each assorter mean is not greater than 1/2.
If that hypothesis is rejected for every assertion, the audit concludes that the outcome is correct.
Otherwise, the audit expands, potentially to a full hand count. If every null is tested at risk level α, this results 
in a risk-limiting audit with risk limit α:
**_if the election outcome is not correct, the chance the audit will stop shy of a full hand count is at most α_**.

This formulation unifies polling audits and comparison audits, with or without replacement. It allows for the ballots to
be divided into _strata_, each of which is sampled independently (_stratified sampling_), or to use
batches of ballot cards instead of individual cards (_cluster sampling_).

| term          | definition                                                                                     |
|---------------|------------------------------------------------------------------------------------------------|
| N             | the number of ballot cards validly cast in the contest                                         |
| risk	         | we want to confirm or reject the null hypothesis with risk level α.                            |
| assorter      | assigns a number between 0 and upper to each ballot, chosen to make assertions "half average". |
| assertion     | the mean of assorter values is > 1/2: "half-average assertion"                                 |
| estimator     | estimates the true population mean from the sampled assorter values.                           |
| riskTestingFn | is the statistical method to test if the assertion is true.                                    |
| audit         | iterative process of picking ballots and checking if all the assertions are true.              |


### Assorters and supported SocialChoices

#### PLURALITY

"Top k candidates are elected."
The rules may allow the voter to vote for one candidate, k candidates or some other number, including n, which
makes it approval voting.

See SHANGRLA, section 2.1.

A contest has K ≥ 1 winners and C > K candidates. Let wk be the kth winner, and ℓj be the jth loser.
For each pair of winner and loser, let H_wk,ℓj be the assertion that wk is really the winner over ℓj.

There are K(C − K) assertions. The contest can be audited to risk limit α by testing all assertions at significance level α.
Each assertion is tested that the mean of the assorter values is > 1/2 (or not).

For the case when there is only one winner, there are C - 1 assertions, pairing the winner with each loser.
For a two candidate election, there is only one assertion.

For the ith ballot, define `A_wk,ℓj(bi)` as
````
    assign the value “1” if it has a mark for wk but not for ℓj; 
    assign the value “0” if it has a mark for ℓj but not for wk;
    assign the value 1/2, otherwise.
 ````

For polling, the assorter function is this A_wk,ℓj(MVR).

For a comparison audit, the assorter function is B(MVR, CVR) as defined below, using this A_wk,ℓj.

Notes
* Someone has to enforce that each CVR has <= number of allowed votes.


#### APPROVAL

See SHANGRLA, section 2.2.

In approval voting, voters may vote for as many candidates as they like.
The top k candidates are elected.

The plurality voting algorithm is used plurality voting.


#### SUPERMAJORITY

"Top k candidates are elected, whose percent vote is above a fraction, f."

See SHANGRLA, section 2.3.

A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win.
If multiple winners are allowed, each reported winner generates one assertion.

For the ith ballot, define `A_wk,ℓj(bi)` as
````
    assign the value “1/(2*f)” if it has a mark for wk but no one else; 
    assign the value “0” if it has a mark for exactly one candidate and not wk
    assign the value 1/2, otherwise.
````
For polling, the assorter function is this A_wk,ℓj(bi).

For a comparisian audit, the assorter function is B(MVR, CVR) as defined below, using this A_wk,ℓj.

One only needs one assorter for each winner, not one for each winner/loser pair.

Notes
* Someone has to enforce that each CVR has <= number of allowed votes.
* multiple winners are not yet supported for auditing.


#### IRV

We use the [RAIRE java library](https://github.com/DemocracyDevelopers/raire-java) to generate the assertions needed by SHANGRLA. 

See the RAIRE guides for details:
* [Part 1: Auditing IRV Elections with RAIRE](https://github.com/DemocracyDevelopers/Colorado-irv-rla-educational-materials/blob/main/A_Guide_to_RAIRE_Part_1.pdf)
* [Part 2: Generating Assertions with RAIRE](https://github.com/DemocracyDevelopers/Colorado-irv-rla-educational-materials/blob/main/A_Guide_to_RAIRE_Part_2.pdf)

### Betting martingales

In BETTING, Waudby-Smith and Ramdas develop tests and confidence sequences for the mean of a bounded population using 
betting martingales of the form

    M_j :=  Prod (1 + λ_i (X_i − µ_i)),  i=1..j    (BETTING eq 34 and ALPHA eq  10)

where µi := E(Xi | Xi−1), computed on the assumption that the null hypothesis is true.
(For large N, µ_i is very close to 1/2.)

The sequence (M_j) can be viewed as the fortune of a gambler in a series of wagers.
The gambler starts with a stake of 1 unit and bets a fraction λi of their current wealth on
the outcome of the ith wager. The value Mj is the gambler’s wealth after the jth wager. The
gambler is not permitted to borrow money, so to ensure that when X_i = 0 (corresponding to
losing the ith bet) the gambler does not end up in debt (Mi < 0), λi cannot exceed 1/µi.

See BettingMart.kt and related code for current implementation.

### Polling audits

The requirements for Polling audits:

* There must be a BallotManifest defining the population of ballots, that contains a unique identifier that can be matched to the corresponding physical ballot.
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest.

A polling audit retrieves a physical ballot and the auditors manually agree on what it says, creating an MVR (manual voting record) for it.
The assorter assigns an assort value in [0, upper] to the ballot, which is used in the testing statistic.

For the risk function, we use AlphaMart (or equivilent BettingMart) with ShrinkTrunkage, which estimates the true
population mean (theta) using a weighted average of an initial estimate (eta0) with the actual sampled mean. 
The average assort value is used as the initial estimate (eta0) when testing each assertion. These assort values
are specified in SHANGRLA, section 2. See Assorter.kt for our implementation.

The only settable parameter for the risk function is d, which is used for estimating theta (the true population assort value average) 
at the ith sample:

    estTheta_i = (d*eta0 + sampleSum_i) / (d + sampleSize_i)

which trades off smaller sample sizes when theta = eta0 (large d) vs quickly adapting to when theta < eta0 (smaller d).

To use BettingMart rather than AlphaMart, we just have to set

    λ_i = (estTheta_i/µ_i − 1) / (upper − µ_i)

where upper is the upper bound of the assorter (1 for plurality, 1/(2f) for supermajority), and µ_i := E(Xi | Xi−1) as above.

A few representative plots showing the effect of d are at [meanDiff plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=1185506629#gid=1185506629).
* High values of d do significantly better when the reported mean is close to the true mean. 
* When the true mean < reported mean, high d may force a full hand count unnecessarily.
* Low values of d are much better when true mean < reported mean, at the cost of larger samples sizes.
* Tentatively, we will use d = 100 as default, and allow the user to override.

See [Polling Simulations](docs/PollingSimulations.md) for more details and plots.

### Comparison audits

The requirements for Comparison audits:

* The election system must be able to generate machine-readable Cast Vote Records (CVRs) for each ballot, which is compared to the MVR during the audit.
* Unique identifier must be assigned to each physical ballot, and put on the CVR, in order to find the physical ballot that matches the sampled CVR. 
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest.

For the risk function, we use BettingMart with AdaptiveComparison. AdaptiveComparison needs estimates of the rates of 
over(under)statements. If these estimates are correct, one gets optimal sample sizes. 
AdaptiveComparison uses a variant of ShrinkTrunkage that uses a weighted average of initial estimates (aka priors) with the actual sampled rates.

See Cobra section 4.2 and SHANGRLA Section 3.2.

The overstatement error for the ith ballot is:
````
    ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper    "overstatement error" (SHANGRLA eq 2, p 9)
      bi is the manual voting record (MVR) for the ith ballot
      ci is the cast-vote record for the ith ballot
      A() is the assorter function
Let
     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
     v ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, aka the _diluted margin_).
     τi ≡ (1 − ωi /upper) ≥ 0
     B(bi, ci) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper) ≡ "comparison assorter" ≡ B(MVR, CVR)

Then B assigns nonnegative numbers to ballots, and the outcome is correct iff
    B̄ ≡ Sum(B(bi, ci)) / N > 1/2
and so B is an half-average assorter.
````

Notes 
* The comparison assorter B needs Ā(c) ≡ the average CVR assort value > 0.5.
* Ā(c) should have the diluted margin as the denominator. 
    (Margins are  traditionally calculated as the difference in votes divided by the number of valid votes.
    Diluted refers to the fact that the denominator is the number of ballot cards containing that contest, which is
    greater than or equal to the number of valid votes.)
* If overstatement error is always zero (no errors in CRV), the assort value is always
  ````
      noerror = 1 / (2 - margin/assorter.upperBound()) 
              = 1 / (3 - 2 * awinnerAvg/assorter.upperBound())
              > 0.5 since awinnerAvg > 0.5
  ````
* The possible values of the bassort function are then:
      {0, .5, 1, 1.5, 2} * noerror
* When the cvrs always equal the corresponding mvr, we always get bassort == noerror > .5, so eventually the null is rejected.

#### Comparison Betting Payoffs

For the ith sample with bet λ_i, the BettingMart payoff is

    t_i = 1 + λ_i * (X_i − µ_i)

where

    λ_i in [0, 1/u_i]
    X_i = {0, .5, 1, 1.5, 2} * noerror for {2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder} respectively.
    µ_i ~= 1/2
    λ_i ~in [0, 2]

then 

    payoff = t_i = 1 + λ_i * noerror * {-.5, 0, .5, 1.5}

Using AdaptiveComparison, λ_i depends only on the 4 estimated error rates (see next section) and the margin. 

Plots 1-5 shows the betting payoffs when all 4 error rates are equal to {0.0, 0.0001, .001, .005, .01}, respectively:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.0.html" rel="BettingPayoff0">![BettingPayoff0](./docs/plots/betting/BettingPayoff0.0.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff1.0E-4.html" rel="BettingPayoff1.0E-4">![BettingPayoff1.0E-4](./docs/plots/betting/BettingPayoff1.0E-4.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.001.html" rel="BettingPayoff0.001">![BettingPayoff0.001](./docs/plots/betting/BettingPayoff0.001.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.005.html" rel="BettingPayoff0.005">![BettingPayoff0.005](./docs/plots/betting/BettingPayoff0.005.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.01.html" rel="BettingPayoff01">![BettingPayoff01](./docs/plots/betting/BettingPayoff0.01.png)</a>

Plot 6 shows the payoffs for all the error rates when the MVR matches the CVR (assort value = 1.0 * noerror):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoffAssort1.0.html" rel="BettingPayoffAssort1">![BettingPayoffAssort1](./docs/plots/betting/BettingPayoffAssort1.0.png)</a>

Plot 7 translates the payoff into a sample size, when there are no errors, using (payoff)^sampleSize = 1 / riskLimit and
solving for sampleSize = -ln(riskLimit) / ln(payoff).

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoffSampleSize.html" rel="BettingPayoffSampleSize">![BettingPayoffSampleSize](./docs/plots/betting/BettingPayoffSampleSize.png)</a>

The plot "error=0.0" is the equivilent to COBRA Fig 1, p. 6 for risk=.05. This is the best that can be done, 
this is the minimum sampling size for the RLA.
Note that this value is independent of N, the number of ballots.

See GenBettingPayoff.kt for the geration of these plots.

#### Comparison error rates

The comparison error rates are:

        val p1: // apriori rate of 1-vote overstatements; voted for other, cvr has winner
        val p2: // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
        val p3: // apriori rate of 1-vote understatements; voted for winner, cvr has other
        val p4: // apriori rate of 2-vote understatements; voted for winner, cvr has loser

For IRV, the corresponding descriptions of the errror rates are:

    NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    NEB two vote understatement: cvr has loser preceeding winner(0), mvr has winner as first pref (1)
    NEB one vote understatement: cvr has winner preceding loser, but not first (1/2), mvr has winner as first pref (1)
    
    NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining  (1)

The assumptions that one makes about the comparison error rates greatly affect the sample size estimation.
Currrently all assumptions on the apriori error rates are arbitrary. These rates should
be empirically determined, and public tables for different voting machines should be published. 
While these do not affect the reliabilty of the audit, they have a strong impact on the estimated sample sizes.

See [Ballot Comparison using Betting Martingales](docs/Betting.md) for more details and detail plots of 2-way contests
with varying p2error rates..

### Estimating Error

#### Polling Vs Comparison Estimated Sample sizes with no errors

This plot (_PlotSampleSizeEstimates.plotComparisonVsPoll()_) shows the difference between a polling audit and a comparison
audit at different margins, with no errors:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/samples/ComparisonVsPoll.html" rel="Polling Vs Comparison Estimated Sample sizes">![ComparisonVsPoll](./docs/plots/samples/ComparisonVsPoll.png)</a>

Polling at margins < 4% needs prohibitively large sample sizes.
Comparison audits are perhaps useful down to margins = .4% .

"In a card-level comparison audit, the estimated sample size scales with
the reciprocal of the diluted margin." (STYLISH p.4)

Polling scales as square of 1/margin.

#### Estimating Sample sizes and error rates with fuzz

Currently our strategy for generating comparison errors is as follows:

The MVRs are "fuzzed" by taking _fuzzPct_ of the ballots
and randomly changing the candidate that was voted for. When fuzzPct = 0.0, the cvrs and mvrs agree.
When fuzzPct = 0.01, 1% of the contest's votes were randomly changed, and so on. 

The first plot shows that Comparison sample sizes are somewhat affected by fuzz. The second plot shows that Plotting sample sizes
have greater spread, but on average are not much affected.

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/samples/ComparisonFuzzed.html" rel="ComparisonFuzzed">![ComparisonFuzzed](./docs/plots/samples/ComparisonFuzzed.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/samples/PollingFuzzed.html" rel="PollingFuzzed">![PollingFuzzed](./docs/plots/samples/PollingFuzzed.png)</a>

We use this strategy for generating comparison error rate estimates, as a function of number of candidates in the contest.
(see GenerateComparisonErrorTable.kt):

N=100000 ntrials = 1000
generated 12/01/2024

| ncand | r1     | r2     | r3     | r4     |
|-------|--------|--------|--------|--------|
| 2     | 0.2535 | 0.2524 | 0.2474 | 0.2480 |
| 3     | 0.3367 | 0.1673 | 0.3300 | 0.1646 |
| 4     | 0.3357 | 0.0835 | 0.3282 | 0.0811 |
| 5     | 0.3363 | 0.0672 | 0.3288 | 0.0651 |
| 6     | 0.3401 | 0.0575 | 0.3323 | 0.0557 |
| 7     | 0.3240 | 0.0450 | 0.3158 | 0.0434 |
| 8     | 0.2886 | 0.0326 | 0.2797 | 0.0314 |
| 9     | 0.3026 | 0.0318 | 0.2938 | 0.0306 |
| 10    | 0.2727 | 0.0244 | 0.2624 | 0.0233 |

Then p1 = fuzzPct * r1, p2 = fuzzPct * r2, p3 = fuzzPct * r3, p4 = fuzzPct * r4.
For example, a two-candidate contest has significantly higher two-vote error rates (p2), since its more likely to flip a 
vote between winner and loser, than switch a vote to/from other.

For now, we will use this table to generate the error rates when estimating the sample sizes, until better estimates are avalable.
Currently the percentage of ballots with no votes cast for a contest is not well accounted for. 

Possible refinement of this algorithm might measure:
   1. percent time a mark is seen when its not there
   2. percent time a mark is not seen when it is there
   3. percent time a mark is given to the wrong candidate

## Sampling

SHANGRLA provides a very elegant separation between the implementation of risk testing (mostly described
above) and sampling.

### Estimating Sample sizes

For each contest we simulate the audit with manufactured data that has the same margin as the reported outcome. By
running simulations, we can use estimated error rates and add errors to the manufactured data.

For each contest assertion we estimate the required sample size that will satisfy the risk limit some fraction 
(_auditConfig.quantile_) of the time. The contest sample_size is then the maximum of the contest's assertion estimates.

Audits are done in rounds. If a contest is not proved or disproved, the next round's estimated sample size starts from 
the previous audit's pvalue.

Note that each round does its own sampling without regard to the previous round's sampled ballots. 
Since the seed remains the same and the ballot ordering is the same, then previously audited MVRS are used as much as 
possible in subsequent rounds.

Note: I _think_ its ok if more ballots come in between rounds (although this may be disallowed for security reasons). 
Just add the new ballots to the "all cvrs list", and do the next round as usual. 
Ideally N_c doesnt change, so it just makes less evil zombies.

### Choosing which ballots/cards to sample

Once we have all of the contests' estimated sample sizes, we next choose which ballots/cards to sample. 
This step is highly dependent on how much we know about which ballots contain which contests. In particular,
whether you have Card Style Data (CSD), (see MoreStyle, p2).

For comparison audits, the generated Cast Vote Record (CVR) comprises the CSD, as long as the CVR records when a contest recieves no votes.

So far, we can distinguish the following cases:

1. Comparison, hasCSD: CVR is a CSD.
2. Comparison, !hasCSD: contests with no votes are not recorded on the CVR.

3. Polling, hasCSD: has a ballot manifest with ballot.hasContest(contestId)
4. Polling, !hasCSD: doesnt know which ballots have which contests
5. Polling, precinct batches/containers (TODO). See MoreStyle, p.13:
  * precinct-based voting where each voter in a precinct gets the same ballot style, and the balots are stored by precinct.
  * information about which containers have which card styles, even without information about which cards contain which
    contests, can still yield substantial efficiency gains for ballot-polling audits.

#### Comparison audits and CSDs

ConsistentSampling is used in either case. This assigns large psuedo-random numbers to each ballot, orders the ballots
by that number, and selects the first ballots that use any contest that needs more samples, until all contests have 
at least contest.estSampleSize in the sample of selected ballots.

In practice, its unclear whether there's much difference for Comparison audits when the CSD is complete or not (see plots below). 
It appears that the assort value changes when there is a discrepency between the CVR and MVR, but not otherwise. 

See ComparisonAssorter.overstatementError() in core/Assorter.kt (from SHANGRLA Audit.py class Assorter):

    assorter that corresponds to normalized overstatement error for an assertion

    If `use_style == true`, then if the CVR contains the contest but the MVR does not,
    that is considered to be an overstatement, because the ballot is presumed to contain
    the contest .

    If `use_style == False`, then if the CVR contains the contest but the MVR does not,
    the MVR is considered to be a non-vote in the contest .

TODO: whats the reasoning for the above?

For !hasCSD, we wont select unvoted contests to be in the sample, since they arent recorded.
So then if we see an unvoted contest on the MVR, the case where the MVR contains the contest but not the CVR, then...

#### Polling audits and CSDs

When a Polling audit has CSDs, then ConsistentSampling can be used. Otherwise, we have to use the following process: 

The contests' estimated sample sizes are computed as usual. In order to find ballots containing a contest, 
among a batch of N ballots where Nc ballots contain that contest, we have to examine (on average) (N / Nc) * contest.estSampleSize. 

Then contest sample_size is then the maximum of the contest's assertion estimates, as before. The audit's sample size is the
maximum over the contests.

UniformSampling assigns large psuedo-random numbers to each ballot, sorts and chooses the first nsamples of them. Since
we cant distinguish whoch ballots have which contests, we just take the first audit.sampleSize ballots.

We must Nc as a condition of the audit, but its straightforwad to calculate a contests' sample size without Nc,
since it works out that Nc cancels out:

        sampleEstimate = rho / dilutedMargin                  // (SuperSimple p. 4)
        where 
          dilutedMargin = (vw - vl)/ Nc
        sampleEstimate = rho * Nc / (vw - vl)
        totalEstimate = sampleEstimate * N / Nc               // must scale by proportion of ballots with that contest
                      = rho * N / (vw - vl) 
                      = rho / fullyDilutedMargin
        where
          fullyDilutedMargin = (vw - vl)/ N

The scale factor N/Nc depends on how many contests there are and how they are distributed across the ballots.
In the following plot we just show N/Nc = 1, 2, 5 and 10. N/Nc = 1 is the case where the audit has CSDs.

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/samples/PollingNoStyle.html" rel="PollingNoStyle">![PollingNoStyle](./docs/plots/samples/PollingNoStyle.png)</a>

See _PlotPollingNoStyles.kt_.

#### Polling Vs Comparison with/out CSD Estimated Sample sizes

The following plot shows polling with CSD vs comparison with CSD vs comparison without CSD at different margins:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/samples/ComparisonVsStyleAndPoll.html" rel="ComparisonVsStyleAndPoll">![ComparisonVsStyleAndPoll](./docs/plots/samples/ComparisonVsStyleAndPoll.png)</a>

Little difference between comparison with/out CSD. Large difference with polling with CSD. Not showing polling without CSD,
since it depends on N/Nc scaling.

See _PlotSampleSizeEstimates.plotComparisonVsStyleAndPoll()_.

### Missing Ballots (aka phantoms-to-evil zombies) (TODO)

From P2Z paper:

    What if the ballot manifest is not accurate?
    it suffices to make worst-case assumptions about the individual randomly selected ballots that the audit cannot find.
    requires only an upper bound on the total number of ballots cast
    This ensures that the true risk limit remains smaller than the nominal risk limit.

    A listing of the groups of ballots and the number of ballots in each group is called a ballot manifest.
    designing and carrying out the audit so that each ballot has the correct probability of being selected involves the ballot manifest.

    To conduct a RLA, it is crucial to have an upper bound on the total number of ballot cards cast in the contest.
    
From SHANGRLA, section 3.4:

    Let NC denote an upper bound on the number of ballot cards that contain the contest. 
    Suppose that n ≤ NC CVRs contain the contest and that each of those CVRs is associated with a unique,
    identifiable physical ballot card that can be retrieved if that CVR is selected for audit.
    
    If NC > n, create NC − n “phantom ballots” and NC − n “phantom CVRs.” Calculate the assorter mean for all the CVRs,
    including the phantoms by treating the phantom CVRs as if they contain no valid vote in the contest contest 
    (i.e., the assorter assigns the value 1/2 to phantom CVRs). Find the corresponding assorter margin (v ≡ 2Ā − 1).
    
    To conduct the audit, sample integers between 1 and NC.
    
    1. If the resulting integer is between 1 and n, retrieve and inspect the ballot card associated with the corresponding CVR.
        1. If the associated ballot contains the contest, calculate the overstatement error as in (SHANGRLA eq 2, above).
        2. If the associated ballot does not contain the contest, calculate the overstatement error using the value the 
           assorter assigned to the CVR, but as if the value the assorter assigns to the physical ballot is zero
           (that is, the overstatement error is equal to the value the assorter assigned to the CVR).
       2. If the resulting integer is between n + 1 and NC , we have drawn a phantom CVR and a phantom ballot. Calculate the
          overstatement error as if the value the assorter assigned to the phantom ballot was 0 (turning the phantom into an “evil zombie”),
          and as if the value the assorter assigned to the CVR was 1/2.
    
    Some jurisdictions, notably Colorado, redact CVRs if revealing them might compromise
    vote anonymity. If such CVRs are omitted from the tally and the number of phantom
    CVRs and ballots are increased correspondingly, this approach still leads to a valid RLA.
    But if they are included in the tally, then if they are selected for audit they should be
    treated as if they had the value u TODO (the largest value the assorter can assign) in calculating
    the overstatement error.

_This is in the code but not testes yet TODO. See ComparisonAssorter.bassort()._


## Stratified audits using OneAudit (TODO)

Deal with one contest at a time for now.

````
Let bi denote the true votes on the ith ballot card; there are N ballots in all. 
Let ci denote the voting system’s interpretation of the ith card. Suppose we
have a CVR ci for every ballot card whose index i is in C. The cardinality of C is
|C|. Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {G_g} g=1..G for
which reported assorter subtotals are available. For instance Gg might comprise
all ballots for which no CVR is available or all ballots cast in a particular precinct.

A(bi) is the assort value of the ith ballot, Ā(b) its average value = Sum(A(bi))/N over all ballots
A(ci) is the assort value of the ith CVR, Ā(c) its average value = Sum(A(ci))/N over all ballots (note using N) 
With subscript its just over that set Ā_Gg(c) = Sum(A(ci)) / |Gg|, ci in Gg.
````

Using a “mean CVR” for the batch is overstatement-net-equivalent to any CVRs that give the same assorter batch subtotals.
````
    v ≡ 2Ā(c) − 1, the reported _assorter margin_, aka the _diluted margin_.

    Ā(b) > 1/2 iff

    Sum(A(ci) - A(bi)) / N < v / 2   (5)

Following SHANGRLA Section 3.2 define

    B(bi) ≡ (upper + A(bi) - A(ci)) / (2*upper - v)  in [0, 2*upper/(2*upper - v)] (6)

    and Ā(b) > 1/2 iff B̄(b) > 1/2

    see OneAudit section 2.3
````

See "Algorithm for a CLCA using ONE CVRs from batch subtotals" in Section 3.
````
This algorithm can be made more efficient statistically and logistically in a variety
of ways, for instance, by making an affine translation of the data so that the
minimum possible value is 0 (by subtracting the minimum of the possible over-
statement assorters across batches and re-scaling so that the null mean is still
1/2) and by starting with a sample size that is expected to be large enough to
confirm the contest outcome if the reported results are correct.
````
See "Auditing heterogenous voting systems" Section 4 for comparision to SUITE
````
The statistical tests used in RLAs are not affine equivariant because
they rely on a priori bounds on the assorter values. The original assorter values
will generally be closer to the endpoints of [0, u] than the transformed values
are to the endpoints of [0, 2u/(2u − v)]

An affine transformation of the overstatement assorter values can move them back to the endpoints of the support
constraint by subtracting the minimum possible value then re-scaling so that the
null mean is 1/2 once again, which reproduces the original assorter.
````

## Differences with SHANGRLA

### Limit audit to estimated samples

SHANGRLA consistent_sampling() in Audit.py only audits with the estimated sample size. However, in multiple
contest audits, additional ballots may be in the sample because they are needed by another contest. Since theres no 
guarentee that the estimated sample size is large enough, theres no reason not to include all the available mvrs in the audit.

### compute sample size

From STYLISH paper:

        4.a) Pick the (cumulative) sample sizes {𝑆_𝑐} for 𝑐 ∈ C to attain by the end of this round of sampling.
        The software offers several options for picking {𝑆_𝑐}, including some based on simulation.
        The desired sampling fraction 𝑓_𝑐 := 𝑆_𝑐 /𝑁_𝑐 for contest 𝑐 is the sampling probability
            for each card that contains contest 𝑘, treating cards already in the sample as having sampling probability 1.
        The probability 𝑝_𝑖 that previously unsampled card 𝑖 is sampled in the next round is the largest of those probabilities:
            𝑝_𝑖 := max (𝑓_𝑐), 𝑐 ∈ C ∩ C𝑖, where C_𝑖 denotes the contests on card 𝑖.
        4.b) Estimate the total sample size to be Sum(𝑝_𝑖), where the sum is across all cards 𝑖 except phantom cards.

AFAICT, the calculation of the total_size using the probabilities as described in 4.b) is only used when you just want the
total_size estimate, but not do the consistent sampling. Also maybe only works whne you use sampleThreshold ??

### estimate comparison error rates

SHANGRLA has guesses for p1,p2,p3,p4. We do a blanket fuzz, and simulate the errors by ncandidates in a contest, then use those.

### use of previous round's sampled_cvr_indices

At first glance, it appears that SHANGRLA Audit.py CVR.consistent_sampling() might make use of the previous round's
selected ballots (sampled_cvr_indices). However, it looks like CVR.consistent_sampling() never uses sampled_cvr_indices, 
and so uses the same strategy as we do of sampling without regards to the previous rounds.

Its possible that the code is wrong when sampled_cvr_indices is passed in, since the sampling doesnt just use the 
first n sorted samples, which the code seems to assume. But I think the question is moot.


## Other Notes

* [ALPHA testing statistic](docs/AlphaMart.md)
* [Notes on Corla](docs/Corla.md)

## Development Notes

* [RLA Options](docs/RlaOptions.md)
* [TODO](docs/Development.md)