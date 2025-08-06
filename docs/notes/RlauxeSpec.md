# Rlauxe Implementation
8/6/25

## Polling Audits

When CVRs are not available, a Polling audit can be done instead. A Polling audit  
creates an MVR for each ballot card selected for sampling, just as with a CLCA, except without the CVR.

The requirements for Polling audits:

* There must be a BallotManifest defining the population of ballots, that contains a unique identifier that can be matched
  to the corresponding physical ballot.
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest.



Define the assorter function `A_wk(bi)` for winner w and loser ℓ operating on the ith ballot bi.
The following Social Choice functions are supported.

### Plurality and Approval

"Top k candidates are elected."
The rules may allow the voter to vote for one candidate, k candidates or some other number, including n, which
makes it approval voting.

A contest has K ≥ 1 winners and C > K candidates. Let wk be the kth winner, and ℓj be the jth loser.
For each pair of winner and loser, let H_wk,ℓj be the assertion that wk is really the winner over ℓj.
There are K(C − K) assertions. 

**Plurality**: there is exactly one winner, then there are C - 1 assertions, pairing the winner with each loser.
For a two candidate election, there is only one assertion. See SHANGRLA, section 2.1. 

**Approval**:  voters may vote for as many candidates as they like. The top K candidates are elected. See SHANGRLA, section 2.2.

The assorter function `A_wk(bi)` for winner w and loser ℓ operating on the ith ballot bi is

````
    assign 0 if (usePhantoms && mvr.isPhantom)
    assign 1 if it has a mark for w but not for ℓ; 
    assign 0 if it has a mark for ℓ but not for w;
    assign the value 1/2, otherwise.
````
The upper bound is 1.

### SuperMajority

"Top k candidates are elected, whose percent vote is above a fraction, f." See SHANGRLA, section 2.3.

A winning candidate must have a minimum fraction f in the open interval (0, 1) of the valid votes to win.
Note that we use valid votes for the contest (Vc) instead of all notes (Nc) in the denominator when calculating
the percent vote for a candidate..

Currently we only support 1 winner.
For supermajorrity, we only need one assorter for each winner, not one for each winner/loser pair.

For the ith ballot, calculate `A_wk` as

````
    assign the value “1/(2*f)” if it has a mark for wkbut no one else; 
    assign the value “0” if it has a mark for exactly one candidate and not w
    assign the value 1/2, otherwise.
````
The upper bound is 1/(2*f).


## Card Level Comparison Audits (CLCA)

When the election system produces an electronic record for each ballot card, known as a Cast Vote Record (CVR), then
Card Level Comparison Audits can be done that compare sampled CVRs with the corresponding ballot card that has been
hand audited to produce a Manual Vote Record (MVR). A CLCA typically needs many fewer sampled ballots to validate contest
results than other methods.

The requirements for CLCA audits:

* The election system must be able to generate machine-readable Cast Vote Records (CVRs) for each ballot.
* Unique identifier must be assigned to each physical ballot, and put on the CVR, in order to find the physical ballot that matches the sampled CVR.
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest.

Rlauxe uses the **BettingMart** risk function with the **AdaptiveBetting** _betting function_.
This is also called the _p-value calculators_

AdaptiveBetting needs estimates of the rates of over(under)statements. If these estimates are correct, one gets optimal sample sizes.
AdaptiveBetting uses a variant of ShrinkTrunkage that uses a weighted average of initial estimates (aka priors) with the actual sampled rates.


### The ClcaAssorter for CLCA

The ClcaAssorter for CLCA uses the assorter functions for Plurality, Approval, and SuperMajority social choice
functions `A_wℓ` as defined above. We will continue to use _assorter function_ to mean these functions, and use
_clcaAssorter function_ to refer to the function used by Card Level Comparison Audits.

CLCAs have the same number of assertions as in the Polling Audit case, with the same meaning.

Define the ClcaAssorter function `B(A_wℓ, bi, ci)` for winner w and loser ℓ operating on the ith ballot bi and the ith
CVR ci as:

    B(A_wℓ, ci) = (1-o/u)/(2-v/u), where
        A_wℓ is the assorter function for winner w and loser ℓ.
        u is the upper bound on the value the assorter function assigns to any ballot, given above
        v is the assorter margin = 2 * (reported assorter mean) - 1
        o is the overstatement

The reported assorter mean for A_wℓ is calculated as `(winnerVotes - loserVotes) / Nc`, where Nc is the maximum ballots for contest c.

