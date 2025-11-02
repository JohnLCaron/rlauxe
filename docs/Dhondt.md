Proportional elections
10/26/2025

**Assertion-Based Approaches to Auditing Complex Elections, with Application to Party-List Proportional Elections**
2021

3. Creating assorters from assertions

In this section we show how to transform generic linear assertions, i.e. inequalities of the form

    Sum_b∈L { Sum_e∈E {ae * be } } > c

into canonical assertions using assorters as required by SHANGRLA. There are three steps:

1. Construct a set of linear assertions that imply the correctness of the outcome.
   (Constructing such a set is outside the scope of this paper; we suspect there is no
   general method. Moreover, there may be social choice functions for which there is no such set)
2. Determine a ‘proto-assorter’ based on this assertion.
3. Construct an assorter from the proto-assorter via an affine transformation.

We work with social choice functions where each valid ballot can contribute a non-negative (zero or more)
number of ‘votes’ or ‘points’ to various tallies (we refer to these as votes henceforth).

Let the various tallies of interest be T1, T2, ..., Tm for m different entities e∈E.
These represent the total count of the votes across all valid ballots.

Step 1. A linear assertion is a statement of the form

    a1*T1 + a2*T2 + ... + am*Tm > 0  for some constants a1 ,... , am 

For example, a pairwise majority assertion is usually written as pA > pB ,
stating that candidate A got a larger proportion of the valid votes than candidate B.

````
pA > pB
TA/TL > TB/TL
TA − TB > 0
````

a1 = 1, a2 = -1

Another example is a super/sub-majority assertion, pA > t, for some threshold t.

````
pA > t
TA/TL > t
TA > t * TL
TA − t * TL > 0
TA − t * Sum(Ti) > 0
(1-t) TA - t * {Ti, i != A} > 0

So the linear coefficients are:

aA = (1-t), ai = -t for i != A.

g(b) = a1 b1 + a2 b2 + · · · + am bm
     = (1-t)*bA + t*sum(bi, i != A)

so if vote is for A, g = (1-t)
   if vote for not B, r = -t
   else 0

lower bound a = -t
upper bound u = (1-t)
c = -1/2a
h = (g(b) - a)/-2a
````


Another example is an under threshold assertion, pA < t, for some threshold t.

````
pA < t
TA/TL < t
TA < t * TL
0 < t * TL - TA
0 < t * Sum(Ti) - TA
t * Sum(Ti) - TA > 0
(t-1) TA + t * {Ti, i != A} > 0

So the linear coefficients are:

  aA = (t-1), ai = t for i != A.

so if vote is for A, g = (t-1)
   if vote for not A, r = t
   else 0

lower bound a = (t-1)
upper bound u = t
c = -1/2a
h = (g(b) - a)/-2a
````

Step 2. For the given linear assertion, we define the following function on ballots, which we call a proto-assorter :

        g(b) = a1*b1 + a2*b2 + · · · + am*bm

where b is a given ballot, and b1 , b2 , . . . , bm are the votes contributed by that ballot to the tallies T1 , T2 , . . . , Tm respectively.

Summing this function across all ballots, Sum_b {g(b)}, gives the left-hand side of the linear assertion.
Thus, the linear assertion is true iff Sum_b {g(b)} > 0. 
The same property holds for the average across ballots, ḡ = Sum_b {g(b)}/L, L = total numver of ballots. The linear assertion is true iff ḡ > 0.

Step 3. To obtain an assorter in canonical form, we apply an affine transformation to g such that it never takes negative values 
and also so that comparing its average value to 1/2 determines the truth of the assertion. One such transformation is

    h(b) = c · g(b) + 1/2      (eq 1)

for some constant c. There are many ways to choose c. Here are two possibilities:

First, we determine a lower bound for the proto-assorter, a value _a_ such that _g(b) >= a_ for all b.
Note that a < 0 in all interesting cases: if not, the assertion would be trivially true (ḡ > 0) or trivially false (ḡ ≡ 0, with aj = 0 for all j).

If a >= −1/2, simply setting c = 1 produces an assorter: we have h ⩾ 0, and h̄ > 1/2 iff ḡ > 0. (option 1)

Otherwise, we can choose c = −1/2a (option 2). From eq 1, this gives:
   
    h(b) = -1/2a * g(b)  + 1/2
    h(b) = (g(b) − a) / −2a     (eq 2)

To see that h(b) is an assorter, first note that h(b) ⩾ 0 since the numerator is always non-negative and the denominator is positive. 
Also, the sum and mean across all ballots are, respectively:

    Sum_b {h(b)} = -1/2a * Sum_b {g(b)} + L/2
    h̄ = -1/2a ḡ + 1/2          (eq 3)

Therefore, h̄ > 1/2 iff ḡ > 0.

5.1 highest averages

A highest averages method is parameterized by a set of divisors d(1), d(2), . . . d(S) where S is the number of seats.
The divisors for D’Hondt are d(i) = i. Sainte-Laguë has divisors d(i) = 2i − 1.

