# OneAudit Notes

### Why you cant use BettingMart

BettingMart bets as aggressively as possible, and is very sensitive to errors between the MVR and CVR. 
It assumes that mostly the two agree, and manages the bet to deal with the expected rates of disagreement. 
With OA, when you have a ballot from the non-CVR stratum, its like you always have a disagreement, so your test pretty much always fails.

In a regular CLCA, the possible CLCA assort values (Bi) are 

````
Assort values:
  assort in {0, .5, 1} (plurality)

Regular CLCA:
  overstatementError ≡ A(CVR) − A(MVR) ≡ ωi
  ωi is in [-1, -.5, 0, .5, 1] (plurality)
  
  find B transform to interval [0, 1],  so that H0 is B < 1/2
  Bi ≡ (1 - ωi) / (2 - v)
  let tau ≡ (1 - ωi), noerror ≡ 1 / (2 - v)
  so Bi ≡ tau * noerror; 

  Bi in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]

where 
  v = assorter margin
  noerror ≡ 1/(2-v)
````

In OneAudit, when the ballot is in the non-CVR strata:

````
  mvr assort in {0, .5, 1}, as before
  cvr assort is always Ā(g) ≡ assorter_mean_poll ≡ (winner total - loser total) / Ng
  overstatementError ≡ ωi ≡ A(CVR) − A(MVR) = Ā(g) - {0, .5, 1} = { Ā(g), Ā(g)-.5, Ā(g)-1 } = [loser, nuetral, winner]

  τi ≡ (1 − ωi) = {1 - Ā(g), 1 - (Ā(g)-.5), 1 - (Ā(g)-1)}
  B(bi, ci) ≡ {1 - Ā(g), 1 - (Ā(g)-.5), 1 - (Ā(g)-1)} / (2 − v)
          
  mvr has loser vote = (1 - A) / (2-v)
  mvr has winner vote = (2 - A) / (2-v)
  mvr has other vote = (1 - (A -.5)) / (2-v) = (1.5 - A) / (2-v) 
  
  v = 2A-1, 2A = v + 1
  2-v = 2-(2A-1) = 3-2A = 2*(1.5-A)
  other = (1.5-A) / (2-v) = (1.5-A)/2*(1.5-A) = 1/2
  
  Bi in [ (1-A) / (2-v), .5, (2-A) / (2-v)) ] = [Bl, Bo, Bw]
  
  where 
      v = assorter margin in non-CVR strata
      noerror ≡ 1/(2-v)
````

The Alpha supermartingale is Tj+1 = Tj * tj, where

    val tj = 1.0 + betj * (Bj - mj)

can be thought of as the "betting payoff" of betj.

    mj = 1/2
    (Bl - 1/2) = (1-A)/(2-v) - 1/2 = 2(1-A)/2(2-v) - (2-v)/2(2-v) = (2-2A-(2-v))/2(2-v) = 2-(v+1)-2+v)/2(2-v) = -1/2(2-v)
    (Bo - 1/2) = 0
    (Bw - 1/2) = (2-A)/(2-v) - 1/2 = 2(2-A)/2(2-v) - (4-v)/2(2-v) = (4-2A-(2-v))/2(2-v) = 4-(v+1)-2+v)/2(2-v) = 1/2(2-v)

    (Bi - 1/2) = [-f, 0, f], where f ≡ 1/2(2-v)

For example if A = .52, v = .04, noerror = 0.510204082, Bi in [.245, .5, 0.755102041], and
(Bi - 1/2) in [-.255102, 0, .255102]

In order to reject the null, we need Tk > 1 / risk, for some k < N. A value of tj > 1 increase Tj, a value <  1 decreases it.

Consider a contest with w winner votes and l loser votes out of a total of N votes. (We can ignore other votes)

The factors tj consist of l pairs of votes where one has a vote for the winner, and one has a vote for the loser,
and (w - l) votes for the winner.

For the pairs, the product of their tjs has the form (1 + betj*f) * (1 - betj*f) = 1 - (betj*f)**2, which are always less than 1, which decrease Tj.
(Note that the betting values betj will change for each j, so this is approximate, unless the bets are constant)
So there an asymettry to the betting payoff.

And we have (w - l) = v * N, tjs with the value (1 + betj*f), which will increase Tj.

Assuming betj are constant = b, then 

    (1 - (b*f)**2)^l * (1 + b*f)^(w-l) > 1/risk
    l * log(1-(b*f)**2) + (w-l) * log (1+b*f)  > -log(risk)


//////////////
One possibility is that we can mofify the betting function to take into account these known "errors".





////////////////////////////////////////////////////////////////


OneAudit, 2.3 pp 5-7:
````
      "assorter" here is the plurality assorter
      from oa_polling.ipynb
      assorter_mean_all = (whitmer-schuette)/N
      v = 2*assorter_mean_all-1
      u_b = 2*u/(2*u-v)  # upper bound on the overstatement assorter
      noerror = u/(2*u-v)

      Let bi denote the true votes on the ith ballot card; there are N cards in all.
      Let ci denote the voting system’s interpretation of the ith card, for ballots in C, cardinality |C|.
      Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {G_g}, g=1..G for which reported assorter subtotals are available.

          Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
          margin ≡ 2Ā(c) − 1, the _reported assorter margin_

          ωi ≡ A(ci) − A(bi)   overstatementError
          τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
          B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)

         Ng = |G_g|
         Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng; > 0
         margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
         
         mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
         mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
         otherwise = 1/2
