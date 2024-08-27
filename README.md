# rlauxe
last update: 11/27/2024

A port of Philip Stark's SHANGRLA framework and related code to kotlin, 
for the purpose of making a reusable and maintainable library.

WORK IN PROGRESS

## Papers

    SHANGRLA	Sets of Half-Average Nulls Generate Risk-Limiting Audits: SHANGRLA	Stark; 24 Mar 2020
        https://github.com/pbstark/SHANGRLA

    ALPHA	    ALPHA: Audit that Learns from Previously Hand-Audited Ballots	Stark; Jan 7, 2022
        https://github.com/pbstark/alpha.

    ONEAudit    ONEAudit: Overstatement-Net-Equivalent Risk-Limiting Audit	Stark, P.B., 6 Mar 2023.
            https://github.com/pbstark/ONEAudit

## SHANGRLA framework

SHANGRLA checks outcomes by testing half-average assertions, each of which claims that the mean of a finite list of numbers between 0 and upper is greater than 1/2.
Each list of numbers results from applying an _assorter_ to the ballot cards. The assorter uses the votes and possibly other information 
(e.g., how the voting system interpreted the ballot) to assign a number between to each ballot.

SHANGRLA tests the negation of each assertion, the complementary null hypothesis that each assorter mean is not greater than 1/2.
If that hypothesis is rejected for every assertion, the audit concludes that the outcome is correct.
Otherwise, the audit expands, potentially to a full hand count. If every null is tested at level α, this results in a risk-limiting audit with risk limit α:
if the outcome is not correct, the chance the audit will stop shy of a full hand count is at most α.
The test must be valid for the composite hypothesis θ ≤ µ.

This formulation unifies polling audits and comparison audits, with or without replacement.
It might be drawn from the population as a whole (unstratified sampling), or the population might be divided into strata, each of which is sampled independently (stratified sampling).
It might be drawn using Bernoulli sampling, where each item is included independently, with some common probability.
Or batches of ballot cards might be sampled instead of individual cards (cluster sampling), with equal or unequal probabilities.

|           | definition                                                                                     |
|-----------|------------------------------------------------------------------------------------------------|
| N 	     | the number of ballot cards validly cast in the contest                                         |
| risk	     | we want to confirm or reject the null hypothesis with risk limit α.                            |
| assorter  | assigns a number between 0 and upper to each ballot, chosen to make assertions "half average". |
| assertion | the mean of assorter values is > 1/2: "half-average assertions"                                |
| estimator | estimates the true population mean from the sampled assorter values.                           |
| test      | is the statistical method to test if the assertion is true. aka "risk function".               |
| audit     | iterative process of picking ballots and checking if all the assertions are true.              |

### Comparison audits vs polling audits

A polling audit retrieves a physical ballot and the auditors manually agree on what it should be, creating a MVR (manual voting record) for it.
The assorter assigns an arrort value to it assort: (Cvr) -> Double in [0, upper], which is used in the testing statistic algorithm.

For comparision audits, the system has already created a CVR (cast vote record) for each ballot which is compared to the MVR.
The overstatement error for the ith ballot is
````
    ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper.
      bi is the manual voting record (MVR) for the ith ballot
      ci is the cast-vote record for the ith ballot

Let
     Ā = Sum(A(ci))/N be the average CVR assort value
     ω̄ = Sum(A(ci) − A(bi))/N be the average overstatement error
     τi ≡ 1 − ωi /upper ≥ 0
     v ≡ 2Ā − 1, the reported assorter margin, aka the _diluted margin_.
     B(bi, c) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper)

Then B assigns nonnegative numbers to ballots, and the outcome is correct iff
    B̄ ≡ Sum(B(bi, c)) / N > 1/2
and B is an assorter.
````

See SHANGRLA Section 3.2. 

So polling vs comparision is all in the assorter function. 

### Assorters and supported SocialChoice Functions

