# Belgium D'Hondt Elections
_last changed 09/28/2026_

## Belgium Elections

Belgiun has eleven _Constituencies_ which each run an independent election. Each election has a single contest, in which the voter chooses one party. There is a common set of parties across the country, and the set of parties running in each constituency is a subset. Each constituency is allocated some number of seats using d'Hondt proportional allocation, and the election decides how many seats are awarded to each party.

Within each constituency, each party is reported to have gotten nvotes(party). A party must get a minimum number of votes (5%) for it to be awarded any seats, called the _threshold_. For each party, nvotes(party) is divided by a divisor that are consecutive integers, denoted party/1, party/2, party/3 ... These are called _scores_ or _candidates_. The seats are awarded to the highest scores, and once a candidate has been awarded a seat, the next divisor is used for that party.

We will use the Belgium 2024 election as our example thoughout. Here are the winning seats for Anvers, plus the next 6 non-winning scores:

````
DhondtContest 'Anvers' winning seats

 seat          party/divisor,  nvotes,  score, 
 ( 1)                N-VA/1 ,  368877, 368877, 
 ( 2)       VLAAMS BELANG/1 ,  249826, 249826,   
 ( 3)                N-VA/2 ,  368877, 184438,
 ( 4)             Vooruit/1 ,  127973, 127973, 
 ( 5)                CD&V/1 ,  125894, 125894,
 ( 6)                PVDA/1 ,  125257, 125257, 
 ( 7)       VLAAMS BELANG/2 ,  249826, 124913,
 ( 8)                N-VA/3 ,  368877, 122959,
 ( 9)                N-VA/4 ,  368877,  92219,
 (10)               GROEN/1 ,   90370,  90370, 
 (11)       VLAAMS BELANG/3 ,  249826,  83275, 
 (12)                N-VA/5 ,  368877,  73775,
 (13)            open vld/1 ,   70890,  70890, 
 (14)             Vooruit/2 ,  127973,  63986, 
 (15)                CD&V/2 ,  125894,  62947,  
 (16)                PVDA/2 ,  125257,  62628, 
 (17)       VLAAMS BELANG/4 ,  249826,  62456, 
 (18)                N-VA/6 ,  368877,  61479, 
 (19)                N-VA/7 ,  368877,  52696, 
 (20)       VLAAMS BELANG/5 ,  249826,  49965,  
 (21)                N-VA/8 ,  368877,  46109,  
 (22)               GROEN/2 ,   90370,  45185, 
 (23)             Vooruit/3 ,  127973,  42657,  
 (24)                CD&V/3 ,  125894,  41964,
                     PVDA/3 ,  125257,  41752,  
            VLAAMS BELANG/6 ,  249826,  41637,    
                     N-VA/9 ,  368877,  40986, 
                    N-VA/10 ,  368877,  36887, 
            VLAAMS BELANG/7 ,  249826,  35689,  
                 open vld/2 ,   70890,  35445,  
 ````

Another way to summarize the results:

````
                  party    Round:   1 |           2 |           3 |           4 |           5 |           6 |           7 |           8 |           9 |
2         BELG.UNIE-BUB*:        1686 |         843 |         562 |         421 |         337 |         281 |         240 |         210 |         187 |
4                  CD&V :  125894 (5) |  62947 (15) |  41964 (24) |       31473 |       25178 |       20982 |       17984 |       15736 |       13988 |
7            DierAnimal*:       10341 |        5170 |        3447 |        2585 |        2068 |        1723 |        1477 |        1292 |        1149 |
10                GROEN :  90370 (10) |  45185 (22) |       30123 |       22592 |       18074 |       15061 |       12910 |       11296 |       10041 |
15                 N-VA :  368877 (1) |  184438 (3) |  122959 (8) |   92219 (9) |  73775 (12) |  61479 (18) |  52696 (19) |  46109 (21) |       40986 |
19                 PVDA :  125257 (6) |  62628 (16) |       41752 |       31314 |       25051 |       20876 |       17893 |       15657 |       13917 |
21        Partij BLANCO*:        7221 |        3610 |        2407 |        1805 |        1444 |        1203 |        1031 |         902 |         802 |
24        VLAAMS BELANG :  249826 (2) |  124913 (7) |  83275 (11) |  62456 (17) |  49965 (20) |       41637 |       35689 |       31228 |       27758 |
25          Volt Europa*:        4213 |        2106 |        1404 |        1053 |         842 |         702 |         601 |         526 |         468 |
26               Voor U*:        8639 |        4319 |        2879 |        2159 |        1727 |        1439 |        1234 |        1079 |         959 |
28              Vooruit :  127973 (4) |  63986 (14) |  42657 (23) |       31993 |       25594 |       21328 |       18281 |       15996 |       14219 |
30             open vld :  70890 (13) |       35445 |       23630 |       17722 |       14178 |       11815 |       10127 |        8861 |        7876 |

