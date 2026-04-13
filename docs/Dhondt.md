# D'Hondt Notes
_last changed 02/05/2026_

<!-- TOC -->
* [D'Hondt Notes](#dhondt-notes)
  * [Notes From Bounding paper 04/12/2026](#notes-from-bounding-paper-04122026)
  * [Notes From Proportional paper 02/05/2026](#notes-from-proportional-paper-02052026)
    * [Section 3. Creating assorters from assertions](#section-3-creating-assorters-from-assertions)
    * [Section 5.1 highest averages](#section-51-highest-averages)
    * [Section 5.2 Simple D’Hondt: Party-only voting](#section-52-simple-dhondt-party-only-voting)
    * [Section 5.3  More complex methods: Multi-candidate voting](#section-53--more-complex-methods-multi-candidate-voting)
<!-- TOC -->

## Notes From Bounding paper 04/12/2026

2.1 Contest of the Audit

Approximately half of the Belgian voters vote on hand-marked and hand-counted
paper ballots. Voting happens on a Sunday morning, and counting offices op-
erate on Sunday afternoon and sometimes evening. Each counting office has 6
members, who may tally up to 2400 ballots. The ballots are sorted by lists,
then counted, then placed into sealed envelopes. All the ballots in any single
envelope must express votes for the same list.

(So, up to 2400 votes in an envelope that must be kept in order. Hainut example shows typical largest candidate has 30%, so 720)

The other half of the voters vote on ballot marking devices that print paper
ballots, which are scanned, and counted from the scanned electronic records.

("The audit would be based on an evolution of the current BMDs" which is what?)

2.2 Election manifest

The manifest typically contains other book keeping information, including
a list of boxes and envelopes and the reported content of each envelope.

In the context of hand-counted ballots, each envelope contains ballots supporting a
single list, and invalid ballots are also gathered in one envelope. In the context
of e-voting, each envelope contains ballots of which an interpretation has been
reported during the scanning process.

2.3
````
    * S = number of available seats 
    * d(i) divisor for ith index d(i) = i for dhondt
    * P is set of Parties, p ∈ P, ||p|| = number of candidates fielded by p
    * s :: P -> N ∪ {0} allocation os seats to parties
      s(p) is both the number of seats allocated to party p and (if non-zero)
      the index of the candidate in list p who is reported to get seated with the
      lowest quotient. (what is "lowest quotient"?) 
      = LW(p) "last winner"
    * FL(p)   = s(p) + 1    if s(p) < ||p||, otherwise undefined
      the index of the candidate in p who has the highest quotient
      of those who do not get a seat, if it exists
      "first loser"
    * T(p) = total number of valid votes for p
    * T(p,i) = quotient of the i-th ranked candidate of a list p = T(p) / d(i)
    
````

2.3.2

DH(A, B) = D’Hondt assertion that the last winner of list A won over the first loser of list B
        = T(A,LW(A)) > T(B,FL(B))

proto assorter g_AB(b) = b(A) / s(A) - b(B) / FL(B)
    where b(p) is set to 1 if the ballot b is a vote for p, and set to 0 otherwise

assorter h_AB(b) = b(A) * FL(B) / 2 * s(A)  + (1 - b(B)) / 2 
                 = 0 for a vote for B, = 1/2 for a vote for other, = FL(B) / 2 * s(A) vote for A

2.4  Adding a Threshold

 T(V) = number of valid votes
theta = T(V) * .05

above threshold AT(p) = T(p) > theta
below threshold BT(p) = T(p) < theta

AboveThreshold

ta_(b) = b(A)/2*theta  + b_INV / 2  (what is b_INV ?? probably typo)

general formula once you have g is

    h(b) = c * g(b) + 1/2
       c = -1/(2a)
       a = lower bound of g

    for AT, g =  (1 - t) if vote for A else -t, so a = -t, c = 1/2t
    then h(b) =  g(b)/2t + 1/2

using b(p) = 1 if the ballot b is a vote for p, and set to 0 otherwise
   g(b) = b(p) - t
   h(b) = (b(p) - t)/2t + 1/2
        = b(p)/2t - t/2t + 1/2
        = b(p)/2t

(paper has ta_(b) = b(A)/2*theta  + b_INV / 2  (what is b_INV ?? probably typo))

other formulation with c = 1 (not used)
    If a >= −1/2, simply setting c = 1 produces an assorter: we have h ⩾ 0, and h̄ > 1/2 iff ḡ > 0. (option 1)

    h(b) = c * g(b) + 1/2   
    h(b) = g(b) + 1/2   
    h(b) = b(p) - t + 1/2

BelowThreshold

    g(b) = if (vote == candId) t-1 else t   
    a = lowerbound = t-1 
    c = -1/(2a) = -1/(2*(t-1))

using b(p) = 1 if the ballot b is a vote for p, and set to 0 otherwise

    g(b) = -b(p) + t  

    h(b) = c * g(b) + 1/2
         = -1/(2*(t-1)) * g(b) + 1/2
         = -g(b)/(2*(t-1)) + 1/2
         = -g(b)/(2*(t-1)) + (t-1)/2(t-1)
         = (-g(b) + (t-1))/(2(t-1))
         = (b(p) - t  + t-1)/(2(t-1))
         = (b(p)-1) / (2(t-1))


3.1 Check margins

"estimate (the difficulty) using the reported margins".

Instead of margins, better to use noerror, which takes into account the upper limit of the assorter, which != 1 for DH, AT, BT.

noerror = 1.0 / (2.0 - assorterMargin / assorter.upperBound())

when you get a noerror sample, your payoff is

payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (taking µ_i approximately 1/2)

where 
* taking µ_i approximately 1/2
* can take λ as constant
* noerror always > 1/2

large noerror is, the larger the payoff is.

and you need N of these to get over the risk limit

payoff_noerror^N > 1/risk

N = ln(1/risk / ln(payoff_noerror))

The large the payoff is, the smaller N is.

This is "Betting martingale" specific, but I cant imagine how it could be different for another risk function.

It could be that the margin should be defined differently, in order to make margin the correct measure of difficulty ?? 
Perhaps assorterMargin / assorter.upperBound() ??
Probably not, we need mean = assorter mean, margin = 2.0 * mean - 1.0

how about noerrorMargin = 2.0 * noerror - 1.0  = 2 * (noerror - 1/2) 

noerror = 1.0 / (2.0 - margin / upper)

noerrorMargin = 2.0 * noerror - 1.0 = 2/(2 - margin/upper) - 1 = (2 - (2 - margin/upper)) /(2 - margin/upper) = (margin/upper) / (2 - margin/upper) = margin/(2 - margin) when upper = 1

mean = (margin + 1.0) / 2.0

  margin = 2 * mean - 1
nomargin = 2 * noerror - 1

noerror = 1 / (2 - margin/upper) = 1 / (2 - (2 * mean - 1)/upper)

1/noerror = (2 - (2 * mean - 1)/upper)  = 2 - 2*mean/upper - 1/upper
upper/noerror = 2*upper - 2 * mean - 1



nomargin = (margin/upper) / (2 - margin/upper)


= maru / (2 - maru) = margin/(2 - margin) when u = 1

3.2 Compare expectaions using ALPHA martingales.

Presume this subsumes BettingMarts.

/////////////////////////////////////////////////////////////////////

/usr/lib/jvm/jdk-21-oracle-x64/bin/java -javaagent:/home/stormy/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/plugins/java/lib/rt/debugger-agent.jar=file:///tmp/capture12483755947381890058.props -ea -Didea.test.cyclic.buffer.size=1048576 -javaagent:/home/stormy/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/lib/idea_rt.jar=35521 -Dkotlinx.coroutines.debug.enable.creation.stack.trace=false -Ddebugger.agent.enable.coroutines=true -Dkotlinx.coroutines.debug.enable.flows.stack.trace=true -Dkotlinx.coroutines.debug.enable.mutable.state.flows.stack.trace=true -Ddebugger.async.stack.trace.for.all.threads=true -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 @/tmp/idea_arg_file1598434137 com.intellij.rt.junit.JUnitStarter -ideVersion5 -junit5 org.cryptobiotic.rlauxe.dhondt.TestBelgiumContest,testShowDhondtAssertions
Anvers (1) Nc=1235587 Nphantoms=0 votes={6=368877, 1=249826, 5=127973, 8=125894, 4=125257, 7=90370, 2=70890, 10=10341, 9=8639, 11=7221, 3=4213, 12=1686} undervotes=44400, voteForN=1

seat             party/round     nvotes,  score, voteDiff,  
( 1)                  N-VA / 1,  368877, 368877,
( 2)         VLAAMS BELANG / 1,  249826, 249826,  119051,
( 3)                  N-VA / 2,  368877, 184438,   65388,
( 4)               Vooruit / 1,  127973, 127973,   56465,
( 5)                  CD&V / 1,  125894, 125894,    2079,
( 6)                  PVDA / 1,  125257, 125257,     637,
( 7)         VLAAMS BELANG / 2,  249826, 124913,     344,
( 8)                  N-VA / 3,  368877, 122959,    1954,
( 9)                  N-VA / 4,  368877,  92219,   30740,
(10)                 GROEN / 1,   90370,  90370,    1849,
(11)         VLAAMS BELANG / 3,  249826,  83275,    7095,
(12)                  N-VA / 5,  368877,  73775,    9500,
(13)              open vld / 1,   70890,  70890,    2885,
(14)               Vooruit / 2,  127973,  63986,    6904,
(15)                  CD&V / 2,  125894,  62947,    1039,
(16)                  PVDA / 2,  125257,  62628,     319,
(17)         VLAAMS BELANG / 4,  249826,  62456,     172,
(18)                  N-VA / 6,  368877,  61479,     977,
(19)                  N-VA / 7,  368877,  52696,    8783,
(20)         VLAAMS BELANG / 5,  249826,  49965,    2731,
(21)                  N-VA / 8,  368877,  46109,    3856,
(22)                 GROEN / 2,   90370,  45185,     924,
(23)               Vooruit / 3,  127973,  42657,    2528,
(24)                  CD&V / 3,  125894,  41964,     693,
.
                   party/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion
                      PVDA / 3,  125257,  41752,     212,  0.500129,   0.0003,      12345,      23454, 0.7802, winner CD&V/3 loser PVDA/3
                                                     905,  0.500550,   0.0011,    2433444,    3434344, 0.3469, winner Vooruit/3 loser PVDA/3
.
             VLAAMS BELANG / 6,  249826,  41637,     327, 0.500265, 0.0005, 0.6006, winner CD&V/3 loser VLAAMS BELANG/6
                                                    1020, 0.500827, 0.0017, 0.2037, winner Vooruit/3 loser VLAAMS BELANG/6
.
                      N-VA / 9,  368877,  40986,     978, 0.500892, 0.0018, 0.1796, winner CD&V/3 loser N-VA/9
                                                    1671, 0.501526, 0.0031, 0.0531, winner Vooruit/3 loser N-VA/9

(or) contested seats
.
                   party/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion
(23)               Vooruit / 3,  127973,  42657,    2528,
                      PVDA / 3,  125257,  41752,     905,  0.500550,   0.0011,    2433444,    3434344, 0.3469, winner Vooruit/3 loser PVDA/3
               VLAAMS BELANG/6,  125257,  41752,     905,  0.500550,   0.0011,    2433444,    3434344, 0.3469, winner Vooruit/3 loser PVDA/3
                        N-VA/9,  125257,  41752,     905,  0.500550,   0.0011,    2433444,    3434344, 0.3469, winner Vooruit/3 loser PVDA/3

(24)                  CD&V / 3,  125894,  41964,     693,


/////////////////////////////////////////////////
## Notes From Proportional paper 02/05/2026

Notes From [Assertion-Based Approaches to Auditing Complex Elections, with Application to Party-List Proportional Elections](http://arxiv.org/abs/2107.11903v2)

### Section 3. Creating assorters from assertions

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

g lower bound is a = -t
g upper bound is u = (1-t)
c = -1/2a
h = g * c + 1/2 = (g(b) - a)/-2a = (g(b) + t)/2t
h upper bound is h(g upper) = h(1 - t) = (1 - t + t ) /  2t = 1/2t
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
c = -1/2a = 1/2(1-t)
h = c * g + 1/2 = g/2(1-t) + 1/2 = (g + 1 - t)/2(1-t)
h upper bound is h(g upper) = (t + 1 - t)/2(1-t) = 1/2(1-t)

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

### Section 5.1 highest averages

A highest averages method is parameterized by a set of divisors d(1), d(2), . . . d(S) where S is the number of seats.
The divisors for D’Hondt are d(i) = i. Sainte-Laguë has divisors d(i) = 2i − 1.

Define

    fe,s = Te/d(s) for entity e and seat s.

### Section 5.2 Simple D’Hondt: Party-only voting

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

g lower bound is -1/d(WB) = -1/first (lowest winner)
g upper bound is 1/d(WA)  = 1/last   (highest loser)
c = -1.0 / (2 * lower) = first/2
h upper bound is h(g upper) = h(1/last) * c + 1/2 = (1/last) * first/2 + 1/2 = (first/last+1)/2

first and last both range from 1 to nseats, so 
    min upper is (1/nseats + 1)/2 which is between 1/2 and 1
    max upper is (nseats + 1)/2 which is >= 1

### Section 5.3  More complex methods: Multi-candidate voting

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