The overstatement is calculated as

        val overstatement = overstatementError(mvr, cvr, hasStyle) // ωi eq (1)
        val tau = (1.0 - overstatement / u)                        // τi eq (6)
        return tau * noerror                                       // Bi eq (7)

The overstatementError(mvr, cvr) is

        val mvr_assort = if (mvr.isPhantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
                         else A_wℓ(mvr, usePhantoms = false)
        val cvr_assort = if (cvr.isPhantom) .5 else A_wℓ(cvr, usePhantoms = false)
        return cvr_assort - mvr_assort

### Proof that B is an assorter

See SHANGRLA Section 3.2.

    Let bi denote the ith ballot, and let ci denote the cast-vote record for the ith ballot.
    Let A denote an assorter, which maps votes into [0, u], where u is an upper bound (eg 1, 1/2f).

    The overstatement error for the ith ballot is
         ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ u.     (1)

    Let Āc = AVG(A(ci)), Āb = AVG(A(bi)) and ω̄ = AVG(ωi).
    Then Āb = Āc − ω̄, so
         Āb > 1/2  iff  ω̄ < Āc − 1/2.          (2)

     We know that Āc > 1/2 (or the assertion would not be true for the CVRs), so 2Āc − 1 > 0,
     so we can divide without flipping the inequality:
        ω̄ < Āc − 1/2  <==>  ω̄ / (2Āc − 1) < (Āc − 1/2) / (2Āc − 1) = (2Āc − 1) / 2(2Āc − 1) = 1/2
     that is,
        Āb > 1/2  iff  ω̄ / (2Āc − 1) < 1/2     (3)

     Define v ≡ 2Āc − 1 == the reported assorter margin so
        Āb > 1/2  iff  ω̄ / v < 1/2             (4)

     Let τi ≡ 1 − (ωi / u) ≥ 0, and τ̄ ≡ Avg(τi) = 1 − ω̄/u, and ω̄ = u(1 − τ̄), so
        Āb > 1/2  iff  (u/v) * (1 − τ̄) < 1/2   (5)

     Then (u/v) * (1 − τ̄) < 1/2 == (-u/v) τ̄ < 1/2 - (u/v) == τ̄ > (-v/u)/2 - (-v/u)(u/v) == 1 - v/2u == (2u - v) / 2u
        τ̄ * u / (2u - v)  > 1/2  ==   τ̄ / (2 - v/u) > 1/2     (6)

     Define B(bi, ci) ≡ τi /(2 − v/u) =  (1 − (ωi / u)) / (2 − v/u)    (7)
       Āb > 1/2  iff  Avg(B(bi, ci)) > 1/2                              (8)

     which makes B(bi, ci) an assorter.

## Rlaux risk functions (aka  _p-value calculators_)

### Polling Audits

AlphaMart (aka ALPHA) is a risk-measuring function that adapts to the drawn sample as it is made.
It estimates the reported winner’s share of the vote before the jth card is drawn from the j-1 cards already in the sample.
The estimator can be any measurable function of the first j − 1 draws, for example a simple truncated shrinkage estimate, described below.
See ALPHA paper, section 2.2.

Define:

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


For the risk function, Rlaux uses the **AlphaMart** (aka ALPHA) function with the **ShrinkTrunkage** estimation of the true
population mean (theta). ShrinkTrunkage uses a weighted average of an initial estimate of the mean with the measured mean
of the mvrs as they are sampled. The reported mean is used as the initial estimate of the mean. The assort values
are specified in SHANGRLA, section 2. See Assorter.kt for our implementation.

See [AlphaMart risk function](docs/AlphaMart.md) for details on the AlphaMart risk function.

#### Truncated shrinkage estimate of the population mean

See ALPHA paper, section 2.5.2.

The only settable parameter for the TruncShrink funcition function is d, which is the weighting between the initial guess
at the population mean (eta0) and the running mean of the sampled data:

    estTheta_i = (d*eta0 + sampleSum_i) / (d + sampleSize_i)

This trades off smaller sample sizes when theta = eta0 (large d) vs quickly adapting to when theta < eta0 (smaller d).


### CLCA Audits

In BETTING, Waudby-Smith and Ramdas develop tests and confidence sequences for the mean of a bounded population using
betting martingales of the form

    M_j :=  Prod (1 + λ_i (X_i − µ_i)),  i=1..j    (BETTING eq 34 and ALPHA eq  10)

    where 
        M_j is the martingal after the jth sample
        λ_i is the ith bet
        X_i is the ith assort value
        µ_i := E(Xi | Xi−1), computed on the assumption that the null hypothesis is true. (For large N, µ_i is very close to 1/2.)

The sequence (M_j) can be viewed as the fortune of a gambler in a series of wagers.
The gambler starts with a stake of 1 unit and bets a fraction λi of their current wealth on
the outcome of the ith wager. The value Mj is the gambler’s wealth after the jth wager. The
gambler is not permitted to borrow money, so to ensure that when X_i = 0 (corresponding to
losing the ith bet) the gambler does not end up in debt (Mi < 0), λi cannot exceed 1/µi.

See Cobra section 4.2 and SHANGRLA Section 3.2. See [CLCA Risk function](../docs/BettingRiskFunction.md) for more details.
See [BettingRiskFunction implementation](../../core/src/main/kotlin/org/cryptobiotic/rlauxe/core/BettingMart.kt).


#### The CLCA betting function

The "Estimating means of bounded random variables by betting" paper presents general techniques for estimating an unknown mean from bounded observations.

The ALPHA paper summarizes this for RLAs, in section 2.3. While formally equivalent to the sequential probability ratios (SPR) approach,
the betting strategy approach gives better intuition on the "aggressive betting" strategy, which is necessary to
get good performance for ballot comparison audits.

The COBRA paper explores a number of algorithms for optimal betting parameters for ballot
comparison audits, based on estimating the rates of the under/overstatement errors:

Table 1.
````
    p0 := #{xi = a}/N is the rate of correct CVRs.
    p1 := #{xi = a/2}/N is the rate of 1-vote overstatements.
    p2 := #{xi = 0}/N is the rate of 2-vote overstatements.
    p3 := #{xi = 3a/2}/N is the rate of 1-vote understatements.
    p4 := #{xi = 2a}/N is the rate of 2-vote understatements.
````
where
* a := 1 / (2 − v/au)
* v := 2Āc − 1 is the diluted margin
* au := assort upper value; = 1 for plurality, 1/(2*minFraction) for supermajority
* mu_i := mean value under H0 (= 1/2 for with replacement), otherwise for WoR, varies for each sample i (ALPHA section 2.2.1).
* The possible values of the comparison assort function are: {1, 1/2, 0, 3/2, 2} * a

The expected value of the test statistic (generalized from COBRA section 3.2) is based on the comparison assort values
for each of the under/overstatement error types:

Equation 1
````
EF[Ti] = p0 [1 + λ(a − mu_i)] + p1 [1 + λ(a/2 − mu_i)] + p2 [1 − λ*mu_i)]  + p3 [1 + λ(3*a/2 − mu_i)]  + p4 [[1 + λ(2*a − mu_i)]
````

We follow the code in https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R, to
find the value of lamda that maximizes EF[Ti], using org.apache.commons.math3.optim.univariate.BrentOptimizer.
See [OptimalComparison implementation](../core/src/main/kotlin/org/cryptobiotic/rlauxe/core/OptimalComparison.kt).

See [CLCA AdaptiveBetting](docs/AdaptiveBetting.md) for details on the AdaptiveBetting function.


## Instant Runoff Voting (IRV)

Also known as Ranked Choice Voting, this allows voters to rank their choices by preference.
In each round, the candidate with the fewest first-preferences (among the remaining candidates) is eliminated.
This continues until only one candidate is left. Only 1 winner is allowed.

In principle one could use polling audits for IRV, but the information
needed to create the Raire Assertions all but necessitates CVRs.
So currently we only support IRV with CLCA audits.

We use the [RAIRE java library](https://github.com/DemocracyDevelopers/raire-java) to generate IRV assertions
that fit into the SHANGRLA framewok, and makes IRV contests amenable to risk limiting auditing, just like plurality contests.

See the RAIRE guides for details:
* [Part 1: Auditing IRV Elections with RAIRE](https://github.com/DemocracyDevelopers/Colorado-irv-rla-educational-materials/blob/main/A_Guide_to_RAIRE_Part_1.pdf)
* [Part 2: Generating Assertions with RAIRE](https://github.com/DemocracyDevelopers/Colorado-irv-rla-educational-materials/blob/main/A_Guide_to_RAIRE_Part_2.pdf)


