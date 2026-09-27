# Belgium D'Hondt Elections
_last changed 06/29/2026_

I found a bug in my relaxed assertion algorithm. I thought I was recursively checking for all relaxed (aka "disputed" or "failed")
assertions, but actually didnt go to more than one level of recursion.

For the Belgium 2024 standard example of limited mvrs, only these 2 assertions were found to be failures for FlandreEast:

````
Contested       loser/round   nvotes,  score, scoreDiff,  noerror, estSamples, actSamples, estRisk, assertion
 (20)               CD&V/ 3,  125871,  41957,      629, 0.500436,      3567,      800,     0.5107, winner Vooruit/3 loser CD&V/3
 (20)                                             2224, 0.501544,      1009,      800,     0.0927, winner CD&V/3 loser open vld/3
````

Changes:

1. I realize that you can imagine that each "party/round" is a candidate in a "Vote for N" plurality contest where n = number of seats.
The D'hondt assertions are then a simplification of "test every winner against every loser". 
2. I renamed the candidates "party/round", and the assertions renamed to "winner/round"-"loser/round".
3. I have introduced a new metric "scoreDiffMin" explained below.
4. I have simplified my algorithm using  scoreDiffMin and created a new "Relaxed Assertion Report" based on it:

````
FlandreEast (4) Nc=1083369 Nphantoms=0 votes={24=234888, 15=231470, 28=127758, 4=125871, 30=119200, 10=103722, 19=75942, 21=8057, 26=8007, 11=2352, 2=1390} undervotes=44712, voteForN=1
reported winners
 seat         winner-round     nvotes,   score, scoreDiff, scoreDiffMin
 ( 1)       VLAAMS BELANG/1 ,  234888, 234888, 
 ( 2)                N-VA/1 ,  231470, 231470,       3418,
 ( 3)             Vooruit/1 ,  127758, 127758,     103712,
 ( 4)                CD&V/1 ,  125871, 125871,       1887,
 ( 5)            open vld/1 ,  119200, 119200,       6671,
 ( 6)       VLAAMS BELANG/2 ,  234888, 117444,       1756,
 ( 7)                N-VA/2 ,  231470, 115735,       1709,
 ( 8)               GROEN/1 ,  103722, 103722,      12013,
 ( 9)       VLAAMS BELANG/3 ,  234888,  78296,      25426,
 (10)                N-VA/3 ,  231470,  77156,       1140,
 (11)                PVDA/1 ,   75942,  75942,       1214,
 (12)             Vooruit/2 ,  127758,  63879,      12063,
 (13)                CD&V/2 ,  125871,  62935,        944,
 (14)            open vld/2 ,  119200,  59600,       3335,
 (15)       VLAAMS BELANG/4 ,  234888,  58722,        878,
 (16)                N-VA/4 ,  231470,  57867,        855,
 (17)               GROEN/2 ,  103722,  51861,       6006,
 (18)       VLAAMS BELANG/5 ,  234888,  46977,       4884,
 (19)                N-VA/5 ,  231470,  46294,        683,
 (20)             Vooruit/3 ,  127758,  42586,       3708,
                     CD&V/3 ,  125871,  41957,        629,   2805*
                 open vld/3 ,  119200,  39733,       2853,   2805            CD&V/3-open vld/3: 2224, 2805*;
            VLAAMS BELANG/6 ,  234888,  39148,       3438,   2104       CD&V/3-VLAAMS BELANG/6: 2809, 2104 ;   open vld/3-VLAAMS BELANG/6: 585, 2104*;
                     N-VA/6 ,  231470,  38578,       4008,   2104                CD&V/3-N-VA/6: 3379, 2104 ;           open vld/3-N-VA/6: 1155, 2104*;       VLAAMS BELANG/6-N-VA/6: 570, 1403*;
                     PVDA/2 ,   75942,  37971,       4615,   3506                CD&V/3-PVDA/2: 3986, 3506 ;           open vld/3-PVDA/2: 1762, 3506*;      VLAAMS BELANG/6-PVDA/2: 1177, 2805*;                N-VA/6-PVDA/2: 607, 2805*;                N-VA/6-PVDA/2: 607, 2805*;
                    GROEN/3 ,  103722,  34574,       8012,   2805               CD&V/3-GROEN/3: 7383, 2805 ;          open vld/3-GROEN/3: 5159, 2805 ;     VLAAMS BELANG/6-GROEN/3: 4574, 2104 ;              N-VA/6-GROEN/3: 4004, 2104 ;              PVDA/2-GROEN/3: 3397, 3506*;              PVDA/2-GROEN/3: 3397, 3506*;              N-VA/6-GROEN/3: 4004, 2104 ;              PVDA/2-GROEN/3: 3397, 3506*;              PVDA/2-GROEN/3: 3397, 3506*;