A contest has K ≥ 1 winners and C > K candidates. Let wk be the kth winner, and ℓj be the jth loser.
For each pair of winner and loser, let H_wk,ℓj be the assertion that wk is really the winner over ℓj.
There are K(C − K) hypotheses. The contest can be audited to risk limit α by testing all hypotheses at significance level α.
Each assertion is tested that the mean of the assorter values is > 1/2 (or not).

For the ith ballot, define A_wk,ℓj(bi) as
````
    assign the value “1” if it has a mark for wk but not for ℓj; 
    assign the value “0” if it has a mark for ℓj but not for wk;
    assign the value 1/2, otherwise.
 ````


#### PLURALITY

There are K(C − K) assertions. For the case when there is only one winner, there are C - 1 assertions, pairing the winner with each loser. 
For a two candidate election, there is only one assertion to test.

For polling, the assorter function is A_wk,ℓj(MVR) as defined above

For comparision audits, the assorter function is A_wk,ℓj(CVR) − A_wk,ℓj(MVR), as defined above.

For a comparisian audit, the assorter function is B(bi, c) as defined above.


#### APPROVAL

In approval voting, voters may vote for as many candidates as they like.

The same algorithm works for approval voting as for plurality voting.


#### SUPERMAJORITY 

A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win. 

For the ith ballot, define A_wk,ℓj(bi) as
````
    assign the value “1/(2*f)” if it has a mark for wk but no one else; 
    assign the value “0” if it has a mark for exactly one candidate and not Alice
    assign the value 1/2, otherwise.
````
If multiple winners are allowed, each reported winner generates one assertions.

For a comparisian audit, the assorter function is B(bi, c) as defined above.


#### IRV

Not implemented yet.

See 
````
    Blom, M., Stuckey, P.J., Teague, V.: Risk-limiting audits for irv elections. 
    arXiv:1903.08804 (2019), https://arxiv.org/abs/1903.08804
````
and possibly
````
    Adaptively Weighted Audits of Instant-Runoff Voting Elections: AWAIRE	Ek, Stark, Stuckey, Vukcevic; 5 Oct 2023
````

## ALPHA testing statistic

ALPHA is adaptive, estimating the reported winner’s share of the vote before the jth card is drawn from the j-1 cards already in the sample.
The estimator can be any measurable function of the first j − 1 draws, for example a simple truncated shrinkage estimate.
ALPHA generalizes BRAVO to situations where the population {xj} is not necessarily binary, but merely nonnegative and bounded.
ALPHA works for sampling with or without replacement, with or without weights, while BRAVO is specifically for IID sampling with replacement.
````
θ 	        true population mean
Xk 	        the kth random sample drawn from the population.
X^j         (X1 , . . . , Xj) is the jth sequence of samples.

µj          E(Xj | X^j−1 ) computed under the null hypothesis that θ = 1/2. "expected value of the next sample's assorted value (Xj) under the null hypothosis".
            With replacement, its 1/2.
            Without replacement, its the value that moves the mean to 1/2.

η0          an estimate of the true mean before sampling .
ηj          an estimate of the true mean, using X^j-1 (not using Xj), or estimate of what the sampled mean of X^j is (not using Xj) ??
            This is the "estimator function". 

Let ηj = ηj (X^j−1 ), j = 1, . . ., be a "predictable sequence": ηj may depend on X j−1 , but not on Xk for k ≥ j.

Tj          ALPHA nonnegative supermartingale (Tj)_j∈N  starting at 1

	E(Tj | X^j-1 ) = Tj-1, under the null hypothesis that θj = µj (7)

	E(Tj | X^j-1 ) < Tj-1, if θ < µ (8)

	P{∃j : Tj ≥ α−1 } ≤ α, if θ < µ (9) (follows from Ville's inequality)
````
## BRAVO testing statistic

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

## Stratified audits

Replaced by OneAudit I think.

## Missing Ballots

To conduct a RLA, it is crucial to have an upper bound on the total number of ballot cards cast in the contest.

See SHANGRLA section 3.4