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

    // see org.cryptobiotic.rlauxe.dhondt.DHondtAssorter.scoreRange(Npop: Int, nsamples: Int, alpha: Double)
    // the affine transform to create the assorter value is
    val mean = c * scoreDiff/Nc + 0.5
    val (mean - 0.5) * Npop / c = voteDiff

    scoreDiffMin = (mean - 0.5) * Nc / c
}