````

The first part shows the reported winners with total votes for each party, the score for each candidate (= nvotes/round),
and the difference of the scores from the row above.

In this example, FlandreEast has a limit of 800 mvrs. This limits how small an assertion's margin can be and still pass the 5% risk limit.
Its a bit tricky because the margin depends on which rounds the winner and loser came from (see calculation below).
ScoreDiffMin is the minimum scoreDiff = (winner score - loser score ) for that particular assertion, which passes the risk limit (when there are no errors).

The second part of the table shows scoreDiffMin for each assertion; when scoreDiff < scoreDiffMin the assertion will fail to achieve the risk limit.

The first line after the last winner (Vooruit/3) shows the loser with the highest score (CD&V/3). It fails (629 < 2805) and is marked with an "*".
The first column shows assertions with "Vooruit/3" as the winner, and the row candidate (eg "open vld/3") as the loser.
The second line first column shows the loser "open vld/3", which doesnt fail (2853 > 2805), and so on down the rows for the first column of (scoreDiff, scoreDiffMin) values.

Because the "Vooruit/3-CD&V/3" assertion fails, it generates a second column, which are the assertions generated by ignoring Vooruit/3 and pretending that CD&V/3
was the winner, so this second column are the assertions "CD&V/3-"row candidate". Since its hard to track, the actual assertion name is shown.
Looking at the second column, "CD&V/3-open vld/3: 2224, 2805*" fails, but the rest succeed. This failure generates the third column.

The third column has four assertions and three fail, generating 3 new columns, and so on. Every failure generates a new column.

Using this simple algorithm drags in all the rest of the candidates. Yikes!

The crux of the matter

````
    1. Vooruit/3 >? CD&V/3     assertion fails
    2. Vooruit/3 > open vld/3  assertion succeeds  
but
    3. CD&V/3 >? open vld/3    assertion fails
````

It seems that assertion 2 should prove that open vld/3 cannot be a winner. Then, having eliminated them, we are only left with the assertion failures from column 1.
I hope.

## scoreDiffMin calculation

If you have nsamples, whats the largest margin satisfying the risk limit? Assume no errors and a constant payout.