````

TODO redo proof with supermajority
````
Plurality assort values:
  assort in {0, .5, 1}

Regular Comparison:
  overstatementError in [-1, -.5, 0, .5, 1] == A(ci) − A(bi) = ωi
  find B transform to interval [0, u],  where H0 is B < 1/2
  Bi = (1 - ωi/u) / (2 - v/u)
  Bi = tau * noerror; tau = (1 - ωi/u), noerror = 1 / (2 - v/u)

  Bi in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]
  
Batch Comparison:
  mvr assort in {0, .5, 1} as before
  cvr assort is always Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng
  overstatementError == A(ci) − A(bi) = Ā(g) - {0, .5, 1} = { Ā(g), Ā(g)-.5, Ā(g)-1} = [loser, nuetral, winner]
  
  ωi ≡ A(ci) − A(bi)   overstatementError
  τi ≡ (1 − ωi /u) = {1 - Ā(g)/u, 1 - (Ā(g)-.5)/u, 1 - (Ā(g)-1)/u}
  B(bi, ci) ≡ {1 - Ā(g)/u, 1 - (Ā(g)-.5)/u, 1 - (Ā(g)-1)/u} / (2 − v/u)
          
  mvr has loser vote = (1 - Ā(g)/u) / (2-v/u)
  mvr has winner vote = (1 - (Ā(g)-1)/u) / (2-v/u)
  mvr has other vote = (1 - (Ā(g)-.5)/u) / (2-v/u) = 1/2
  
  when u = 1
   mvr has loser vote = (1 - A) / (2-v)
   mvr has winner vote = (2 - A) / (2-v)
   mvr has other vote = (1.5 - A) / (2-v) 
  
  v = 2A-1
  2-v = 2-(2A-1) = 3-2A = 2*(1.5-A)
  other = (1.5-A) / (2-v) = (1.5-A)/2*(1.5-A) = 1/2
  
  Bi in [ (1 - Ā(g)), .5, (2 - Ā(g))] * noerror(g)
````
Using a “mean CVR” for the batch is overstatement-net-equivalent to any CVRs that give the same assorter
batch subtotals.

````
    v ≡ 2Ā(c) − 1, the reported _assorter margin_, aka the _diluted margin_.

    Ā(b) > 1/2 iff

    Sum(A(ci) - A(bi)) / N < v / 2   (5)

Following SHANGRLA Section 3.2 define

    B(bi) ≡ (upper + A(bi) - A(ci)) / (2*upper - v)  in [0, 2*upper/(2*upper - v)] (6)

    and Ā(b) > 1/2 iff B̄(b) > 1/2

    see OneAudit section 2.3
````
Section 2
````
    Ng = |G_g|
    assorter_mean_poll = (winner total - loser total) / Ng
    mvr has loser vote = (1-assorter_mean_poll)/(2-v)
    mvr has winner vote = (2-assorter_mean_poll)/(2-v)
    otherwise = 1/2
  
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

Section 4: Auditing heterogenous voting systems: When the voting system can report linked CVRs for some but not all cards.

See "Auditing heterogenous voting systems" Section 4 for comparision to SUITE:
````
The statistical tests used in RLAs are not affine equivariant because
they rely on a priori bounds on the assorter values. The original assorter values
will generally be closer to the endpoints of [0, u] than the transformed values
are to the endpoints of [0, 2u/(2u − v)]

An affine transformation of the overstatement assorter values can move them back to the endpoints of the support
constraint by subtracting the minimum possible value then re-scaling so that the
null mean is 1/2 once again, which reproduces the original assorter.
````

Section 5.2

````
While CLCA with ONE CVRs is algebraically equivalent to BPA, the perfor-
mance of a given statistical test will be different for the two formulations.

Transforming the assorter into an overstatement assorter using the ONEAudit transformation, then testing whether 
the mean of the resulting population is ≤ 1/2 using the ALPHA test martingale with the
truncated shrinkage estimator of [22] with d = 10 and η between 0.505 and 0.55
performed comparably to—but slightly worse than—using ALPHA on the raw
assorter values for the same d and η, and within 4.8% of the overall performance
of the best-performing method.
````

"ALPHA on the raw assorter values" I think is regular BPA.
"Transforming the assorter into an overstatement assorter" is ONEAUDIT I think, but using Alpha instead of Betting?
This paper came out at the same time as COBRA.

If ONEAUDIT is better than current BPA, perhaps can unify all 3 (comparison, polling, oneaudit) into a single workflow??
The main difference is preparing the contest with strata.

Unclear about using phantoms with ONEAUDIT non-cvr strata. Perhaps it only appears if the MVR is missing?

Unclear about using nostyle with ONEAUDIT.