* failed threshold
````

## D'Hondt Assertions

To fit the d'Hondt into the SHANGRLA framework, we create a set of assertions which proves the reported results within a given risk limit. We have three kinds of assertions:

**DH assertion** tests that a winning candidate's score is higher than a losing candidate's score. It suffices to create a DH assertion between each party's _lowest winning score_ and every other party's _highest losing score_.

**Below Threshold assertion** is generated for every party with less than the threshold number of votes.

**Above Threshold assertion** is generated for every party with more than the threshold number of votes.

Each assertion has a _margin_ and an _upper bound_ that depend on the winning and losing totalVotes and the divisor of the winning and losing candidate. For DH assertions this is:

        val lw = loserDivisor/winnerDivisor
        val margin = (winnerTotalVotes * lw - loserTotalVotes)/Nc
        val upper = (lw + 1)/2

Because DH assorters' upper bounds vary widely, we look for the assorter with the smallest noerror term instead of margin:

        val noerror = 1.0 / (2.0 - margin / upper) // clca assort value when no error

This is because for CLCA audits, the test statistic T depends on margin / upper, not just margin.
    
        If we approximate µ_i = .5 and use a constant bet of λc, then the payoff when there is no error
           val payoff ~= (1 + λc (noerror − .5) 
           
        After n noerror samples:
            val T_n = (1 + λc (noerror − .5))^n
            
        From which we approximate n as    
         val estSampleSize =~  ln(1/alpha) / ln(payoff)

The dependence on the winning and losing candidate divisors means that an assertion's estSampleSize isnt a simple function of the difference in scores.

### Blocking

It suffices to create a DH assertion between each party's _lowest winning score_ and every other party's _highest losing score_.

We can expand noerror as

        val lw = loserDivisor/winnerDivisor
        val noerror = (lw + 1)/2 / ((lw + 1) - (winnerTotalVotes * lw - loserTotalVotes)/Nc)
        1 / mo

Consider the DH assertion Dh(winParty/dw, loseParty/dl)

    fun g(partyVote: Int): Double {
        return if (partyVote == winner) 1.0 / winnerDivisor
            else if (partyVote == loser) -1.0 / loserDivisor
            else 0.0
    }

    // h transforms from [-1.0 / loserDivisor, 1.0 / winnerDivisor] to [0, upper]
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

val upperg = 1.0 / winnerDivisor  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
val lowerg = -1.0 / loserDivisor  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)


Candidate A "blocks" Candidate B if A loses => B loses.
For example, for candidates in the same party, P/d1 blocks P/d2 if d1 < d2.

TODO


## Failed Assertions

If a constituency limits the number of ballots to less than is needed to prove all assertions, then some of the assertions will fail to reach the risk limit. 

### DH Assertion failures



Failed assertions divide the seats into 3 groups. 

**Yellow seats**: Seats that have a failing assertion.

**Green seats**: Seats above the yellow are definite winners. Each is either blocked by a seat of the same party below it, or if it is the lowest winning candidate, has a non-failing assertion for every other candidate, which confirms its winning status.

**Red seats**: Seats below the yellow are definite losers. Each is either blocked by a seat of the same party above it, or if it is the highest losing candidate, has a non-failing assertion for every other candidate, which confirms its losing status.

Switching to FlandresEast as the example constituency, which has a single failing assertion. This needs 3567 samples to prove it, but we only have 800, and the risk limit is 0.51, instead of the target 0.05 :

````
Failures
                     name, winningSeat, noerror,  estMvrs, samplesUsed,   risk
         Vooruit/3-CD&V/3,          20,  0.5004,     3567,         800, 0.5112,