See core/main org.cryptobiotic.rlauxe.betting.Utils:

    fun noerror(margin: Double, upper: Double) = 1.0 / (2.0 - margin / upper)
    fun payoff(bet:Double, noerror:Double,) = 1.0 + bet * (noerror - 0.5)
    fun estMarginUpperFromSamples(bet:Double, nsamples:Int, alpha: Double)

    payoff^n = 1/alpha
    ln(payoff) * n = -ln(alpha)
    // substitute payoff = 1.0 + bet * (noerror - 0.5)
    ln(1.0 + bet * (noerror - 0.5)) = -ln(alpha) / n   
    1.0 + bet * (noerror - 0.5) = e^(-ln(alpha) / n)

    // let term = e^(-ln(alpha) / n)
    1.0 + bet * (noerror - 1/2) = term
    (noerror - 1/2) = (term - 1)/bet
    noerror = (term - 1)/bet + 1/2

    // substitute noerror = 1/(2 - marginUpper)
    1/(2 - marginUpper) = (term - 1)/bet + 1/2
    1/(2 - marginUpper) = 2*(term - 1)/2*bet + bet/2*bet
    1/(2 - marginUpper) = (2*term - 2 + bet)/2*bet
    (2 - marginUpper) = 2*bet/(2*term - 2 + bet)
    marginUpper = 2 - 2*bet/(2*term - 2 + bet)

    val margin = marginUpper * upperBound()  // this would be the difference in scores except for the affine transform
    val mean = (margin + 1.0) / 2.0

    // see org.cryptobiotic.rlauxe.dhondt.DhondtAssorter.scoreRange(Npop: Int, nsamples: Int, alpha: Double)
    // the affine transform to create the assorter value is
    val mean = c * scoreDiff/Nc + 0.5
    val (mean - 0.5) * Npop / c = voteDiff

    scoreDiffMin = (mean - 0.5) * Nc / c
}

9/27
///////////////////////////////////////////////////////////////////////////////////////////

## one BH assertion fails. Example Bruxelles:

````
winning seats
 seat         winner-round     nvotes,   score, scoreDiff, maxRisk, maxAssertion
 ( 1)                  MR/1 ,  120155, 120155,           , 0.000, 
 ( 2)                  PS/1 ,   96516,  96516,      23639, 0.000, 
 ( 3)            PTB-PVDA/1 ,   86927,  86927,       9589, 0.000, 
 ( 4)                  MR/2 ,  120155,  60077,      26850, 0.000, 
 ( 5)               ECOLO/1 ,   58645,  58645,       1432, 0.000, 
 ( 6)         LES ENGAGÉS/1 ,   49425,  49425,       9220, 0.000, 
 ( 7)                  PS/2 ,   96516,  48258,       1167, 0.000, 
 ( 8)            PTB-PVDA/2 ,   86927,  43463,       4795, 0.000, 
 ( 9)                  MR/3 ,  120155,  40051,       3412, 0.000, 
 (10)                DéFI/1 ,   34143,  34143,       5908, 0.000, 
 (11)                  PS/3 ,   96516,  32172,       1971, 0.000, 
 (12)                  MR/4 ,  120155,  30038,       2134, 0.000, 
 (13)               ECOLO/2 ,   58645,  29322,        716, 0.000, 
 (14)            PTB-PVDA/3 ,   86927,  28975,        347, 0.000, 
 (15)         LES ENGAGÉS/2 ,   49425,  24712,       4263, 0.050, 
 (16)                  PS/4 ,   96516,  24129,        583, 0.511, PS/4-MR/5
                       MR/5 ,  120155,  24031,         98, 0.511, PS/4-MR/5
                 PTB-PVDA/4 ,   86927,  21731,       2300, 0.000, 
                       MR/6 ,  120155,  20025,       1706, 0.000, 
                    ECOLO/3 ,   58645,  19548,        477, 0.000, 
                       PS/5 ,   96516,  19303,        245, 0.000, 
                 PTB-PVDA/5 ,   86927,  17385,       1918, 0.000, 
````

maxRisk = max(BH assertions with w=winner) for winning candidates, or max(BH assertions with l=loser) for losing candidates

We know that the last seat is either PS/4 or MR/5.
All the seats below are still statistically eliminated from the BH assertions w=PS/4, which did not fail.

We know that an audit with more samples would either confirm the assertion PS/4 > MR/5 or continue to a hand audit.
If a hand audit showed that indeed PS/4 < MR/5, this audit does not tell us what the actual votes would end up. There could be a different configuration of ballots awarded. 

But within this risk limit I think we can say that the only variation is "the last seat is either PS/4 or MR/5".

## multiple BH assertions fail. Example Anvers:

