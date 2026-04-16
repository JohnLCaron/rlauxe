# Publicly Verifiable RLAs
5/23/2026

Paper Submission #147

## 1. Introduction

An interactive proof [4] is a formal protocol between
two parties: a computationally unbounded prover (in our
case, the electoral authority) and a resource-bounded verifier
(in our case, the public).

The interaction proceeds as a dialogue: the verifier issues randomized chal-
lenges, and the prover responds. Two properties are gener-
ally of interest: completeness and soundness. Completeness
means that if the statement is true and the prover follows the
protocol honestly, the verifier accepts with high probability.
Soundness means that if the statement is false, no (even
malicious) prover can convince the verifier to accept, except
with a small probability (in our case, the risk limit plus a
negligible extra).

We distinguish between ballots and ballot cards (or cards). A card is
a piece of paper. A ballot includes all the contests a given voter is eligible
to vote in. In some democracies there is only one contest per piece of
paper, but in others (e.g., Switzerland and the USA) electors typically vote
on many questions in one election, so a ballot comprises multiple cards.

* The electoral authority takes the role of _prover_.
* The public takes the role of _verifier_.
* The _instance_ x is a trusted physical collection of paper ballots.
* The _Cast Vote Record_ (CVR) is an electronic records purporting to be the voters’ selections on individual ballot cards.
* The _reported outcome_ is the outcome claimed by the prover. 
* The _correct outcome_ is the true winner(s) according to the expressed preference on every cast ballot card(s) in the instance.
* The _risk-limiting property_ is an upper bound on the probability that the verifier accepts an apparent outcome that
is actually wrong. The risk-limiting property is a soundness property.

## 2. Model

**Interactive proofs**. 
An IP is two interactive, randomized,
polytime algorithms: the prover P and verifier V . In an IP,
the prover P attempts to convince a verifier V that some data
called the instance has a specified property. The instance
is an input to both parties. In the protocol, P and V can
exchange messages and flip coins. At the end of the protocol,
V either accepts or rejects.

**RLAs**. 
RLAs resemble IPs. In an RLA, the instance is
a collection of physical ballots and a claim about their
outcome. The prover P is the election official. The verifier V
is the auditor.
    The prover P and verifier V engage in a protocol
that only requires V to check a few random ballots, yet does
not require V to trust P .
    The physical details of RLAs, however, mean that they
must be modeled as a new kind of IP. Crucially, in an RLA,
the physical ballots are stored in boxes, but are not organized
or indexed in a way that the auditor can trust. The ballots
might have IDs on them, but the auditor cannot assume these
are unique. And, while an official might claim that some
ballot is the ith (in some order), the auditor can not trust
that claim. So, when an auditor receives a ballot, they do
not know which ballot they’ve received. Fortunately, since
they observe the retrieval process, they can assume that the
ballot was not modified during retrieval.
    Since the auditor cannot control which data they ex-
amine, auditing is hard. Many recent RLAs deal with this
difficulty using a clever observation: it helps to have some
organization system for the ballots, even if the system’s
consistency cannot be trusted. Thus, before the audit begins,
the official labels each ballot with an ID. These are typically
printed on all ballots before voting, or they might be written
in red during the official tally.
    When V gets a ballot xi , they also see its (possibly non-
unique) ID. IDs are fixed before the audit begins; during the
audit, ballots and IDs cannot be modified.
The two features above—V ’s indirect access to ballots
and the use of (possibly duplicate) IDs—warrant a new kind
of IP, which we define below.

**Intermediated holographic proof (IHP)**
An IHP comprises two randomized, polytime algorithms:
the prover P and verifier V . The instance comprises a ballot
array _x_ of size N . Initially, the prover P fixes an ID array _id_.
⃗The verifier V cannot directly access x or id. Instead,
V asks for ballot i, P chooses i′ , and V receives (x_i′, id_i′),
but not i′ . Additionally, P and V can exchange messages
and flip coins.
    An IHP is _ϵ-computationally sound_ if for all instances
