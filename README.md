# rlauxe
last update: 11/25/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

**WORK IN PROGRESS**

Read this on [github.io](https://johnlcaron.github.io/rlauxe/) in order to see the rendered plots.

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
  * [Sampling](#sampling)
    * [Estimating Sample sizes (in progress)](#estimating-sample-sizes-in-progress)
    * [Consistent Sampling](#consistent-sampling)
    * [Use Styles](#use-styles)
    * [Missing Ballots (aka phantoms-to-evil zombies))](#missing-ballots-aka-phantoms-to-evil-zombies)
  * [Stratified audits using OneAudit (TODO)](#stratified-audits-using-oneaudit-todo)
  * [Differences with SHANGRLA](#differences-with-shangrla)
    * [Limit audit to estimated samples](#limit-audit-to-estimated-samples)
    * [compute sample size](#compute-sample-size)
  * [Plots](#plots)
    * [Polling Vs Comparison Estimated Sample sizes](#polling-vs-comparison-estimated-sample-sizes)
    * [Comparison Sample sizes with fuzz](#comparison-sample-sizes-with-fuzz)
  * [Notes](#notes)
  * [Development Notes](#development-notes)
<!-- TOC -->

## Reference Papers

    P2Z         Limiting Risk by Turning Manifest Phantoms into Evil Zombies. Banuelos and Stark. July 14, 2012

    RAIRE       Risk-Limiting Audits for IRV Elections.			Blom, Stucky, Teague    29 Oct 2019
        https://arxiv.org/abs/1903.08804

    SHANGRLA	Sets of Half-Average Nulls Generate Risk-Limiting Audits: SHANGRLA.	Stark, 24 Mar 2020
        https://github.com/pbstark/SHANGRLA

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


## SHANGRLA framework

SHANGRLA is a framework for running [Risk Limiting Audits](https://en.wikipedia.org/wiki/Risk-limiting_audit) (RLA) for elections.
It uses an _assorter_ to assign a number to each ballot, and a _statistical risk testing function_ that allows an audit to statistically
prove that an election outcome is correct (or not) to within a _risk level_, for example with 95% probability.

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

Waudby-Smith and Ramdas develop tests and confidence sequences for the mean of a bounded population using 
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

### Comparison audits

The requirements for Comparison audits:

* The election system must be able to generate machine-readable Cast Vote Records (CVRs) for each ballot, which is compared to the MVR during the audit.
* Unique identifier must be assigned to each physical ballot, and put on the CVR, in order to find the physical ballot that matches the sampled CVR. 
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest.

For the risk function, we use BettingMart with AdaptiveComparison. AdaptiveComparison needs estimates of the rates of 
over(under)statements. If these estimates are correct, one gets optimal sample sizes. 
AdaptiveComparison uses a variant of ShrinkTrunkage that uses a weighted average of initial estimates (aka priors) with the actual sampled rates.

See SHANGRLA Section 3.2.

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
* The possible values of the bassort function are:
      {0, .5, 1, 1.5, 2} * noerror
* When the cvrs always equal the corresponfing mvr, we always get bassort == noerror > .5, so eventually the null is rejected.

#### Comparison Betting Payoffs

For the jth sample with bet λ_i, the BettingMart payoff is

    t_i = 1 + λ_i * (X_i − µ_i)

where

    λ_i in [0, 1/u_i]
    X_i = {0, .5, 1, 1.5, 2} * noerror for {2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder} respectively.
    µ_i ~= 1/2
    λ_i ~in [0, 2]

then 

    payoff = t_i = 1 + λ_i * noerror * {-.5, 0, .5, 1.5}

Using AdaptiveComparison, λ_i depends only on the 4 estimated error rates and the margin. 

Plots 1-5 shows the betting payoffs when all 4 error rates equal {0.0, 0.0001, .001, .005, .01}:

* [BettingPayoff error=0.0](docs/plots/betting/BettingPayoff0.0.html)
* [BettingPayoff error=0.0001](docs/plots/betting/BettingPayoff1.0E-4.html)
* [BettingPayoff error=0.001](docs/plots/betting/BettingPayoff0.001.html)
* [BettingPayoff error=0.005](docs/plots/betting/BettingPayoff0.005.html)
* [BettingPayoff error=0.01](docs/plots/betting/BettingPayoff0.01.html)

Plot 6 shows the payoffs for all the error rates when the MVR matches the CVR (assort value = 1.0 * noerror):

* [BettingPayoff when MVR matches the CVR](docs/plots/betting/BettingPayoffAssort1.0.html)

Plot 7 translates the payoff into a sample size, when there are no errors, using (payoff)^sampleSize = 1 / riskLimit and
solving for sampleSize = -ln(riskLimit) / ln(payoff).

* [Betting SampleSize](docs/plots/betting/BettingPayoffSampleSize.html)

The plot "error=0.0" is the equivilent to COBRA Fig 1, p. 6 for risk=.05. This is the best that can be done, 
the minimum sampling size for the RLA.
Note that this value is independent of N, the number of ballots.

#### Comparison error rates

The assumptions that one makes about the comparison error rates greatly affect the sample size estimation.

ComparisonSamplerSimulation creates modified mvrs from a set of cvrs, with possibly non-zero valeus for errors p1, p2, p3, and p4.
This works for both Plurality and IRV comparison audits.
If p1 == p3, and p2 == p4, the margin stays the same. Call this fuzzed simulation.
Current defaults rather arbitrarily chosen are:

        val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; voted for other, cvr has winner
        val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
        val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; voted for winner, cvr has other
        val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; voted for winner, cvr has loser

FOr IRV, the corresponding decriptions of the errror rates are:

    NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    NEB two vote understatement: cvr has loser preceeding winner(0), mvr has winner as first pref (1)
    NEB one vote understatement: cvr has winner preceding loser, but not first (1/2), mvr has winner as first pref (1)
    
    NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining  (1)

TODO: Compare the sample sizes of fuzzed simulations with the case of all errors == 0, at different margins.
We expect the spread to increase, but also shift to larger samples sizes, since the cost of overstatement is higher than understatements.

If the errors are from random processes, its possible that margins remain approx the same, but also possible that some rates
are more likely to be affected than others. Itw worth noting that these rates combine machine errors with human errors of
fetching and interpreting ballots.

In any case, currrently all assumptions on the a-priori error rates are arbitrary. These need to be measured for existing
machines and practices. While these do not affect the reliabilty of the audit, they have a strong impact on the estimated sample sizes.

## Sampling

SHANGRLA provides a very elegant separation between the implementation of risk testing (mostly described
above) and sampling.

### Estimating Sample sizes (in progress)

For each contest assertion we estimate the needed sample size. The contest sample_size is then the maximum of those.

Consistent sampling (see below) then figures out which CVRs are chosen to satisfy all of the contests being audited.

Note 1: "The software offers several options for picking {𝑆_𝑐}, including some based on simulation."
SHANGRLA doesnt seem to have any non-simulation options. May be a terminology issue.


### Consistent Sampling

TODO: describe algorithm.

We implement only sampling without replacement. See ConsistentSampling.kt.

When there are additional rounds, each round does its own consistent sampling without regards to the previous
rounds. Since the seed remains the same, the sort is the same, and so previously founds MVRS are used as much as possible.

Note that the code in SHANGRLA Audit.py CVR.consistent_sampling() never uses sampled_cvr_indices, so adopts the
same strategy (sampling without regards to the previous rounds). Its possible that the code is wrong when sampled_cvr_indices 
is passed in, since the sampling doesnt just use the first n sorted samples, which the code seems to assume. But I think the question is moot.

I _think_ its fine if more ballots come in between rounds. Just add to the "all cvrs list". Ideally N_c doesnt change,
so it just makes less evil zombies.

### Use Styles

* See "More style, less work: card-style data decrease risk-limiting audit sample sizes" Glazer, Spertus, Stark; 6 Dec 2020
* See "Stylish Risk-Limiting Audits in Practice" Glazer, Spertus, Stark;  16 Sep 2023

This gives a much tighter bound when you know what cards/ballots have which contests, since you can restrict your sampling
to just those contests that are being audited.

"Instead of sampling cards uniformly at random, the method uses card-style data (CSD) and consistent sampling"

Without CSD "you basically have to pull 20 times the sample size". And yet all the CSD information is there in
the election system. I'm inclined to say "you have to have CSD" to use our library (effectively).
Then work with the vendors to make that a reality. We can do the work ourselves and give it to them.
If the practical difference is so big, a production library can be more assertive in telling the user what they have to do.

We implement only with CSD currently.

See overstatement_assorter() in core/Assertion

    assorter that corresponds to normalized overstatement error for an assertion

    If `use_style == true`, then if the CVR contains the contest but the MVR does not,
    that is considered to be an overstatement, because the ballot is presumed to contain
    the contest .

    If `use_style == False`, then if the CVR contains the contest but the MVR does not,
    the MVR is considered to be a non-vote in the contest .


### Missing Ballots (aka phantoms-to-evil zombies))

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

_In the code for ballot comparison but not polling yet TODO. See ComparisonAssorter.bassort()._


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

## Plots

These plots have only two-person ballots.

### Polling Vs Comparison Estimated Sample sizes

1. [Polling Vs Comparison Estimated Sample sizes](docs/plots/EstimateSampleSize.html)

### Comparison Sample sizes with fuzz

Estimated sample size for Comparison Audits. The MVRs are "fuzzed" by taking fuzzPct of the ballopts and randomly 
changing the candidate voted for. fuzzPct = 0.0 means the cvrs and mvrs agree.

* [Comparison Sample sizes with fuzz](docs/plots/ComparisonFuzzSampleSizeConcurrent.html)

This result is a mixture of contest sizes. empty votes are allowed.
````
fuzzPct = 0.001
avgRates = [0.9992159882654829, 2.845617895122841E-4, 1.386138613861406E-4, 2.1763843050971667E-4, 1.4319765309864335E-4]
error% = [999.2159882654829, 0.2845617895122841, 0.1386138613861406, 0.21763843050971668, 0.14319765309864335]
fuzzPct = 0.005
avgRates = [0.9950672900623334, 0.0017207554088742374, 0.0010907590759075846, 0.0012455078841217438, 8.756875687568617E-4]
error% = [199.01345801246669, 0.3441510817748475, 0.21815181518151694, 0.24910157682434875, 0.17513751375137235]
fuzzPct = 0.01
avgRates = [0.9909070407040654, 0.0030907590759076255, 0.0019191419141913585, 0.0024609460946094313, 0.001622112211221137]
error% = [99.09070407040655, 0.30907590759076253, 0.19191419141913585, 0.24609460946094314, 0.1622112211221137]
fuzzPct = 0.02
avgRates = [0.9813668866886412, 0.006589842317564948, 0.0038734873487348905, 0.00507059039237249, 0.003099193252658597]
error% = [49.06834433443206, 0.3294921158782474, 0.19367436743674454, 0.2535295196186245, 0.15495966263292985]
fuzzPct = 0.05
avgRates = [0.9517691602493656, 0.016377887788778477, 0.010581591492482438, 0.012896773010634483, 0.008374587458745916]
error% = [19.035383204987312, 0.32755775577556956, 0.21163182984964876, 0.25793546021268965, 0.16749174917491833]
````
A two-candidate contest has significantly higher two-vote error rates, since its more likely to flip a vote than vote for other.

## Notes

* [Simulations](docs/Simulations.md)
* [Ballot Comparison using Betting Martingales](docs/Betting.md)
* [ALPHA testing statistic](docs/AlphaMart.md)
* [Notes on Corla](docs/Corla.md)

## Development Notes

* [RLA Options](docs/RlaOptions.md)
* [TODO](docs/Development.md)