````
winning seats
seat         winner-round     nvotes,   score, scoreDiff, maxRisk, maxAssertion
 ( 1)                N-VA/1 ,  368877, 368877,           , 0.000, 
 ( 2)       VLAAMS BELANG/1 ,  249826, 249826,     119051, 0.000, 
 ( 3)                N-VA/2 ,  368877, 184438,      65388, 0.000, 
 ( 4)             Vooruit/1 ,  127973, 127973,      56465, 0.000, 
 ( 5)                CD&V/1 ,  125894, 125894,       2079, 0.000, 
 ( 6)                PVDA/1 ,  125257, 125257,        637, 0.000, 
 ( 7)       VLAAMS BELANG/2 ,  249826, 124913,        344, 0.000, 
 ( 8)                N-VA/3 ,  368877, 122959,       1954, 0.000, 
 ( 9)                N-VA/4 ,  368877,  92219,      30740, 0.000, 
 (10)               GROEN/1 ,   90370,  90370,       1849, 0.000, 
 (11)       VLAAMS BELANG/3 ,  249826,  83275,       7095, 0.000, 
 (12)                N-VA/5 ,  368877,  73775,       9500, 0.000, 
 (13)            open vld/1 ,   70890,  70890,       2885, 0.000, 
 (14)             Vooruit/2 ,  127973,  63986,       6904, 0.000, 
 (15)                CD&V/2 ,  125894,  62947,       1039, 0.000, 
 (16)                PVDA/2 ,  125257,  62628,        319, 0.000, 
 (17)       VLAAMS BELANG/4 ,  249826,  62456,        172, 0.000, 
 (18)                N-VA/6 ,  368877,  61479,        977, 0.000, 
 (19)                N-VA/7 ,  368877,  52696,       8783, 0.000, 
 (20)       VLAAMS BELANG/5 ,  249826,  49965,       2731, 0.000, 
 (21)                N-VA/8 ,  368877,  46109,       3856, 0.000, 
 (22)               GROEN/2 ,   90370,  45185,        924, 0.002, 
 (23)             Vooruit/3 ,  127973,  42657,       2528, 0.136, Vooruit/3-PVDA/3
 (24)                CD&V/3 ,  125894,  41964,        693, 0.626, CD&V/3-PVDA/3
                     PVDA/3 ,  125257,  41752,        212, 0.626, CD&V/3-PVDA/3
            VLAAMS BELANG/6 ,  249826,  41637,        115, 0.383, CD&V/3-VLAAMS BELANG/6
                     N-VA/9 ,  368877,  40986,        651, 0.039, 
                    N-VA/10 ,  368877,  36887,       4099, 0.000, 
            VLAAMS BELANG/7 ,  249826,  35689,       1198, 0.000, 
                 open vld/2 ,   70890,  35445,        244, 0.000, 
````

There are 3 failing assertions.

We know that seat 24 is either CD&V/3, PVDA/3, or VLAAMS BELANG/6
We know that seat 23 is either Vooruit/3 or PVDA/3.

Do we need to add an assertion for Vooruit/3-CD&V/3 ? We dont care if they flip, but:

PDV/3 is in range of both CD&V/3 and Vooruit/3. If it displaces Vooruit/3, Vooruit/3 moves down but doesnt lose, unless the 
assertion Vooruit/3-CD&V/3 fails, then it might lose a seat. 

Perhaps this is where we need to to do all combinations, because we might be able to eliminate the possibility that Vooruit/3 loses a seat. Then the rule would be: if more than 1 seat is involved, add DH assertions between those winning seats.

So if Vooruit/3-CD&V/3 fails:

We know that Vooruit has 2 or 3 seats.
We know that CD&V has 2 or 3 seats.
We know that PVDA has 2 or 3 seats.
We know that VLAAMS BELANG has 5 or 6 seats.

If Vooruit/3-CD&V/3 succeeds:

We know that CD&V has 2 or 3 seats.
We know that PVDA has 2 or 3 seats.
We know that VLAAMS BELANG has 5 or 6 seats.