(of size N) that are not in the language, for all efficient
provers P∗, P∗ convinces V with probability at most ϵ. It
is _δ-complete_ if for all instances of size at most N in the
language, P convinces V with probability at least 1 − δ .
    In our model, V ’s queries are resolved by P .
That is, P intermediates the queries. IHPs are designed to yield a lightweight, in-person
RLA about physical ballots. The motivation
of IHPs is different: to yield a in-person protocol that
executes over physical ballots. Thus, some cryptographic
tools are not applicable, P is harder to constrain, and IHPs
do not assume that P answers queries correctly
    First, in RLAs, ballots are assumed to be unmodifiable during the audit; in
an IHP, the array  x is immutable. Second, in RLAs, the
ballots are unorganized and retrieved through help from the
election operator, so the auditor does not know which ballot
it is given; in an IHP, P intermediates V ’s queries. Third,
in an RLA, the auditor takes all actions in public view; thus
we will focus on public-coin IHPs. Last, in RLAs, sampling
randomness and retrieving ballots is typically the bottleneck;
thus we will focus on IHPs with little randomness and few
queries.

## 4. Main construction

An election-generic RLA that uses a PRG.

Let V be the set of possible votes, including an illegible vote BAD.

Definition 1. An assertion A is a predicate on multisets of
V . It must be BAD-invariant: for any vote multiset M,
A (M) ⇐⇒ A (M ⊎ {{BAD}}) .

Assorters. An assorter a(c, m) function from a CVR c
and an MVR m to a “evidence” amount e in the interval
[0, ua ], where ua is an assorter-specific upper bound

Definition 2. Assorter a expresses assertion A if for all
CVR sequences  c and all MVR sequences m of length N ,
    1/N Sum{ a(ci,mi) } > 1/2   
implies ⇒ that A(m) is true, ie the assertion holds if the assorter mean
is at least 1/2.

Furthermore, the vote BAD should be evidence-minimal:
    ∀c, m ∈ V, a(c, BAD) ≤ a(c, m)

Our RLA will need to sample a ballot sequence
π of length T , that is, an element of (1..N )^T (pick T element of ballots from (1..N))

**Sampling strategy** is a function S : {0, 1}^L → (1..N)^T that maps a large
and uniformly random bit sequence to a ballot sequence
from the distribution defined by S

A **p-value calculator** P-VAL for a given
sampling strategy S is a family of functions, indexed by (TODO why a family of functions?)
1 ≤ t ≤ T . Each function inputs a sequence of t assorter
values: (e_πi),i=1..t . For all  ⃗e = (e1 , . . . , eN ) of mean at most 1/2,
the following must hold for all t and all p ∈ [0, 1]:

    Pr [P-VAL_t (e_π1 , . . . , e_πt ) ≤ p] ≤ p

where π is sampled from the distribution of S .

Definition 4. A p-value calculator P- VAL is **sequentially valid** 
for S if for all  ⃗e = (e1 , . . . , eN ) of mean at most 1/2,
the following must hold for all p ∈ [0, 1]:

    Pr [inf_t P-VAL_t (e_π1 , . . . , e_πt ) ≤ p] ≤ p

We also assume that P-VAL is monotone. Technically, 
the prior conditions do not imply monotonicity, but some of whether the 
the p-value calculators used in existing RLAs are monotone, 
such as Wald’s SPRT [26]. Monotonicity makes it easy definition.
to reason about how changes to P- VAL’s inputs affect its
output. Our security proof (Sec. 5.1) relies on this.

Definition 5. A p-value calculator P-VAL is **monotone** if
for all u, N, t and all e1 ≤ e′1 , . . . , et ≤ e′t , then
P- VALt (e1 , . . . , et ) ≥ P-VALt (e′1 , . . . , e′t ).  (TODO)

The protocol (Algorithm 2)

1. P commits to a unique ID for each ballot as the distinguished (oracular) ID commitment id.
2. P sends CVRs  ⃗c for all ballots.
3. P sends an assertion list A⃗ that implies the outcome O.
4. V checks that the assertions do imply the outcome. (TODO)

