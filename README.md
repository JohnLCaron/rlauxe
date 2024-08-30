# rlauxe
last update: 11/30/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

WORK IN PROGRESS

Table of Contents
<!-- TOC -->
* [rlauxe](#rlauxe)
  * [Papers](#papers)
  * [SHANGRLA framework](#shangrla-framework)
    * [Comparison audits vs polling audits](#comparison-audits-vs-polling-audits)
    * [Missing Ballots (aka phantoms-to-evil zombies))](#missing-ballots-aka-phantoms-to-evil-zombies)
    * [Assorters and supported SocialChoice Functions](#assorters-and-supported-socialchoice-functions)
      * [PLURALITY](#plurality)
      * [APPROVAL](#approval)
      * [SUPERMAJORITY](#supermajority-)
      * [IRV](#irv)
  * [ALPHA testing statistic](#alpha-testing-statistic)
    * [Sampling with or without replacement](#sampling-with-or-without-replacement)
    * [Truncated shrinkage estimate of the population mean](#truncated-shrinkage-estimate-of-the-population-mean)
    * [BRAVO testing statistic](#bravo-testing-statistic)
  * [Stratified audits using OneAudit](#stratified-audits-using-oneaudit)
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

| term      | definition                                                                                    |
|-----------|-----------------------------------------------------------------------------------------------|
| N 	       | the number of ballot cards validly cast in the contest                                        |
| risk	     | we want to confirm or reject the null hypothesis with risk level α.                           |
| assorter  | assigns a number between 0 and upper to each ballot, chosen to make assertions "half average". |
| assertion | the mean of assorter values is > 1/2: "half-average assertion"                                |
| estimator | estimates the true population mean from the sampled assorter values.                          |
| test      | is the statistical method to test if the assertion is true. aka "risk function".              |
| audit     | iterative process of picking ballots and checking if all the assertions are true.             |

### Comparison audits vs polling audits

A polling audit retrieves a physical ballot and the auditors manually agree on what it says, creating an MVR (manual voting record) for it.
The assorter assigns an assort value in [0, upper] to the ballot, which is used in the testing statistic.

For comparison audits, the system has already created a CVR (cast vote record) for each ballot which is compared to the MVR.
The overstatement error for the ith ballot is
````
    ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper                 overstatement error (SHANGRLA eq 2, p 9)
      bi is the manual voting record (MVR) for the ith ballot
      ci is the cast-vote record for the ith ballot
      A() is the assorter function
Let
     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
     ω̄ ≡ Sum(ωi)/N = Sum(A(ci) − A(bi))/N be the average overstatement error
     v ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 c ondidate plurality, the _diluted margin_.
     τi ≡ (1 − ωi /upper) ≥ 0
     B(bi, ci) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper)

Then B assigns nonnegative numbers to ballots, and the outcome is correct iff
    B̄ ≡ Sum(B(bi, ci)) / N > 1/2
and so B is an half-average assorter.
````

See SHANGRLA Section 3.2. 

Note that polling vs comparison audits differ only in the assorter function. 


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

### Assorters and supported SocialChoice Functions

A contest has K ≥ 1 winners and C > K candidates. Let wk be the kth winner, and ℓj be the jth loser.
For each pair of winner and loser, let H_wk,ℓj be the assertion that wk is really the winner over ℓj.
There are K(C − K) assertions. The contest can be audited to risk limit α by testing all assertions at significance level α.
Each assertion is tested that the mean of the assorter values is > 1/2 (or not). 


#### PLURALITY

There are K(C − K) assertions. For the case when there is only one winner, there are C - 1 assertions, pairing the winner with each loser. 
For a two candidate election, there is only one assertion to test.

For the ith ballot, define `A_wk,ℓj(bi)` as
````
    assign the value “1” if it has a mark for wk but not for ℓj; 
    assign the value “0” if it has a mark for ℓj but not for wk;
    assign the value 1/2, otherwise.
 ````

For polling, the assorter function is A_wk,ℓj(MVR).

For a comparisian audit, the assorter function is B(bi, ci) as defined above, using this A_wk,ℓj.


#### APPROVAL

In approval voting, voters may vote for as many candidates as they like.

The same algorithm works for approval voting as for plurality voting.


#### SUPERMAJORITY 

A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win.
If multiple winners are allowed, each reported winner generates one assertions.

For the ith ballot, define `A_wk,ℓj(bi)` as
````
    assign the value “1/(2*f)” if it has a mark for wk but no one else; 
    assign the value “0” if it has a mark for exactly one candidate and not Alice
    assign the value 1/2, otherwise.
````

For polling, the assorter function is A_wk,ℓj(MVR).

For a comparisian audit, the assorter function is B(bi, ci) as defined above, using this A_wk,ℓj.


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
`    (N * µ − Sum(X^j-1)/(N − j + 1).
`
If this ever becomes less than zero, the null hypothesis is certainly false. When allowed to sample all N
values without replacement, eventually this value becomes less than zero.

### Truncated shrinkage estimate of the population mean

This estimate function can be anything, but it highly affects the efficiency.

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

Is ALPHA dependent on the ordering of the sample? Maybe no, since multiplication is commutative ?? Depends on estimFn?
    "The draws must be in random order, or the sequence is not a supermartingale under the null"

Is ALPHA dependent on N? Only to test sampleSum > N * t ??

Is sampling without replacement more efficient than with replacement? Should be.

Can we really replicate BRAVO results?

Options
* ContestType: PLURALITY, APPROVAL, SUPERMAJORITY, IRV
* AuditType: POLLING, CARD_COMPARISON, ONEAUDIT 
* SamplingType: withReplacement, withoutReplacement
*
* use_styles: do we know what ballots have which contests? Can sample from just those??
* do we have CVRs for all ballots? with/without phantom ballots
* use batches (cluster sampling)


## Stratified audits using OneAudit

Deal for now with one contest at a time.

Let bi denote the true votes on the ith ballot card; there are N ballots in all. 
Let ci denote the voting system’s interpretation of the ith card. Suppose we
have a CVR ci for every ballot card whose index i is in C. The cardinality of C is
|C|. Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {G_g} g=1..G for
which reported assorter subtotals are available. For instance Gg might comprise
all ballots for which no CVR is available or all ballots cast in a particular precinct.

````
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

This algorithm be made more efficient statistically and logistically in a variety
of ways, for instance, by making an affine translation of the data so that the
minimum possible value is 0 (by subtracting the minimum of the possible over-
statement assorters across batches and re-scaling so that the null mean is still
1/2) and by starting with a sample size that is expected to be large enough to
confirm the contest outcome if the reported results are correct.

See "Auditing heterogenous voting systems" Section 4 for comparision to SUITE

the statistical tests used in RLAs are not affine equivariant because
they rely on a priori bounds on the assorter values. The original assorter values
will generally be closer to the endpoints of [0, u] than the transformed values
are to the endpoints of [0, 2u/(2u − v)]

An affine transformation of the over-
statement assorter values can move them back to the endpoints of the support
constraint by subtracting the minimum possible value then re-scaling so that the
null mean is 1/2 once again, which reproduces the original assorter