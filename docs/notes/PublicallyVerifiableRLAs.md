# Publicly Verifiable RLAs
5/23/2020

## 1. Introduction

* The electoral authority takes the role of _prover_.
* The public takes the role of _verifier_.
* The _instance_ x is a trusted physical collection of paper ballots.
* The _Cast Vote Record_ (CVR) is an electronic records purporting to be the voters’ selections on individual ballot cards.
* The _reported outcome_ is the outcome claimed by the prover. 
* The _correct outcome_ is the true winner(s) according to the expressed preference on every cast ballot card(s) in the instance.
* The _risk-limiting property_ is an upper bound on the probability that the verifier accepts an apparent outcome that
is actually wrong.

## 2. Interactive Proofs

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

