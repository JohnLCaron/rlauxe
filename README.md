# rlauxe
last update: 09/10/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

**WORK IN PROGRESS**

Table of Contents
<!-- TOC -->
* [rlauxe](#rlauxe)
  * [Papers](#papers)
  * [SHANGRLA framework](#shangrla-framework)
    * [Assorters and supported SocialChoices](#assorters-and-supported-socialchoices)
      * [PLURALITY](#plurality)
      * [APPROVAL](#approval)
      * [SUPERMAJORITY](#supermajority)
      * [IRV](#irv)
    * [Comparison audits vs polling audits](#comparison-audits-vs-polling-audits)
    * [Missing Ballots (aka phantoms-to-evil zombies))](#missing-ballots-aka-phantoms-to-evil-zombies)
  * [ALPHA testing statistic](#alpha-testing-statistic)
    * [Sampling with or without replacement](#sampling-with-or-without-replacement)
    * [Truncated shrinkage estimate of the population mean](#truncated-shrinkage-estimate-of-the-population-mean)
    * [BRAVO testing statistic](#bravo-testing-statistic)
    * [Questions](#questions)
  * [Stratified audits using OneAudit](#stratified-audits-using-oneaudit)
  * [Sample size simulations (Polling)](#sample-size-simulations-polling)
    * [compare table 3 of ALPHA for Polling Audit with replacement](#compare-table-3-of-alpha-for-polling-audit-with-replacement)
    * [how to set the parameter d?](#how-to-set-the-parameter-d)
<!-- TOC -->

## Papers

    SHANGRLA	Sets of Half-Average Nulls Generate Risk-Limiting Audits: SHANGRLA	Stark; 24 Mar 2020
        https://github.com/pbstark/SHANGRLA

    ALPHA	    ALPHA: Audit that Learns from Previously Hand-Audited Ballots	Stark; Jan 7, 2022
        https://github.com/pbstark/alpha.

    ONEAudit    ONEAudit: Overstatement-Net-Equivalent Risk-Limiting Audit	Stark, P.B., 6 Mar 2023.
        https://github.com/pbstark/ONEAudit



## SHANGRLA framework

SHANGRLA is a framework for running [Risk Limiting Audits](https://en.wikipedia.org/wiki/Risk-limiting_audit) (RLA) for elections.
It uses an _assorter_ to assign a number to each ballot, and a _statistical test function_ that allows an audit to statistically
prove that an election outcome is correct (or not) to within a _risk level_, for example within 95% probability.

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

TODO elucidate the supported SocialChoices. Test for allowing multiple winners.

#### PLURALITY

"Top k candidates are elected."
The rules may allow the voter to vote for one candidate, k candidates or some other number, including n (see approval).

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

For polling, the assorter function is A_wk,ℓj(MVR).

For a comparison audit, the assorter function is B(MVR, CVR) as defined below, using this A_wk,ℓj.

Notes
* The candidates, winners and losers are kept in the AuditContest.
* The ?? creates the assertions. Each assertion has a unique assorter.
* Someone has to enforce that each CVR has <= number of allowed votes.


#### APPROVAL

See SHANGRLA, section 2.2.

In approval voting, voters may vote for as many candidates as they like.
The top k candidates are elected.

The same algorithm works for approval voting as for plurality voting.

Notes
* Someone has to enforce that each CVR has <= number of allowed votes.


#### SUPERMAJORITY

See SHANGRLA, section 2.3.

A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win.
If multiple winners are allowed, each reported winner generates one assertions.

For the ith ballot, define `A_wk,ℓj(bi)` as
````
    assign the value “1/(2*f)” if it has a mark for wk but no one else; 
    assign the value “0” if it has a mark for exactly one candidate and not wk
    assign the value 1/2, otherwise.
````
For polling, the assorter function is A_wk,ℓj(MVR).

For a comparisian audit, the assorter function is B(MVR, CVR) as defined below, using this A_wk,ℓj.

One only needs one assorter for each winner, not one for each winner/loser pair.

Note that the third condition means "ignore ballots with multiple votes." 
So multiple winners can only happen when f < 0.5 / multiple.
TODO: allow multiple votes.


#### IRV

Not implemented yet.

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

### Comparison audits vs polling audits

See SHANGRLA Section 3.2.

A polling audit retrieves a physical ballot and the auditors manually agree on what it says, creating an MVR (manual voting record) for it.
The assorter assigns an assort value in [0, upper] to the ballot, which is used in the testing statistic.

For comparison audits, the system has already created a CVR (cast vote record) for each ballot which is compared to the MVR.
The overstatement error for the ith ballot is
````
    ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper    overstatement error (SHANGRLA eq 2, p 9)
      bi is the manual voting record (MVR) for the ith ballot
      ci is the cast-vote record for the ith ballot
      A() is the assorter function
Let
     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
     v ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, the _diluted margin_).
     τi ≡ (1 − ωi /upper) ≥ 0
     B(bi, ci) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper) ≡ "comparison assorter" ≡ B(MVR, CVR)

Then B assigns nonnegative numbers to ballots, and the outcome is correct iff
    B̄ ≡ Sum(B(bi, ci)) / N > 1/2
and so B is an half-average assorter.
````

Notes 
* polling vs comparison audits differ only in the assorter function.
* The comparison assorter B needs Ā(c) ≡ the average CVR assort value.
* Ā(c) should have the diluted margin as the denominator.
(Margins are  traditionally calculated as the difference in votes divided by the number of valid votes.
Diluted refers to the fact that the denominator is the number of ballot cards, which is
greater than or equal to the number of valid votes.)

This algorithm seems to be less efficient than polling. The margins are approximately half, so take much longer.

For example, for theta = .51, and the CVRs agree exactly with the MVRs (all overstatements == 0),
the avg number of samples went from 12443 to 16064 (N=50000), and 5968 to 7044 (N=10000).

This is because the SHANGRLA comparison assorter reduces the margin by approx half: 

theta    margin    B       Bmargin  Bmargin/margin
0.5050   0.0100   0.5025   0.0050   0.5025
0.5100   0.0200   0.5051   0.0101   0.5051
0.5200   0.0400   0.5102   0.0204   0.5102
0.5300   0.0600   0.5155   0.0309   0.5155
0.5400   0.0800   0.5208   0.0417   0.5208
0.5500   0.1000   0.5263   0.0526   0.5263
0.5750   0.1500   0.5405   0.0811   0.5405
0.6000   0.2000   0.5556   0.1111   0.5556
0.6500   0.3000   0.5882   0.1765   0.5882
0.7000   0.4000   0.6250   0.2500   0.6250

Tables 7 and 8 in ALPHA, showing comparison ballot results, switch from using theta to using "mass at 1". So hard to compare.

However, see TestComparisonFromAlpha.kt; setting eta0 very high brings the numbers down such that comparison is much better
than polling, and can deal even with very small margins:

````
    //  nsamples, ballot comparison, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9955,   9766,   9336,   8571,   7461,   2257,    464,    221,    140,    101,     59,     41,     25,     18,
    // 1.000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
    // 1.500,   9916,   9014,   5954,   3189,   1827,    418,    153,     98,     74,     59,     39,     29,     19,     14,
    // 2.000,   9825,   6722,   2923,   1498,    937,    309,    148,     98,     74,     59,     39,     29,     19,     14,
    // 5.000,   5173,   1620,    962,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 7.500,   3310,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //10.000,   2765,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //15.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //20.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
````
````
nsamples, ballot comparison, eta0=20.0, d = 100, error-free
theta (col) vs N (row)
,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
1000,    951,    777,    631,    527,    450,    258,    138,     94,     71,     57,     38,     29,     19,     14,
5000,   2253,   1294,    904,    695,    564,    290,    147,     98,     73,     59,     39,     29,     19,     14,
10000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
20000,   2781,   1442,    973,    734,    589,    296,    148,     99,     74,     59,     39,     29,     19,     14,
50000,   2907,   1475,    988,    742,    595,    298,    149,     99,     74,     59,     39,     29,     19,     14,

pct nsamples, ballot comparison, eta0=20.0, d = 100, error-free
theta (col) vs N (row)
,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
1000,  95.10,  77.70,  63.10,  52.70,  45.00,  25.80,  13.80,   9.40,   7.10,   5.70,   3.80,   2.90,   1.90,   1.40,
5000,  45.06,  25.88,  18.08,  13.90,  11.28,   5.80,   2.94,   1.96,   1.46,   1.18,   0.78,   0.58,   0.38,   0.28,
10000,  25.88,  13.90,   9.49,   7.21,   5.81,   2.94,   1.48,   0.98,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14,
20000,  13.91,   7.21,   4.87,   3.67,   2.95,   1.48,   0.74,   0.50,   0.37,   0.30,   0.20,   0.15,   0.10,   0.07,
50000,   5.81,   2.95,   1.98,   1.48,   1.19,   0.60,   0.30,   0.20,   0.15,   0.12,   0.08,   0.06,   0.04,   0.03,
````

* why does setting eta0 high improve this?
* Need to test this when there are errors in the CRVs.
* Also, may need to adjust upper for comparison audits. 
* Note bug in ALPHA python code for alpha mart.


### Missing Ballots (aka phantoms-to-evil zombies))

(This seems to apply only to ballot comparision)

To conduct a RLA, it is crucial to have an upper bound on the total number of ballot cards cast in the contest.

Let NC denote an upper bound on the number of ballot cards that contain the contest. 
Suppose that n ≤ NC CVRs contain the contest and that each of those CVRs is associated with a unique,
identifiable physical ballot card that can be retrieved if that CVR is selected for audit.

If NC > n, create NC − n “phantom ballots” and NC − n “phantom CVRs.” Calculate the assorter mean for all the CVRs,
including the phantoms by treating the phantom CVRs as if they contain no valid vote in the contest contest 
(i.e., the assorter assigns the value 1/2 to phantom CVRs). 
Find the corresponding assorter margin (v ≡ 2Ā − 1).

To conduct the audit, sample integers between 1 and NC.

1. If the resulting integer is between 1 and n, retrieve and inspect the ballot card associated with the corresponding CVR.
    1. If the associated ballot contains the contest, calculate the overstatement error as in (SHANGRLA eq 2, above).
    2. If the associated ballot does not contain the contest, calculate the overstatement error using the value the 
       assorter assigned to the CVR, but as if the value the assorter assigns to the physical ballot is zero
       (that is, the overstatement error is equal to the value the assorter assigned to the CVR).
2. If the resulting integer is between n + 1 and NC , we have drawn a phantom CVR and a phantom ballot. Calculate the
   overstatement error as if the value the assorter assigned to the phantom ballot was 0 (turning the phantom into an “evil zombie”),
   and as if the value the assorter assigned to the CVR was 1/2.

See note in SHANGRLA Section 3.4 on Colorado redacted ballots.

Also theres "use_styles" which gets a tighter bound when you know what ballots have which contests.

    use_style: is the sample drawn only from ballots that should contain the contest?

see overstatement_assorter() in core/Assertion

    assorter that corresponds to normalized overstatement error for an assertion

    If `use_style == true`, then if the CVR contains the contest but the MVR does not,
    that is considered to be an overstatement, because the ballot is presumed to contain
    the contest .

    If `use_style == False`, then if the CVR contains the contest but the MVR does not,
    the MVR is considered to be a non -vote in the contest .

See "Limiting Risk by Turning Manifest Phantoms into Evil Zombies"

    What if the ballot manifest is not accurate?
    it suffices to make worst-case assumptions about the individual randomly selected ballots that the audit cannot find.
    requires only an upper bound on the total number of ballots cast
    This ensures that the true risk limit remains smaller than the nominal risk limit.

    A listing of the groups of ballots and the number of ballots in each group is called a ballot manifest.
    designing and carrying out the audit so that each ballot has the correct probability of being selected involves the ballot manifest.

## ALPHA testing statistic

ALPHA is a risk-measuring function that adapts to the drawn sample as it is made.
ALPHA estimates the reported winner’s share of the vote before the jth card is drawn from the j-1 cards already in the sample.
The estimator can be any measurable function of the first j − 1 draws, for example a simple truncated shrinkage estimate, described below.
ALPHA generalizes BRAVO to situations where the population {xj} is not necessarily binary, but merely nonnegative and bounded.
ALPHA works for sampling with or without replacement, with or without weights, while BRAVO is specifically for IID sampling with replacement.
````
θ 	        true population mean
Xk 	        the kth random sample drawn from the population.
X^j         (X1 , . . . , Xj) is the jth sequence of samples.

µj          E(Xj | X^j−1 ) computed under the null hypothesis that θ = 1/2. 
            "expected value of the next sample's assorted value (Xj) under the null hypothosis".
            With replacement, its 1/2.
            Without replacement, its the value that moves the mean to 1/2.

η0          an estimate of the true mean before sampling .
ηj          an estimate of the true mean, using X^j-1 (not using Xj), 
            estimate of what the sampled mean of X^j is (not using Xj) ??
            This is the "estimator function". 

Let ηj = ηj (X^j−1 ), j = 1, . . ., be a "predictable sequence": ηj may depend on X^j−1, but not on Xk for k ≥ j.

Tj          ALPHA nonnegative supermartingale (Tj)_j∈N  starting at 1

	E(Tj | X^j-1 ) = Tj-1, under the null hypothesis that θj = µj (7)

	E(Tj | X^j-1 ) < Tj-1, if θ < µ (8)

	P{∃j : Tj ≥ α−1 } ≤ α, if θ < µ (9) (follows from Ville's inequality)
````

### Sampling with or without replacement

We need E(Xj | X^j−1 ) computed with the null hypothosis that θ == µ == 1/2. 

Sampling with replacement means that this value is always µ == 1/2.

For sampling without replacement from a population with mean µ, after draw j - 1, the mean of
the remaining numbers is 
`(N * µ − Sum(X^j-1)/(N − j + 1).`
If this ever becomes less than zero, the null hypothesis is certainly false. When allowed to sample all N
values without replacement, eventually this value becomes less than zero.

### Truncated shrinkage estimate of the population mean

The estimate function can be anything, but it strongly affects the efficiency.

See section 2.5.2 of ALPHA for a function with parameters eta0, c and d.

See SHANGRLA shrink_trunc() in NonnegMean.py for an updated version with additional parameter f.

````
sample mean is shrunk towards eta, with relative weight d compared to a single observation,
then that combination is shrunk towards u, with relative weight f/(stdev(x)).

The result is truncated above at u*(1-eps) and below at m_j+etaj(c,j)
Shrinking towards eta stabilizes the sample mean as an estimate of the population mean.
Shrinking towards u takes advantage of low-variance samples to grow the test statistic more rapidly.

// Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
// (if the sample mean approaches µi or less), we shall take epsi := c/sqrt(d + i − 1) for a nonnegative constant c,
// for instance c = (η0 − µ)/2.
// The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , 1), where ǫi → 0 as the sample size grows.

val weighted = ((d * eta0 + sampleSum) / (d + lastj - 1) + u * f / sdj3) / (1 + f / sdj3)
val npmax = max( weighted, mean2 + c / sqrt((d + lastj - 1).toDouble()))  // 2.5.2 "choosing ǫi"
return min(u * (1 - eps), npmax)
````

````
Choosing d. As d → ∞, the sample size for ALPHA approaches that of BRAVO, for
binary data. The larger d is, the more strongly anchored the estimate is to the reported vote
shares, and the smaller the penalty ALPHA pays when the reported results are exactly correct.
Using a small value of d is particularly helpful when the true population mean is far from the
reported results. The smaller d is, the faster the method adapts to the true population mean,
but the higher the variance is. Whatever d is, the relative weight of the reported vote shares
decreases as the sample size increases.
````
### BRAVO testing statistic

BRAVO is ALPHA with the following restrictions:
* the sample is drawn with replacement from ballot cards that do have a valid vote for the
reported winner w or the reported loser ℓ (ballot cards with votes for other candidates or
non-votes are ignored)
* ballot cards are encoded as 0 or 1, depending on whether they have a valid vote for the
reported winner or for the reported loser; u = 1 and the only possible values of xi are 0
and 1
* µ = 1/2, and µi = 1/2 for all i since the sample is drawn with replacement
* ηi = η0 := Nw /(Nw + Nℓ ), where Nw is the number of votes reported for candidate w and
Nℓ is the number of votes reported for candidate ℓ: η is not updated as data are collected

### Questions

Is ALPHA dependent on the ordering of the sample? YES
_"The draws must be in random order, or the sequence is not a supermartingale under the null"_

Is ALPHA dependent on N? Only to test sampleSum > N * t ?? 
I think this means that one needs the same number of samples for 100, 1000, 1000000 etc. 
So its highly effective (as percentage of sampling) as N increases.

Is sampling without replacement more efficient than with replacement? Should be.

Can we really replicate BRAVO results?

Options
* ContestType: PLURALITY, APPROVAL, SUPERMAJORITY, IRV
* AuditType: POLLING, CARD_COMPARISON, ONEAUDIT 
* SamplingType: withReplacement, withoutReplacement
* use_styles: do we know what ballots have which contests? Can sample from just those??
* do we have CVRs for all ballots? with/without phantom ballots?
* are we using batches (cluster sampling)?


## Stratified audits using OneAudit

Deal with one contest at a time for now .

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

## Sample size simulations (Polling)

Plots are updated here, to fix bug in shrink_trunk estimator function: 

See [9/4/24 plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=662624429#gid=662624429)

* RLA cant help for close elections unless N is large
* seems like this is because sample size is approx. independent of N (Plot 1)
* sample size approx 1/margin as N -> inf. Something more complicated as margin -> 0. (Plot 2b)
* variance is quite large (Plot 4)

### compare table 3 of ALPHA for Polling Audit with replacement

* eta0 = theta (no divergence of sample from true). 
* 1000 repetitions

nvotes sampled vs theta = winning percent

| N      | 0.510    | 0.520    | 0.530    | 0.540 | 0.550 | 0.575 | 0.600 | 0.650 | 0.7 |
|--------|----------|----------|----------|-------|------|-------|-------|-------|-----|
| 1000   |      897 |      726 |      569 | 446   |  340 | 201   | 128   | 60    | 36  |
| 5000   |     3447 |     1948 |     1223 | 799   |  527 | 256   | 145   | 68    | 38  |
| 10000  |     5665 |     2737 |     1430 | 871   |  549 | 266   | 152   | 68    | 38  |
| 20000  |     8456 |     3306 |     1546 | 926   |  590 | 261   | 154   | 65    | 38  |
| 50000  |    12225 |     3688 |     1686 | 994   |  617 | 263   | 155   | 67    | 37  |


stddev samples vs theta 

| N      | 0.510    | 0.520    | 0.530    | 0.540   | 0.550   | 0.575   | 0.600   | 0.650  | 0.700  |
|--------|----------|----------|----------|---------|---------|---------|---------|--------|--------|
| 1000   | 119.444  | 176.939  | 195.837  | 176.534 | 153.460 | 110.204 | 78.946  | 40.537 | 24.501 |  
| 5000   | 1008.455 | 893.249  | 669.987  | 478.499 | 347.139 | 176.844 | 101.661 | 52.668 | 28.712 |  
| 10000  | 2056.201 | 1425.911 | 891.215  | 583.694 | 381.797 | 199.165 | 113.188 | 52.029 | 27.933 |  
| 20000  | 3751.976 | 2124.064 | 1051.194 | 656.632 | 449.989 | 190.791 | 123.333 | 47.084 | 28.173 |  
| 50000  | 6873.319 | 2708.147 | 1274.291 | 740.712 | 475.265 | 194.538 | 130.865 | 51.086 | 26.439 |

* no use for the parameter d in this case. Likely thats is used only for when eta0 != theta

### how to set the parameter d?

From ALPHA (p 9)

````
Choosing d. As d → ∞, the sample size for ALPHA approaches that of BRAVO, for
binary data. The larger d is, the more strongly anchored the estimate is to the reported vote
shares, and the smaller the penalty ALPHA pays when the reported results are exactly correct.
Using a small value of d is particularly helpful when the true population mean is far from the
reported results. The smaller d is, the faster the method adapts to the true population mean,
but the higher the variance is. Whatever d is, the relative weight of the reported vote shares
decreases as the sample size increases.
````

See [output](docs/DiffMeanOutput.txt) of DiffMeans.kt and PlotDiffMeans.kt. This is done for each value of
N and theta.

* samples size when reported mean != theta (true mean)
* show tables of mean difference = (reported mean - theta) columns vs values of d parameter (rows)
* ntrials = 1000

A few representative plots are at See [meanDiff plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=1185506629#gid=1185506629)

Notes:

* For many values of N and theta, we cant help (margin too small; N too small); or it doesnt matter much (margin large, N large).
* Ive chosen a few plots where values of N and theta have pct samples 10 - 30%, since thats where improvements might matter for having a successful RLA vs a full hand recount.
* High values of d work well when reported mean ~= theta. 
* Low values of d work better as mean difference = (reported mean - theta) grows.
* The question is, how much weight to give "outliers", at the expense of improving success rate for "common case" of reported mean ~= theta ?

To Investigate
* Does it makes sense to use small values of d for large values of reported mean? because it will only matter if (reported mean - theta) is large.
* Sample percents get higher as theta -> 0. Can we characterize that?
* Number of samples is independent of N as N -> inf.