5. V uniformly samples a small PRG seed
6. V expands it into a long bitstring s_twiddle
7. V derives a sequence of ballot samples π (line 7); s is sent to P .

8. The p-values pj (where j indexes assertions) are initially set to 1
9. For each ballot t in [1..T] 
10. V requests πt  (TODO what is πt)
11. P sends πt as (m't, id't)
12-14. If a ballot’s ID id′t doesn’t match the ID commitment id_πt, it is treated as a BAD ballot.
15-17. For each assorter Aj (line 15), the assorter value is computed and stored in ej. Then, the p-value pj is updated.

Sharing a PRG seed with an
adversary is highly unusual. It prevents us from proving
the security of Algorithm 2 through a reduction to PRG
security in the standard model.

4.5. Instantiations
AO: TODO
Majority. AO: TODO
Plurality. AO: TODO
More examples. AO: TODO

5.1. Soundness

incorrect outcomes should be accepted with small probability.

Theorem 7. If the p-value calculator P- VAL is sequentially
valid (Definition 4) and monotone (Definition 5), then for
all α ∈ [0, 1], Algorithm 2 is sound when PRG is modelled
as a random oracle.

5.2. Completeness

Completeness means that if the statement is true and the prover follows the
protocol honestly, the verifier accepts with high probability.

Since we are in the completeness case, the prover P is assumed to be an honest prover.

We have the following assumptions we could make:

• UBTight: Is the upper bound Ncard tight or not?   (TODO what does tight mean? Probably nphantoms=0)

• UBSufficient: Is the upper bound Ncard sufficiently
low such that the number of potentially missing ballot
cards could not cast the result of a hand count of the
accessible ballot cards into doubt?

• NoLimit: Does the prover return a sequence of ID-
tagged ballot cards that is equal to the full set x,
covering all physically accessible ballot cards?

• HandCountIsProtocol: If the NoLimit is false, i.e.,
the sample size is smaller than |x|, and the verifier is
not convinced by what they have seen, then then will
call for a full hand count. Now, is this a failure or still
part of the cryptographic protocol? If it is a failure,
it will contribute to a lower completeness percentage,
else it will only contribute to a lower completeness
percantage if the full hand count does not imply the
reported result (including no result can be determined).

I would argue that UBTight or UBSufficient would need
to hold, otherwise “correct outcome” is ill-defined.

I think we should present our main
protocol (and our main analysis) in a simplified model where
every card contains the contest and is marked, and the
number of cards is fixed. (e.g., Ncard = Ncontest = N ). And,
I think we should assume T < N samples.
Then, in Section 6, we can discuss how to extend our
completeness results if we have something nice to say.
Here’s a theorem that I hope is well-defined and true:

Theorem 10 (Completeness). Fix an outcome O and ballots
⃗x ∈ V^N such that O = TALLY(⃗x). Fix a margin β ∈ R,
assertions A⃗ that imply the outcome, with assorters  ⃗a, such
that for all aj ∈  ⃗a, N1 i aj (xi , xi ) > 1/2 + β . Then, if P
follows Algorithm 2 using the assertions A, then V rejects
with probability at most TODO.

Maybe we could have a different margin for each assorter without much more work?
Presumable some function of N , β , |A|, T, and ?

6.2. Contest not present

AO: TODO: explain how to generalize to a setting where a card may not contain the contest at all

6.3. Style-based sampling

AO: TODO(related): explain how to generalize to a setting where there is an upper bound on the number of
cards that contain the contest. This builds on the previous section.
AO: Below, I’ve put a few things from our original writeup that might be relevant.

Appendx A Assorter constructions = Appendix in 929

Appendix B.
Monotonicity of p-value calculators



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
A _ballot_ is the physical evidence of one voter’s choices. A ballot may be split into multiple _cards_, each of which 
may have several _contests_. A cast vote record (CVR) is an electronic record of all the choices on one card. 