So when multiple seats are contested, go to a "second round", adding DH assertions between the contested winning candidates.

Perhaps also could add DH assertions between the candidates that may win seats. Because the scores depend on the divisor of the candidates, one might get some extra information.

Each party/divisor is a candidate, and the candidates are vying for seats. 
First decide what candidates might win. Then calculate party ranges.



Candidate A "blocks" Candidate B if A loses => B loses.
For example, for candidates in the same party, P/d1 blocks P/d2 if d1 < d2.




///////////////////////////////////////////////////////////////
from me

Imagine you take all possible pairs of candidates, where a candidate is a party and a divisor, and generates an assertion.

Now for each pair calculate the estimated ballots needed to satisfy the assertion, and discard the ones that are smaller than the sample size.

Whatever is left are the seats that are in play.


from Vanessa:

Right. Great. A few more details, which you've probably thought of already, but which I'll add because I've been dealing with a computer for too long.

- A. Discard anything that's not even expected to be true (e.g. if it seems that party P got n seats, we don't want DH(winner : P, loser : Q, winner_lowest_winner : n+1, anything)) - maybe this already shows up as an infinite sample size in your code.

- B. Discard anything that's logically implied by something else (e.g. if we have DH(winner : P, loser : Q, winner_lowest_winner : w, loser_highest_loser : l), we don't also need DH(winner : P, loser : Q, winner_lowest_winner : w-1, loser_highest_loser : l) because that's implied by arithmetic.

But (B) interacts in a complicated way with:
````
    changed how BT is compared to DH alternatives: DH assertions are generated for all winning candidates, 
    if BT < minimum of all the assertions, than all those assertions are substituted.
````
I think this is good, and I think it should probably come first, so the whole algorithm would be:

````
- Imagine you take all possible pairs of candidates, where a candidate is a party and a divisor, and generate a DH assertion. 
- Generate AT/BT assertions matching the apparent threshold-passing for each party.
- Now for each assertion calculate the estimated ballots needed to satisfy the assertion, and 
- (Step 1) discard the ones that have higher expected sample size than the user's target. 
- (A) Discard anything that's not even expected to be true (e.g. if it seems that party P got n seats, we don't want DH(winner : P, loser : Q, winner_lowest_winner : n+1, anything)) - maybe this already shows up as an infinite sample size in your code.
- (2, VT rewrite) For all parties P for which BT is apparently true, consider the DH assertions for all winning candidates vs P's 1st quotient. 
-- if sample_size( BT(P)) < sample_size(minimum of all the DH assertions vs P's 1st quotient), discard all the DH assertions;
-- if sample_size( BT(P)) > sample_size(minimum of all the DH assertions vs P's 1st quotient), discard BT(P).
- (B) Discard anything that's logically implied by something else (e.g. if we have DH(winner : P, loser : Q, winner_lowest_winner : w, loser_highest_loser : l), we don't also need DH(winner : P, loser : Q, winner_lowest_winner : w-1, loser_highest_loser : l) because that's implied by arithmetic.

Does that look right to you?

A few more thoughts about Step 2:
- it might be the case that one or the other side (either BT(P) or all the DH assertions against P's 1st quotient) have already been discarded, in which case obviously we can't discard the other. But I think that's OK because we're not going to try to discard the higher sample size one anyway.
- it might be that both have already been discarded, which is fine - we're just not going to prove that P got no seats.
- I've been advocating the RAIRE-style approximation in which we consider only the expected sample size of the *worst* assertion, and that's what you've written here (and I've copied). But now that it's written this way, it's clear we don't need to make that approximation. Instead of comparing  the sample size of BT(P) against the worst individual DH assertion, you could estimate the sample size of the whole set, which might sometimes be a bit larger.  (I don't think it's likely to matter a lot, and it makes the code a fair bit more complicated, but I'm just mentioning it in case it happens to be easier than I think.)
````

1. Imagine you take all possible pairs of candidates, where a candidate is a party and a divisor, and generate a DH assertion.

2. Generate AT/BT assertions matching the apparent threshold-passing for each party.

3. Now for each assertion calculate the estimated ballots needed to satisfy the assertion, and
- (Step 1) discard the ones that have higher expected sample size than the user's target.
- (A) Discard anything that's not even expected to be true (e.g. if it seems that party P got n seats, we don't want DH(winner : P, loser : Q, winner_lowest_winner : n+1, anything)) - maybe this already shows up as an infinite sample size in your code.

