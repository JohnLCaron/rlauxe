# OneAudit Batch


````
contest 1 assertion = 1/3
 idx,         xs,        bet,     payoff,       Tj,   pvalue,                                         location,  mvr votes, card votes
 392, 0.52381771, 0.63860766, 1.01521080, 0.628133, 1.592019, DOUGLAS-ED-Chestnut Log ICP 2 - 0-95,                     [], pool=2329, poolAvg=0.4804
 805, 0.77737184, 0.63876478, 1.17717825, 6.748371, 0.148183, CHEROKEE-ED-Booth ICP 1 - 0-44,                          [1], pool=548, poolAvg=0.4868
 879, 0.20748673, 0.63880904, 0.81314351, 12.42957, 0.080453, COLUMBIA-ED-Harlem Branch Library  ICP 1 - 0-126,        [3], pool=1771, poolAvg=0.5961
````

Note that the bet is set will below the maximum of 1.9. I think this is what makes OneAudit Batch successful.

## Betting with OneAudit pools

OneAudit cards are a mixture of CVRs and pooled data. The CVRS are handled exactly as CLCA above. Pooled data
do not have CVRS, instead we have the average assort value in each pool.

Consider a single pool and assorter a, with upper bound u and avg assort value in the pool = poolAvg.
The poolAvg is used for cvr_assort, so the overstatement error = cvr_assort - mvr_assort has one of 3 possible values:

    poolAvg - [0, .5, u] = [poolAvg, poolAvg -.5, poolAvg - u] for mvr loser, other and winner 

then bassort = (1-o/u)/(2-v/u) has one of 3 possible values:

    bassort = [1-poolAvg/u, 1 - (poolAvg -.5)/u, 1 - (poolAvg - u)/u] * noerror
    bassort = [1-poolAvg/u, (u - poolAvg + .5)/u, (2u - poolAvg)/u] * noerror

As a concrete example, use poolAvg=0.5961, u = 1,  noerror=0.51372665 (margin = .053439)

    bassort = [1-poolAvg, 1.5 - poolAvg, 2 - poolAvg] * noerror   for mvr loser, other and winner 
    bassort = [.4039, 0.9039, 1.4039] * 0.51372665     for mvr loser, other and winner 
    bassort = [.20749, 0.464357519, 0.72122]     for mvr loser, other and winner 

    payoff = 1.0 + bet * (noerror - 0.5)

For each pool, we know the expected number of loser, winner, and other votes over all the cards in the pool:

```
    winnerVotes = votes[assorter.winner()]
    loserVotes = votes[assorter.loser()]
    otherVotes = pool.ncards() - winnerVotes - loserVotes
```

The expected rates are the number of votes divided by Npop. These are also the probabilities of drawing a card from that pool with that
assort value. These rates are known for each pool, they are not sample dependent.

Then we extend equation 1 with the expected assort values from the pools:

````
log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (a_k - mui)) * p_k, k=1..n }   (eq 1)

log T_i = ln(1.0 + lamda * (noerror - mui)) * p0 + Sum { ln(1.0 + lamda * (a_k - mui)) * p_k, k=1..n }
          + Sum { ln(1.0 + lamda * (a_pk - mui)) * p_pk; over pools and pool types }                      (eq 2)

where 
    p0 is the probability of no error (mvr matches the cvr)
    p_k is the probability of error type k
    a_k is the value of x when error type k occurs
    p_pk is the probability of getting pool p, type k occurs (k = winner, loser, other)
    a_pk is the assort value when pool p, type k occurs (k = winner, loser, other)
````

And use this to find the optimal value of lambda for OneAudit bets.


## Case Studies

### Georgia

We have county/batch subtotals for the two contests, as well as batch ncards. Use vunderPool.makeCardsForOneAuditPool
to create mvrs for each batch. Assume a single style (1,2) for all cards.

### Boulder

We have "redacted pools" with subtotals but no ncards (2025 adds them).
We have a "Statement of Vote" that gives votes subtotals by precinct and ncards:

````
"Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
"100","2181207100","Presidential Electors","Kamala D. Harris / Tim Walz","1,569","1,325",900,24,0
...
````

1. Group by BoulderContestPrecinctVotes with key = "${it.contestTitle}#${it.precinctCode}#${it.precinctNumber}" including totalBallots (ncards?), totalUnderVotes, totalOverVotes.
2. Aggregate into BoulderContestVotes.
3. BoulderContestBuilder: 
````
    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards

    // sovo gives us an expected undervote for each contest
    val sovoUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN

    // missing undervotes we assume are in the redacted pools
    val redUndervotes = sovoUndervotes - cvrTab.undervotes
    val redVotes = redTab.nvotes()

    // then this is the total cards in the pools
    val redNcards = (redVotes + redUndervotes) / info.voteForN

    // then this is the total cards in the cvrs and the pools
    val totalCards = redNcards + cvrTab.ncardsTabulated
````

5. Assume each pool has a single ballotStyle (this may be untrue)
4. Adjust ncards to minimize the undervotes.

````
// multiple contests, one pool
fun makeCvrsForOnePool(vunders: Map<Int, Vunder>, poolName: String, poolId: Int?, hasExactContests: Boolean): List<Cvr> {
````

I guess its good enough.

Summary: Hokey Baloney; we need to get ncards and ballot styles for redacted pools

### San Francisco

We have full cvrs, but the cvrs from "vote in person" cant be matched to the ballots.

// group cards into pools using the first part of the cvr id.
// this creates 4224 pools. The precinct/style grouping gives 2525. Perhaps can just add a variation that uses one or the other?

We just use the real cvrs as the mvrs, and zero out the cotes if they are in a pool.