A CVR or card has three possibilities:

1. a vote for the contest 
2. a null vote, which means the contest was present, but the voter didn’t fill in any choices 
3. NOTPRESENT, which means the contest was not present on the card

The instance x is a multiset with elements being (card, ID) pairs.
(a multiset (or bag, or mset) is a modification of the concept of a set that, unlike a set, allows for multiple instances for each of its elements)
(LOOK why is it a multiset? because ID might be duplicated? Can the card be duplicated?)


## 3. Definitions

* _U_ is an upper bound on the total number of cards that contain the contest.
* _N_ is an upper bound on the total number of cards.
* _V_ is the set of all valid votes.
* _PROBLEM_ is the set of problem card values (including absent, disputed, etc).
* A _Manual Vote Record_ (MVR) is the version of a cast vote record uploaded when the card is sampled for audit.

**Definition 1**. An assertion A is a predicate on the set of votes, including PROBLEM ones.

    A(V) = true | false

Each assertion has a corresponding assorter, a:

**Definition 3**. An _assorter_ translates data from audited votes (and optionally CVRs) into an output
that is appropriate for a P-value calculation. It is a function

     a : STYLE(i) × [0, u_a] × (V(i) ∪ PROBLEM) → [0, u_a].

    where
        STYLE(i) = [0, 1] indicates if this contest is present on card i
        [0, u_a] is the reference value
        u_a is the _upper bound_ of a
        V(i) are the valid votes for this contest on card i, as seen on an MVR.

### 3.3.1. The prover needs to commit to which ID will be retrieved when a given value in [1, N] is sampled.

**Definition 4**. The prover’s ID commitment is a function:

    FIND : [1, N] → (I ∪ ⊥).

where 
    I is the set of all valid IDs
    ⊥ indicates no ballot will be retrieved.


### 3.4 Ballot Styles

**Definition 5**. The prover’s style commitment is a function

  STYLE : [1, N] → [0, 1].

A value x with 0 < x < 1 means that the card is being treated as part of a collection of cards, of which x fraction 
contains the contest. LOOK MAYBE WRONG?


### 3.5 Assorter Reference values

**Definition 6**. The prover’s assorter reference values are provided, for each index, as a list of reference values, one
for each assertion in ASSERTIONS. ASSORTER-REF inputs a value i in [1, N] and outputs a list (r1, r2, . . . , r|ASSORTERS|).

These reference values are ignored for polling audits. -> These reference values only exist for comparison audits.

It would be (mostly) equivalent to let the prover commit to a CVR, and then let the verifier apply the assorter. 
LOOK Doesnt V need to have the CVRs , not just the reference values as supplied by P?


### 3.6 Connecting Assertions and Assorters

For a valid audit, an assorter mean greater than 1/2 must imply that the assertion is true:

**Definition 7**. Assorter aj _corresponds_ to assertion Aj when, for all possible votes V : [1, N] → (V(i) ∪ PROBLEM),

    Avg( aj(STYLE(i), r(i)_j, V(i)) > 1/2 ⇒ Aj(V) = true

    where
        STYLE(i) is from Definition 5
        r(i)_j is from Definition 6
        V(i) are the valid votes for this contest on card i


**Definition 8**. Define the ballot polling assorter aPOLL as follows.
  aPOLL(i)= 0 if FIND(i) = (⊥, ⊥), i.e. there is no ballotID corresponding to i,
            0 if FIND(i) = (id, ⊥) and either no card was returned or a card with a different ID was returned,
            a(MVR_id) if FIND(i) = (id, ⊥) and card containing MVR_id was returned.


**Definition 10**. Define the ballot-level comparison assorter aCOMP as follows:
aCOMP(i) = 1 / ( 2u − ν) × 1/2 − aPOLL(i)      if FIND(i) = (x, ⊥) (including x = ⊥), i.e. there is no CVR corresponding to i,
                           a(CVR_id) − aPOLL(i) if FIND(i) = (x, CVRid) (including x = ⊥),

where a(CVRid) is the value of the assorter a applied to the cast vote record, u is the upper bound on a(·) and ν is the
assorter margin, 2 * a(CVR) − 1.

PollAssorter
````
    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // valid vote for every loser
        val w = mvr.hasMarkFor(info.id, winner)
        val l = mvr.hasMarkFor(info.id, loser)
        return (w - l + 1) * 0.5
    }