- (Step 2) For all parties P for which BT is apparently true, consider the DH assertions for all winning candidates vs P's 1st quotient.
  -- if sample_size( BT(P)) < sample_size(minimum of all the DH assertions vs P's 1st quotient), discard all the DH assertions;
  -- if sample_size( BT(P)) > sample_size(minimum of all the DH assertions vs P's 1st quotient), discard BT(P).

- (B) Discard anything that's logically implied by something else (e.g. if we have DH(winner : P, loser : Q, winner_lowest_winner : w, loser_highest_loser : l), we don't also need DH(winner : P, loser : Q, winner_lowest_winner : w-1, loser_highest_loser : l) because that's implied by arithmetic.


A few more thoughts about Step 2:
- it might be the case that one or the other side (either BT(P) or all the DH assertions against P's 1st quotient) have already been discarded, in which case obviously we can't discard the other. But I think that's OK because we're not going to try to discard the higher sample size one anyway.
- it might be that both have already been discarded, which is fine - we're just not going to prove that P got no seats.
- I've been advocating the RAIRE-style approximation in which we consider only the expected sample size of the *worst* assertion, and that's what you've written here (and I've copied). But now that it's written this way, it's clear we don't need to make that approximation. Instead of comparing  the sample size of BT(P) against the worst individual DH assertion, you could estimate the sample size of the whole set, which might sometimes be a bit larger.  (I don't think it's likely to matter a lot, and it makes the code a fair bit more complicated, but I'm just mentioning it in case it happens to be easier than I think.)


///////////////////////////////////////////////////////////////////////////////////////////
9/25

FlandreWest 24=5, 15=5, 28=3, 4=2, 30=2, 10=2, 19=1

reported (R)

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |     
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | X   | X  | X   | X   |     |   
| 3 | X   | X   | X   |    |     |     |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     |     | O  | 0   | 0   | 0   | 
| 4 |     |     | O   | 0  | 0   | 0   | 0   | 
| 5 |     |     | O   | 0  | 0   | 0   | 0   | 
| 6 | O   | 0   | 0   | 0  | 0   | 0   | 0   | 
| 7 | 0   | 0   | 0   | 0  | 0   | 0   | 0   | 

fails P28/3-P4/3   629/2805 = 800/3567 -> 0.5078 risk

alternative A

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |  
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | Y   | X  | X   | X   |     |   
| 3 | X   | X   |     | Z  |     |     |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     | Z   |    | 0   | 0   | 0   |
| 4 |     |     | O   | Y  | 0   | 0   | 0   |
| 5 |     |     | O   | 0  | 0   | 0   | 0   |
| 6 | O   | 0   | 0   | 0  | 0   | 0   | 0   |
| 7 | 0   | 0   | 0   | 0  | 0   | 0   | 0   | 

fails P4/3-P30/3  2224/2805 = ?

alternative AA

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |  
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | X   | X  | X   | X   |     |   
| 3 | X   | X   |     |    | Z   |     |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     | 0   | Z  |     | 0   | 0   |
| 4 |     |     | O   | 0  | 0   | 0   | 0   |
| 5 |     |     | O   | 0  | 0   | 0   | 0   |
| 6 | O   | 0   | 0   | 0  | 0   | 0   | 0   |
| 7 | 0   | 0   | 0   | 0  | 0   | 0   | 0   | 

fails P30/3-24/6: 585/2104
fails P30/3-15/6: 1155/2104
fails P30/3-10/3: 1762/3506

alternative AAA

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |  
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | X   | X  | X   | X   |     |   
| 3 | X   | X   |     |    |     |     |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
| 6 | Z   |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     | 0   | 0  | Z   | 0   | 0   |
| 4 |     |     | O   | 0  | 0   | 0   | 0   |
| 5 |     |     | O   | 0  | 0   | 0   | 0   |
| 6 |     | 0   | 0   | 0  | 0   | 0   | 0   |
| 7 | Y   | 0   | 0   | 0  | 0   | 0   | 0   | 

alternative AAB

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |  
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | X   | X  | X   | X   |     |   
| 3 | X   | X   |     |    |     |     |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
| 6 |     | Z   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     | 0   | 0  | Z   | 0   | 0   |
| 4 |     |     | O   | 0  | 0   | 0   | 0   |
| 5 |     |     | O   | 0  | 0   | 0   | 0   |
| 6 | O   |     | 0   | 0  | 0   | 0   | 0   |
| 7 | 0   | Y   | 0   | 0  | 0   | 0   | 0   | 

alternative AAC

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |  
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | X   | X   | X   | X  | X   | X   | X   |  
| 2 | X   | X   | X   | X  | X   | X   |     |   
| 3 | X   | X   |     |    |     | Z   |     |  
| 4 | X   | X   |     |    |     |     |     |   
| 5 | X   | X   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | 0   |   
| 3 |     |     | 0   | 0  | Z   |     | 0   |
| 4 |     |     | O   | 0  | 0   | Y   | 0   |
| 5 |     |     | O   | 0  | 0   | 0   | 0   |
| 6 | O   | 0   | 0   | 0  | 0   | 0   | 0   |
| 7 | 0   | 0   | 0   | 0  | 0   | 0   | 0   | 

             AAA
R - A - AA - AAB
             AAC    


//////////////////////////

(23)             Vooruit/3 ,  127973,  42657,       2528,
(24)                CD&V/3 ,  125894,  41964,        693,
                    PVDA/3 ,  125257,  41752,        212,   1360*
           VLAAMS BELANG/6 ,  249826,  41637,        327,   1020*       PVDA/3-VLAAMS BELANG/6: 115, 1020*;
                    N-VA/10 ,  368877,  36887,       5077,    884                PVDA/3-N-VA/10: 4865, 884 ;      VLAAMS BELANG/6-N-VA/10: 


(A) : AltContest fromFailure=failed 'DHondt w-l=Vooruit/3-PVDA/3: noerror=0.5006' has 1884/2828 samples : risk = 0.1359 cumul=0.1359


     skipAssertions=[DHondt w-l=Vooruit/3-PVDA/3, DHondt w-l=PVDA/3-Vooruit/3, DHondt w-l=PVDA/3-VLAAMS BELANG/6, DHondt w-l=VLAAMS BELANG/6-PVDA/3, DHondt w-l=PVDA/3-N-VA/9, DHondt w-l=N-VA/9-PVDA/3, DHondt w-l=PVDA/3-CD&V/3, DHondt w-l=CD&V/3-PVDA/3, DHondt w-l=Vooruit/3-CD&V/3, DHondt w-l=CD&V/3-Vooruit/3]
        failed 'DHondt w-l=PVDA/3-VLAAMS BELANG/6: noerror=0.5001' has 1884/16656 samples : risk = 0.7140
        failed 'DHondt w-l=PVDA/3-N-VA/9: noerror=0.5007' has 1884/2228 samples : risk = 0.0794
        failed 'DHondt w-l=PVDA/3-CD&V/3: noerror=0.4999' has 1884/0 samples : risk = 1.5963
        failed 'DHondt w-l=Vooruit/3-CD&V/3: noerror=0.5004' has 1884/3693 samples : risk = 0.2171