Define

    fe,s = Te/d(s) for entity e and seat s.

5.2 Simple D’Hondt: Party-only voting

In the simplest form of highest averages methods, seats are allocated to each
entity (party) based on individual entity tallies. Let We be the number of seats
won and Le the number of the first seat lost by entity e. That is:

    We = max{s : (e, s) ∈ W}; ⊥ if e has no winners. this is e's lowest winner.
    Le = min{s : (e, s) !∈ W}; ⊥ if e won all the seats. this is e's highest loser.

The inequalities that define the winners are, for all parties A with at least
one winner, for all parties B (different from A) with at least one loser, as follows:

    fA,WA > fB,LB    A’s lowest winner beat party B’s highest loser
    TA/d(WA) > TB/d(LB)
    TA/d(WA) - TB/d(LB) > 0

From this, we define the proto-assorter for any ballot b as 

    g_AB(b) = 1/d(WA) if b is a vote for A
            = -1/d(WB) if b is a vote for B
            = 0 otherwisa

    or equivilantly, g_AB(b) = bA/d(WA) - bB/d(WB)

The lower bound is -1/d(WB)
The upper bound is 1/d(WA)
  h upper bound is h(1/d(WA)) ?

5.3  More complex methods: Multi-candidate voting

Like some Hamiltonian elections, many highest averages elections also allow
voters to select individual candidates. A party’s tally is the total of its candidates’
votes. Then, within each party, the won seats are allocated to the candidates with
the highest individual tallies. The main entities are still parties, allocated seats
according to Equation 4, but the assorter must be generalised to allow one ballot
to contain multiple votes for various candidates.

The proto-assorter for entities (parties) A != B s.t. WA !=⊥, and LB !=⊥, is
very similar to the single-party case, but votes for each party (bA and bB) count
the total, over all that entity’s candidates, and may be larger than one.

    gA,B(b) := bA/d(WA ) − bB/d(LB )

The lower bound is −m/d(LB ), again substituting in to Equation 2 gives

    hA,B(b) = (bA * d(LB)/d(WA) − bB + m ) / 2m


====================

roundIdx = 2
estSampleSize = 12040
estNewSampleSize = 11040
actualMvrs = 12040
actualNewMvrs = 11040
status = InProgress
included = true
done = false

DHondtContest 'Anvers' (1) DHONDT voteForN=1 votes={6=368877, 1=249826, 5=127973, 8=125894, 4=125257, 7=90370, 2=70890, 10=10341, 9=8639, 11=7221, 3=4213, 12=1686} undervotes=44400, voteForN=1
winners=[6, 1, 5, 8, 4, 7, 2] Nc=1235587 Np=0 Nu=44400 sumVotes=1191187
nseats=24 winners={6=8, 1=5, 5=3, 8=3, 4=2, 7=2, 2=1} belowMin=[3, 9, 10, 11, 12]
DHondt 8/4 votes=41964.7/41752.3 diff=212.3 (w-l)/w =0.00506

candidate          Round:           1 |           2 |           3 |           4 |           5 |           6 |           7 |           8 |           9 |
1        VLAAMS BELANG :  249826 (2) |  124913 (7) |  83275 (11) |  62456 (17) |  49965 (20) |       41637 |       35689 |       31228 |       27758 |
2             open vld :  70890 (13) |       35445 |       23630 |       17722 |       14178 |       11815 |       10127 |        8861 |        7876 |
3          Volt Europa*:        4213 |        2106 |        1404 |        1053 |         842 |         702 |         601 |         526 |         468 |
4                 PVDA :  125257 (6) |  62628 (16) |       41752 |       31314 |       25051 |       20876 |       17893 |       15657 |       13917 |
5              Vooruit :  127973 (4) |  63986 (14) |  42657 (23) |       31993 |       25594 |       21328 |       18281 |       15996 |       14219 |
6                 N-VA :  368877 (1) |  184438 (3) |  122959 (8) |   92219 (9) |  73775 (12) |  61479 (18) |  52696 (19) |  46109 (21) |       40986 |
7                GROEN :  90370 (10) |  45185 (22) |       30123 |       22592 |       18074 |       15061 |       12910 |       11296 |       10041 |
8                 CD&V :  125894 (5) |  62947 (15) |  41964 (24) |       31473 |       25178 |       20982 |       17984 |       15736 |       13988 |
9               Voor U*:        8639 |        4319 |        2879 |        2159 |        1727 |        1439 |        1234 |        1079 |         959 |
10           DierAnimal*:       10341 |        5170 |        3447 |        2585 |        2068 |        1723 |        1477 |        1292 |        1149 |
11        Partij BLANCO*:        7221 |        3610 |        2407 |        1805 |        1444 |        1203 |        1031 |         902 |         802 |
12        BELG.UNIE-BUB*:        1686 |         843 |         562 |         421 |         337 |         281 |         240 |         210 |         187 |