winning seats
 seat         winner-round     nvotes,   score, scoreDiff, maxRisk, maxAssertion
 ( 1)       VLAAMS BELANG/1 ,  234888, 234888,           , 0.000, 
 ( 2)                N-VA/1 ,  231470, 231470,       3418, 0.000, 
 ( 3)             Vooruit/1 ,  127758, 127758,     103712, 0.000, 
 ( 4)                CD&V/1 ,  125871, 125871,       1887, 0.000, 
 ( 5)            open vld/1 ,  119200, 119200,       6671, 0.000, 
 ( 6)       VLAAMS BELANG/2 ,  234888, 117444,       1756, 0.000, 
 ( 7)                N-VA/2 ,  231470, 115735,       1709, 0.000, 
 ( 8)               GROEN/1 ,  103722, 103722,      12013, 0.000, 
 ( 9)       VLAAMS BELANG/3 ,  234888,  78296,      25426, 0.000, 
 (10)                N-VA/3 ,  231470,  77156,       1140, 0.000, 
 (11)                PVDA/1 ,   75942,  75942,       1214, 0.000, 
 (12)             Vooruit/2 ,  127758,  63879,      12063, 0.000, 
 (13)                CD&V/2 ,  125871,  62935,        944, 0.000, 
 (14)            open vld/2 ,  119200,  59600,       3335, 0.000, 
 (15)       VLAAMS BELANG/4 ,  234888,  58722,        878, 0.000, 
 (16)                N-VA/4 ,  231470,  57867,        855, 0.000, 
 (17)               GROEN/2 ,  103722,  51861,       6006, 0.000, 
 (18)       VLAAMS BELANG/5 ,  234888,  46977,       4884, 0.001, 
 (19)                N-VA/5 ,  231470,  46294,        683, 0.003, 
 **(20)           Vooruit/3 ,  127758,  42586,       3708, 0.511, Vooruit/3-CD&V/3 **
 **                  CD&V/3 ,  125871,  41957,        629, 0.511, Vooruit/3-CD&V/3 **
                 open vld/3 ,  119200,  39733,       2224, 0.047, 
            VLAAMS BELANG/6 ,  234888,  39148,        585, 0.007, 
                     N-VA/6 ,  231470,  38578,        570, 0.003, 
                     PVDA/2 ,   75942,  37971,        607, 0.019, 
                    GROEN/3 ,  103722,  34574,       3397, 0.000,
````

The two scores demarked by ** are "in play"; the one above are green, and the ones below are red, which we can visualize as:

````
FlandreWest winning seats: 24=5, 15=5, 28=3, 4=2, 30=2, 10=2, 19=1

|   | P24 | P15 | P28 | P4 | P30 | P10 | P19 |     
|---|-----|-----|-----|----|-----|-----|-----|
| 1 | G   | G   | G   | G  | G   | G   | G   |  
| 2 | G   | G   | G   | G  | G   | G   |     |   
| 3 | G   | G   | Y   |    |     |     |     |  
| 4 | G   | G   |     |    |     |     |     |   
| 5 | G   | G   |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
|   |     |     |     |    |     |     |     |  
| 1 |     |     |     |    |     |     |     |  
| 2 |     |     |     |    |     |     | R   |   
| 3 |     |     |     | R  | R   | R   | R   | 
| 4 |     |     | R   | R  | R   | R   | R   | 
| 5 |     |     | R   | R  | R   | R   | R   | 
| 6 | R   | R   | R   | R  | R   | R   | R   | 
| 7 | R   | R   | R   | R  | R   | R   | R   | 
````

### Relaxed DH Assertions and Party Seat Ranges

When some assertions fail, we can calculate for each party the possible range of seats that might be awarded.

For the seats that are in play, form the "reported yellow set" as the set of winning candidates in the yellow seats. This set has n unique candidates when there are n yellow seats.

The number of seats that a party might lose is bounded by the number of candidates it has in the reported yellow set.

For each failed assertion, add the losing candidate to the "failed assertion candidate" set. This set has k unique candidates when there are k failed assertions.

The number of seats that a party might gain is bounded by the number of candidates it has in the failed assertion candidate set.

These are conservative estimates, further work may be able to tighten these bounds. For example, it may be possible to prove that a winning candidate in the yellow seats must still win a seat at a lower rank.


## Below Threshold failures

If a party gets fewer votes than the threshold, its candidates are removed from the competition for seats. A BT (Below Threshold) assertion is added to verify this within the risk limit.

If the BT assertion does not have enough samples to verify, we generate an "alternate contest" in which the party's candidates compete for seats anyway. This create an alternative seat assignment. This widens the possible seat assignments, called the "Party Seat Ranges".

When there are multiple BT assertion failures, generate the 2^n combinations, generate an alternate contest for each combination, and feed each alternative seat assignment into the Party Seat Range algorithm.


## Above Threshold failures

TODO. same as BT?

## DH Assertion failures and Threshold Assertion failures

WHen there are both DH and Threshold failures, simply combine all the seat ranges.