````

ClcaAssorter
````
    cvrAssortMargin = assorter.reportedMargin()
    noerror = 1.0 / (2.0 - cvrAssortMargin / assorter.upperBound())
    upperBound = 2.0 * noerror

    open fun bassort(mvr: Cvr, cvr:Cvr): Double {
        val overstatement = overstatementError(mvr, cvr, this.hasStyle)     // ωi eq (1)
        val tau = (1.0 - overstatement / this.assorter.upperBound())        // τi eq (6)
        return tau * noerror   // Bi eq (7)
    }
    
    fun overstatementError(mvr: Cvr, cvr: Cvr, hasStyle: Boolean): Double {
        if (hasStyle and !cvr.hasContest(info.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})")
        }

        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr, usePhantoms = false)
        
        return cvr_assort - mvr_assort
    }
````

## 4.3 The Instance

Before the protocol begins, the prover fixes an instance, which represents the claim it will prove. The instance is:

* a multiset x of ID-tagged cards,
* a reported outcome O ∈ the set of possible outcomes,
* a (trusted) upper bound Ncard on the number of cards,
* (optionally) a decision to use style-based sampling and a (trusted) upper bound Ncontest on the number of cards containing the contest.

The prover will attempt to show that the social choice function TALLY yields the reported outcome O, when applied to the cards x.

## 4.4 The Prover's First Message

In the first step of the protocol, the prover P commits to a collection of claims about x.

* An ID commitment FIND (see Definition 6).
* (Optionally) a CVR commitment (see Definition 7).
* A list ASSERTIONS of assertions with corresponding assorters ASSORTERS (see Definition 4, Definition 8 and Definition 9).
* (Optionally) other data AUX - P , such as tallies or subtotals, which the p-value calculator may input.

## 4.5 The Verifier's challenge

The verifier V samples and sends a seed s.

## 4.6 The Provers Response

The prover P computes uses PRG(s) and π ← ḠSAMPLE,N (se). 

For each index i = 1.. P retrieves MVR(i) with identifier FIND (π_i) ("but is free to do otherwise" ??)

## 4.7 The Verifiers Decision

### 4.7.1. Validating the prover’s first message

1) Each assorter satisfies its upper bound.
2) ASSERTIONS implies O given TALLY (see Definition 5)
3) |ASSERTIONS| = |ASSORTERS|.
4) for all i ∈ 1..|ASSORTERS|, ASSORTERSi expresses ASSERTIONS i (see Definition 9 and 10). This effectively says that 
   if we are using style-based sampling and there are CVRs that claim to have the contest but are outside the image of
   FIND then we ignore them.
5) FIND assigns some i ∈ I to every index in 1..N and does not repeat any ballot ID.

### 4.7.2. Validating the prover’s response to s

Algorithm 1. This is what we call running the audit.

On Line 7, the verifier V identifies the identifier of the card that must be audited. 
On Line 8, V reads that card, or sets it to ⊥ if no card has been returned. 
On Line 9, V checks whether the card returned by the prover has the expected ID. If so, V sets MVR(t) to the
canonical interpretation of that card, and to BAD otherwise.

V then goes through all the assorters 
on Line 15, it extends the vector a_i with the value that assorter ai takes for this audited card. 
on line 16 it  updates pi with the p-value computed for this assorter

on line 19 it checks if all p-values are less than the risk limit
on line 24 it accepts O if all p-values are less than the risk limit

So we need to check that the samples are sorted by PRN.
So we need to check that the samples PRN are te smallest available based for that contest.
So we need to check that the MVR id matches the CVR id.

