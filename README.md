# rlauxe
last update: 11/08/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

**WORK IN PROGRESS**

Read this on github.io : https://johnlcaron.github.io/rlauxe/

Table of Contents
<!-- TOC -->
* [rlauxe](#rlauxe)
  * [Reference Papers](#reference-papers)
  * [SHANGRLA framework](#shangrla-framework)
    * [Assorters and supported SocialChoices](#assorters-and-supported-socialchoices)
      * [PLURALITY](#plurality)
      * [APPROVAL](#approval)
      * [SUPERMAJORITY](#supermajority)
      * [IRV (In Progress)](#irv-in-progress)
    * [Comparison audits](#comparison-audits)
    * [Missing Ballots (aka phantoms-to-evil zombies))](#missing-ballots-aka-phantoms-to-evil-zombies)
  * [Use Styles](#use-styles)
    * [Implementation](#implementation)
  * [Stratified audits using OneAudit (TODO)](#stratified-audits-using-oneaudit-todo)
  * [Simulation Results](#simulation-results)
  * [RLA Options](#rla-options)
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
It uses an _assorter_ to assign a number to each ballot, and a _statistical test function_ that allows an audit to statistically
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

| term      | definition                                                                                     |
|-----------|------------------------------------------------------------------------------------------------|
| N         | the number of ballot cards validly cast in the contest                                         |
| risk	     | we want to confirm or reject the null hypothesis with risk level α.                            |
| assorter  | assigns a number between 0 and upper to each ballot, chosen to make assertions "half average". |
| assertion | the mean of assorter values is > 1/2: "half-average assertion"                                 |
| estimator | estimates the true population mean from the sampled assorter values.                           |
| test      | is the statistical method to test if the assertion is true. aka "risk function".               |
| audit     | iterative process of picking ballots and checking if all the assertions are true.              |


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
* multiple winners are allowed


#### IRV (In Progress)

See
````
    Blom, M., Stuckey, P.J., Teague, V.: Risk-limiting audits for irv elections. 
    arXiv:1903.08804 (2019), https://arxiv.org/abs/1903.08804
````
and possibly
````
    Ek, Stark, Stuckey, Vukcevic: Adaptively Weighted Audits of Instant-Runoff Voting Elections: AWAIRE
    5 Oct 2023
````
See code in raire package for current implementation.

### Comparison audits

See SHANGRLA Section 3.2.

A polling audit retrieves a physical ballot and the auditors manually agree on what it says, creating an MVR (manual voting record) for it.
The assorter assigns an assort value in [0, upper] to the ballot, which is used in the testing statistic.

For comparison audits, the system has already created a CVR (cast vote record) for each ballot, which is compared to the MVR.
The overstatement error for the ith ballot is
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

* polling vs comparison audits differ in the assorter function and the testing function.
* The comparison assorter B needs Ā(c) ≡ the average CVR assort value > 0.5.
* Ā(c) should have the diluted margin as the denominator. 
    (Margins are  traditionally calculated as the difference in votes divided by the number of valid votes.
    Diluted refers to the fact that the denominator is the number of ballot cards, which is
    greater than or equal to the number of valid votes.)
* If overstatement error is always zero (no errors in CRV), the assort value is always
  ````
      noerror = 1 / (2 - margin/assorter.upperBound()) 
              = 1 / (3 - 2 * awinnerAvg/assorter.upperBound())
              > 0.5 since awinnerAvg > 0.5
  ````
* The possible values of the bassort function are:
      {0, .5, 1, 1.5, 2} * noerror
* When cvr = mvr, we always get bassort == noerror > .5, so eventually the null is rejected.
* However the convergence is slower than for polling (!), unless one "amplifies" the estimate function.
  See [Ballot Comparison using Betting Martingales](docs/Betting.md) that uses betting strategies to do so.
  See BettingMart and related code for current implementation.


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

## Use Styles

_In the code but not tested yet TODO._

See "More style, less work: card-style data decrease risk-limiting audit sample sizes" Glazer, Spertus, Stark; 6 Dec 2020
See "Stylish Risk-Limiting Audits in Practice" Glazer, Spertus, Stark;  16 Sep 2023

This gets a much tighter bound when you know what ballots have which contests.

"Instead of sampling cards uniformly at random, the method uses card-style data (CSD) and consistent sampling"

Without CSD "you basically have to pull 20 times the sample size". And yet all the CSD information is there in 
the election system. I'm inclined to say "you have to have CSD" to use our library (effectively). 
Then work with the vendors to make that a reality. We can do the work ourselves and give it to them. 
If the practical difference is so big, a production library can be more assertive in telling the user what they have to do.

### Implementation

see overstatement_assorter() in core/Assertion

    assorter that corresponds to normalized overstatement error for an assertion

    If `use_style == true`, then if the CVR contains the contest but the MVR does not,
    that is considered to be an overstatement, because the ballot is presumed to contain
    the contest .

    If `use_style == False`, then if the CVR contains the contest but the MVR does not,
    the MVR is considered to be a non -vote in the contest .


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

## Simulation Results

* [Simulations](docs/Simulations.md)
* [Ballot Comparison using Betting Martingales](docs/Betting.md)
* [ALPHA testing statistic](docs/AlphaMart.md)
* [Notes on Corla](docs/Corla.md)

## RLA Options

* [RLA Options](docs/RlaOptions.md)