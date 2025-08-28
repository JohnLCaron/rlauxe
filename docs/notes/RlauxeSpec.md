**Rlauxe Implementation Specification**

_8/28/25_

See [references](../papers/papers.txt) for reference papers.

<!-- TOC -->
* [Missing Ballots](#missing-ballots)
* [Missing Contests](#missing-contests)
* [Assorters](#assorters)
  * [Plurality and Approval](#plurality-and-approval)
  * [SuperMajority](#supermajority)
  * [Instant Runoff Voting (IRV)](#instant-runoff-voting-irv)
* [Audits](#audits)
  * [Polling Audits](#polling-audits)
  * [Card Level Comparison Audits (CLCA)](#card-level-comparison-audits-clca)
    * [The clcaAssorter](#the-clcaassorter)
    * [Proof that B is an assorter](#proof-that-b-is-an-assorter)
  * [OneAudit](#oneaudit)
* [Risk functions (p-value calculators)](#risk-functions-p-value-calculators)
  * [Polling Audits](#polling-audits-1)
    * [Truncated shrinkage estimate of the population mean](#truncated-shrinkage-estimate-of-the-population-mean)
  * [CLCA Audits](#clca-audits)
    * [The CLCA betting function](#the-clca-betting-function)
* [Attacks](#attacks)
  * [Category A. CLCA with styles](#category-a-clca-with-styles)
    * [Case 1. Prover changes CVR votes for A to B.](#case-1-prover-changes-cvr-votes-for-a-to-b)
    * [Case 2. Prover changes CVR votes for A to undervotes.](#case-2-prover-changes-cvr-votes-for-a-to-undervotes)
    * [Case 3. Prover removes CVR ballots.](#case-3-prover-removes-cvr-ballots)
    * [Case 4. Prover removes CVR ballots and modifies Nc.](#case-4-prover-removes-cvr-ballots-and-modifies-nc)
<!-- TOC -->

# Missing Ballots

From "Limiting Risk by Turning Manifest Phantoms into Evil Zombies" (P2Z) paper:

"A listing of the groups of ballots and the number of ballots in each group is called a ballot manifest.
What if the ballot manifest is not accurate?
It suffices to make worst-case assumptions about the individual randomly selected ballots
that the audit cannot find. This ensures that the true risk limit remains smaller than the nominal risk limit.
The dead (not found, phantom) ballots are re-animated as evil zombies:
We suppose that they reflect whatever would increase the P-value most:
a 2-vote overstatement for a ballot-level comparison audit,
or a valid vote for every loser in a ballot-polling audit."

So:

* When a CVR is missing, an empty CVR is created for it, and marked "isPhantom = true".
* When a ballot cannot be found during sampling, the MVR is marked "isPhantom = true".

All the algorithms can then proceed normally.

TODO: discuss where this is implemented.

# Missing Contests

See "More style, less work: card-style data decrease risk-limiting audit sample sizes" (MoreStyle) paper.

We use _card style_ to refer to the set of contests on a given ballot card, and _Card Style Data_ (CSD)
to refer to the data telling what the card styles for each ballot.

For CLCA audits, the generated Cast Vote Records (CVRs) comprise the CSD, as long as the CVR has the information which contests are
on it, even when a contest recieves no votes. For Polling audits, the BallotManifest (may) contain BallotStyles which comprise the CSD.

Its critical in all cases (with or without CSD), that when the MVRs are created, the auditors record all the contests on the ballot,
whether or not there are any votes for a contest or not. In other words, an MVR always knows if a contest is contained on a ballot or not.
This information is necessary in order to correctly do random sampling, which the risk limiting statistics depend on.

When you dont have CSD, the number of ballots needed to audit (Na_c) is increased by a factor of N/Nc, where N is the total number
of ballots that the contest may be on, and Nc is the total number of ballots that the contest is on. Because Na_c is also
dependent on the margin, this affects close contests the most.  

Without CSD, RLA is unlikely to be practical for close elections. Similarly, Polling audits are much less efficient than CLCAs.
For that reason, this document is focused on CLCA with CSD implementation, but there is still one case that needs to be dealt with, 
which is when the CVR claims that the ballot contains a contest, but upon auditing, the MVR shows that it does not.

So:

* When a CVR has a contest on it that the MVR does not, the overstatementError uses an assort value of 0 for the MVR.

TODO: expain this. WHat if Prover is misrepresenting / wrong about which ballots have which contests?

# Assorters

Define the assorter function `A_wℓ(bi)` for winner w and loser ℓ operating on the ith ballot bi.

The assorter function takes a parameter _usePhantoms_, so a more complete definition is `A_wℓ(bi, usePhantoms)`, but we
will use the simpler notation.
Polling audits always have usePhantoms = true, while CLCA have usePhantoms = false.

The following Social Choice functions are supported:

## Plurality and Approval

"Top k candidates are elected."
The rules may allow the voter to vote for one candidate, k candidates or some other number, including n, which
makes it approval voting.

A contest has K ≥ 1 winners and C > K candidates. Let w be the winner, and ℓ be the loser.
For each pair of winner and loser, let H_wℓ be the assertion that w is really the winner over ℓ.
There are K(C − K) assertions.

**Plurality**: there is exactly one winner, and C - 1 assertions, pairing the winner with each loser.
For a two candidate election, there is only one assertion. See SHANGRLA, section 2.1.

**Approval**:  voters may vote for as many candidates as they like. The top K candidates are elected. See SHANGRLA, section 2.2.

The assorter function `A_wℓ(bi)` for winner w and loser ℓ operating on the ith ballot bi is

````
    0 if (usePhantoms && ballot.isPhantom)
    1 if ballot has a mark for w but not for ℓ 
    0 if ballot has a mark for ℓ but not for w
    1/2, otherwise.
````
The upper bound is 1.

````
    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val w = mvr.hasMarkFor(info.id, winner)
        val l = mvr.hasMarkFor(info.id, loser)
        return (w - l + 1) * 0.5
    }
````

## SuperMajority

"Top k candidates are elected, whose percent vote is above a fraction, f." See SHANGRLA, section 2.3.

A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win.
Note that we use valid votes for the contest (Vc) instead of all ballots (Nc) in the denominator when calculating
the percent vote for a candidate.

Currently we only support 1 winner.
For SuperMajority, we only need one assorter for each winner, not one for each winner/loser pair.

For the ith ballot, calculate `A_wℓ` as

````
    1/(2*f) if it has a mark for w but no one else
    0 if it has a mark for exactly one candidate and not w
    1/2, otherwise.
````
The upper bound is 1/(2*f).

## Instant Runoff Voting (IRV)

Also known as Ranked Choice Voting, this allows voters to rank their choices by preference.
In each round, the candidate with the fewest first-preferences (among the remaining candidates) is eliminated.
This continues until only one candidate is left. Only 1 winner is allowed.

In principle one could use polling audits for IRV, but the information
needed to create the Raire Assertions all but necessitates CVRs.
So currently we only support IRV with CLCA audits.

We use the [RAIRE java library](https://github.com/DemocracyDevelopers/raire-java) to generate assertions that fit into the SHANGRLA framework.
We convert the output of the raire library into RaireAssorters, which assigns the assort values. The clcaAssorter then can be used with
RaireAssorter transparently.

(Should i document the RaireAssorter assort function as above?)

The RaireAssorters function `A_wℓ(bi)` for winner w and loser ℓ operating on the ith ballot bi is

````
if (usePhantoms && mvr.isPhantom) return 0.5

for winner_only assertions:
        val awinner = if (raire_get_rank(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_loser_vote_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
        
for irv_elimination assertions:    
        // Context is that all candidates in "already_eliminated" have been eliminated and their votes distributed to later preferences
        val awinner = raire_votefor_elim(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_votefor_elim(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
            
// if candidate not ranked, return 0, else rank (1 based)
fun raire_get_rank(cvr: Cvr, contest: Int, candidate: Int): Int {
    val rankedChoices = cvr.votes[contest]
    return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
    else rankedChoices.indexOf(candidate) + 1
}

// Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
// Its a vote for the loser if they appear and the winner does not, or they appear before the winner
// return 1 if the given vote is a vote for 'loser' and 0 otherwise
fun raire_loser_vote_wo(cvr: Cvr, contest: Int, winner: Int, loser: Int): Int {
    val rank_winner = raire_get_rank(cvr, contest, winner)
    val rank_loser = raire_get_rank(cvr, contest, loser)

    return when {
        rank_winner == 0 && rank_loser != 0 -> 1
        rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
        else -> 0
    }
}

/**
 * Check whether 'vote' is a vote for the given candidate in the context where only candidates in 'remaining' remain standing.
 * If you reduce the ballot down to only those candidates in 'remaining', and 'cand' is the first preference, return 1; otherwise return 0.
 * @param cand identifier for candidate
 * @param remaining list of identifiers of candidates still standing
 * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
 */
fun raire_votefor_elim(cvr: Cvr, contest: Int, cand: Int, remaining: List<Int>): Int {
    if (cand !in remaining) return 0
    
    val rank_cand = raire_get_rank(cvr, contest, cand)
    if (rank_cand == 0) return 0

    for (altc in remaining) {
        if (altc == cand) continue
        val rank_altc = raire_get_rank(cvr, contest, altc)
        if (rank_altc != 0 && rank_altc <= rank_cand) return 0
    }
    return 1
}

````
The upper bound is 1.

# Audits

## Polling Audits

The requirements for Polling audits:

* There must be a BallotManifest defining the population of ballots, that contains a unique identifier that can be matched
  to the corresponding physical ballot.
* There must be an independently determined upper bound on the number of cast cards/ballots that contain each contest (Nc).


## Card Level Comparison Audits (CLCA)

The requirements for CLCA audits:

* The election system must be able to generate machine-readable Cast Vote Records (CVRs) for each ballot.
* Unique identifiers must be assigned to each physical ballot, and recorded on the CVR, in order to find the physical ballot that matches the sampled CVR.
* There must be an independently determined upper bound on the number of cast cards/ballots that contain the contest (Nc).

### The clcaAssorter

We will use the term *_assorter function_* to refer to the Plurality, Approval, and SuperMajority social choice
functions `A_wℓ` as defined above. We use *_clcaAssorter function_* to refer to the assorter used by Card Level Comparison Audits.
So, a clcaAssorter function has an assorter function, and by composing them, only one clcaAssorter implementation is needed.

CLCAs have the same number of assertions as in the Polling Audit case, with the same meaning.

Define the clcaAssorter function `B(A_wℓ, bi, ci)` for winner w and loser ℓ operating on the ith MVR bi and the ith
CVR ci as:

    B(A_wℓ, mvr, cvr) = (1-o/u)/(2-v/u), where
        A_wℓ is the assorter function for winner w and loser ℓ.
        u is the upper bound on the value the assorter function assigns to any ballot (given above)
        v is the cvrAssortMargin = 2 * (reported assorter mean) - 1
        o is the overstatement

The reported assorter mean for A_wℓ is calculated as `(winnerVotes - loserVotes) / Nc`, where Nc is the trusted maximum ballots for contest c.
TODO: relationship to Ā(cvr)

The overstatement is calculated as

        val noerror = 1.0 / (2.0 - cvrAssortMargin / u)             // clca assort value when overstatementError = 0
        val overstatement = overstatementError(mvr, cvr, hasStyle)  // ωi eq (1)
        val tau = (1.0 - overstatement / u)                         // τi eq (6)
        return tau * noerror                                        // Bi eq (7)

The overstatementError(mvr, cvr) is

        val mvr_assort = if (mvr.isPhantom || (hasStyle && !mvr.hasContest(contest.id))) 0.0
                         else A_wℓ(mvr, usePhantoms = false)
        val cvr_assort = if (cvr.isPhantom) .5 else A_wℓ(cvr, usePhantoms = false)
        return cvr_assort - mvr_assort

The `(hasStyle && !mvr.hasContest(contest.id))` is explained above in "Missing Contests" section.

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
        τ̄ * u / (2u - v)  > 1/2  ==   τ̄ / (2 - v/u) > 1/2             (6)

     Define B(bi, ci) ≡ τi /(2 − v/u) =  (1 − (ωi / u)) / (2 − v/u)   (7)
       Āb > 1/2  iff  Avg(B(bi, ci)) > 1/2                            (8)

     which makes B(bi, ci) an assorter.

## OneAudit

We do not support OneAudit at this time.

# Risk functions (p-value calculators)

## Polling Audits

For the risk function, Rlauxe uses the **AlphaMart** risk function with the **ShrinkTrunkage** estimation of the true
population mean (theta).  AlphaMart is a risk-measuring function that adapts to the drawn sample as it is made.
It estimates the reported winner’s share of the jth vote from the j-1 cards already in the sample.

See ALPHA paper, section 2.2, for a description of the AlphaMart algorithm. 

We use BettingMart to implement AlphaMart, by setting the betting function

    λ_i = (estTheta_i/µ_i − 1) / (upper − µ_i)
    where 
        upper is the upper bound of the assorter
        µ_i := E(Xi | Xi−1) is the truncated shrinkage estimate of the population mean

as described in ALPHA section 2.3.

See [AlphaMart risk function](../AlphaMart.md) for more details.
See [AlphaMart implementation](../../core/src/main/kotlin/org/cryptobiotic/rlauxe/core/AlphaMart.kt).


### Truncated shrinkage estimate of the population mean

See ALPHA paper, section 2.5.2.

ShrinkTrunkage uses a weighted average of an initial estimate of the mean with the measured mean
of the MVRs as they are sampled. The reported mean is used as the initial estimate of the mean. 

The only settable parameter for the TruncShrink funcition function is d, which is the weighting between the initial guess
at the population mean (eta0) and the running mean of the sampled data:

    estTheta_i = (d*eta0 + sampleSum_i) / (d + sampleSize_i)

This trades off smaller sample sizes when theta = eta0 (large d) vs quickly adapting to when theta < eta0 (smaller d).
Our implementation uses d=100 as default, and is settable in the PollingConfig class.


## CLCA Audits

Rlauxe uses the **BettingMart** risk function with the **AdaptiveBetting** _betting function_ for CLCA.
AdaptiveBetting needs estimates of the rates of over(under)statements. If these estimates are correct, one gets optimal sample sizes.
AdaptiveBetting uses a variant of ShrinkTrunkage that uses a weighted average of initial estimates (aka priors) with the actual sampled rates.

In BETTING, Waudby-Smith and Ramdas develop tests and confidence sequences for the mean of a bounded population using
betting martingales of the form

    M_j :=  Prod (1 + λ_i (X_i − µ_i)),  i=1..j    (BETTING eq 34 and ALPHA eq 10)

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

See Cobra section 4.2 and SHANGRLA Section 3.2. See [CLCA Risk function](../BettingRiskFunction.md) for more algorithm details.

See [BettingRiskFunction implementation](../../core/src/main/kotlin/org/cryptobiotic/rlauxe/core/BettingMart.kt) for
implementation details.


### The CLCA betting function

The "Estimating means of bounded random variables by betting" paper (BETTING) presents general techniques for estimating an unknown mean from bounded observations.

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
* a := 1 / (2 − v/u)
* v := 2Āc − 1 is the diluted margin
* u := assort upper value; = 1 for plurality, 1/(2*minFraction) for supermajority
* mu_i := mean value under H0 (= 1/2 for with replacement), otherwise for WoR, varies for each sample i (ALPHA section 2.2.1).
* The possible values of the comparison assort function are: {1, 1/2, 0, 3/2, 2} * a

The expected value of the test statistic (generalized from COBRA section 3.2) is based on the comparison assort values
for each of the under/overstatement error types:

Equation 1
````
EF[Ti] = p0 [1 + λ(a − mu_i)] + p1 [1 + λ(a/2 − mu_i)] + p2 [1 − λ*mu_i)]  + p3 [1 + λ(3*a/2 − mu_i)]  + p4 [[1 + λ(2*a − mu_i)]
````

We follow the code in https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R, to
find the value of lamda that maximizes EF\[Ti], _using org.apache.commons.math3.optim.univariate.BrentOptimizer_.

See [OptimalComparison implementation](../../core/src/main/kotlin/org/cryptobiotic/rlauxe/core/OptimalComparison.kt)
for details on the AdaptiveBetting implementation.

See [CLCA AdaptiveBetting](../AdaptiveBetting.md) for details on the AdaptiveBetting algorithm.

# Attacks

## Category A. CLCA with styles

The CVRs are the manifest. Nc=1000 ballots for contest C for candidates A and B. A=525, B=475. The margin of victory for A is 50.

        val mvr_assort = if (mvr.isPhantom || (hasStyle && !mvr.hasContest(contest.id))) 0.0
                         else A_wℓ(mvr, usePhantoms = false)
        val cvr_assort = if (cvr.isPhantom) .5 else A_wℓ(cvr, usePhantoms = false)
        overstatement = cvr_assort - mvr_assort
        assort = (1.0 - overstatement / u) * noerror

### Case 1. Prover changes CVR votes for A to B.

Prover changes 50 CVRs that voted for A to voting for B.  A=475, B=525.

Sample a changed ballot:
    
    cvr_assort = 1
    mvr_assort = 0
    overstatement = 1
    assort = 0

Audit detects this with probability 1 - risk.

### Case 2. Prover changes CVR votes for A to undervotes.

Prover changes 100 CVRs that voted for A to undervotes.  A=425, B=475.

Sample a changed ballot:

    cvr_assort = 1
    mvr_assort = if (hasStyle && !mvr.hasContest(contest.id)) 0.0
    overstatement = 1
    assort = 0

Audit detects this with probability 1 - risk.

### Case 3. Prover removes CVR ballots.

Prover removes 100 CVRs that voted for A. A=425, B=475.
Since Nc = 1000, we add 100 phantoms. 

Sample a removed ballot:

    cvr_assort = 1
    mvr_assort = if (isPhantom) 0.0
    overstatement = 1
    assort = 0

Audit detects this with probability 1 - risk.

### Case 4. Prover removes CVR ballots and modifies Nc.

Prover removes 100 ballots that voted for A from the CVRs. A=425, B=475. Prover changes Nc to 900.

We cannot detect this.


=============

SHANGRLA
An assorter A assigns a nonnegative value to each ballot card, depending on the marks
the voter made on that ballot card.

SHANGRLA also “plays nice” with the phantoms-to-zombies approach [3] for dealing
with missing ballot cards and missing cast-vote records, which has two benefits: (i) it
makes it easy to treat missing ballots rigorously, and (ii) it can substantially improve the
efficiency of auditing contests that do not appear on every ballot card, by allowing the
sample to be drawn just from cards that the voting system claims contain the contest,
without having to trust that the voting system correctly identified which cards contain
the contest.assorter

half-average assertions, each of
which claims that the mean of a finite list of numbers between 0 and u is greater than 1/2

The core, canonical statistical problem in SHANGRLA is to test the hypothesis that
x̄ ≤ 1/2 using a sample from a finite population {xi }N i=1 , where each xi ∈ [0, u], with u
known.

ALPHA 11
The domain of assorter j is Dj , which could comprise all ballot cards
cast in the election or a smaller set, provided Dj includes every card that contains the contest
that assorter Aj is relevant for. Targeting audit sampling using information about which ballot
cards purport to contain which contests (card style data) can vastly improve audit efficiency
while rigorously maintaining the risk limit even if the voting system misidentifies which
cards contain which contests (Glazer, Spertus and Stark, 2021). There are also techniques for
dealing with missing ballot cards (Bañuelos and Stark, 2012; Stark, 2020).

