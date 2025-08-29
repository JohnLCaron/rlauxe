# RLASpec notes

## 1. Definitions and Background

We begin with the notation we use in this paper:

* \[a, b] is the set of real numbers in the range from a to b inclusive.
* a..b is the set of integers in the range from a to b inclusive.
* y ← z means that variable y is assigned value z.
* y ←rnd Z means that y is an element of the set Z selected uniformly at random from Z.
* U is an upper bound on the total number of cards that contain the contest.
* N is an upper bound on the total number of cards in the election.
* V is the set of all possible valid votes, which always includes the null vote, meaning that the card does not contain a valid choice in the contest.
* *bad* is the set of problematic card values (including notPresent, disputed, etc).
* I is the set of IDs, of size at least N + 1, and includes the special symbol ⊥, which no retrieved ID can match.

### 1.1 Ballots, Cards, CVRs and Valid Interpretations

A ballot is the physical evidence of one voter’s choices. A ballot may comprise multiple cards, each of which may have several contests.

Notation 1. A Cast Vote Record (CVR) is an electronic record purporting to
be the votes on one card. Not all voting systems produce CVRs. CVRs are not
necessarily accurate representations of the votes on the card.

Notation 2. A Manual Vote Record (MVR) is the set choices on a card according to an accurate manual inspection of the card.

For the contest under audit, a CVR or MVR either
1. contains a (possibly invalid) vote in the contest (an element of V ∪ *bad*), which may be a null vote, or
2. does not contain the contest, which is represented by notPresent

### 1.2 Social Choice Functions and Election Outcomes

A social choice function maps sets of votes to an election outcome. 

Definition 3. Let O be a set of possible election outcomes and V be the set
of valid votes. A social choice function tally maps a multiset of elements of
V ∪ bad to an element of O.

### 1.3 Assertions and Assorters

Definition 4. An assertion is a predicate on a multi-set of votes, including bad ones.

Definition 5. An assorter is a mapping  

        a : {0, 1} × (V ∪ bad) × (V ∪ bad) → [0, ua].

        where a(i, c, m) is the assorter value of MVR m with claimed ballot style information i and CVR c

### 1.4   Connecting Assertions and Election Outcomes

Definition 6. An assertion validation function for a social choice function
tally inputs

        • an outcome O ∈ O and
        • a set of assertions A,

    and outputs
        true, if O is the unique outcome implied by A, given tally
        false, otherwise.

### 1.5 comments

% We do not need manifests per se (the Prover might need them to help retrieve sampled ballots, but the verifier doesn't).
%The Prover is responsible to assign an identifier to every voting card.
%As discussed above, we do assume that whatever technology used to assign the identifiers to the cards is "indelible"" for the purpose of the audit.
%This might comprise printing the ID on the card in ink, or specifying the location of the card in a particular physical batch of cards---provided that location cannot be altered during the audit without the verifier's knowledge. Similarly, after the identifiers have been assigned to voting cards, we assume that no voting card can be added without the verifier's knowledge.

%\todo{PBS:
%    I've been thinking about this inside out:
%    the identifiers really are primary; a manifest is a way to **generate} those IDs, and helps with the retrieval.
%    We are really sampling identifiers; the manifest implicitly generates
%    an ID list, even if the cards are not imprinted.
%    A card's implicit ID is the ID of the batch in the manifest it is in, combined with a way to identify which card in that batch it is.
%    That could be "the 13th card in batch A001."
%    If there's no way for the card order to change (or have cards substituted or added) during the audit, that amounts to a unique, immutable identifier.
%    If the public has a way to check whether the card the Prover retrieved when that card was requested was indeed the 13th card in batch A001
%      this will still permit a publicly verifiable audit.
%    However, if there is not a way for the public to confirm that the `correct' ballot is retrieved, can't guarantee
%    the risk limit for sampling methods that rely on selecting from the set.
%}

### 1.5 Committing to Information About Cards

The Prover makes several commitments to untrusted data about the cards under audit. First, Prover must commit to which ID will be retrieved when a given value in 1..N is sampled.

Definition 7.  The Prover's **ID commitment** is a function

    Find : 1..N → I   (I is an ID or ⊥)

    If Find(i) == ⊥ the Prover does not claim to be able to retrieve i.

The Prover is free to define the ID commitment in any way it likes and, in particular, to lie about the identifiers that have been actually assigned to the voting cards.
We make no assumption regarding the faithfulness of that commitment.
In particular, it is fine if:

could be 

The prover is not trusted and may lie about the identifiers that are assigned to
the voting cards. In particular, we need to detect if:

* there are more physical cards than indicated IDs,
* some cards were not assigned IDs in $\ballotIDSet{}$,
* some IDs the Prover claimed to use were not in fact assigned to any physical card, or
* more than one card was assigned the same ID.

Verifying the basic consistency properties of find (e.g., that it is injective except for values mapping to $\bot$) is part of the verification algorithm---see Section 22.6 for details.

In card-level comparison audits, the Prover makes an ID commitment and a **CVR commitment** in which it commits to reference vote for some or all IDs in the ID commitment.
(The exposition here can be generalized from commitments about CVRs to commitments about reference values of assorters, which makes it possible to use the ONEAudit approach to leverage batch-level information.)

Definition 8. The Prover's **CVR commitment** is a function

    CVR : 1..N → (V ∪ *bad*)

Definitions 7 and 8 together model the Prover's initial declaration about the cards.
The Prover is declaring that if the sampling function chooses $i$, the Prover will retrieve a card with ID FIND(i), and that applying TALLY to the set of CVRs in the commitment will produce the same outcome as applying TALLY to $x$.
Of course, these two tables need to be checked for consistency with each other and with the rest of the election data.
In particular, the verifier should check that applying TALLY to the CVRs yields the reported outcome.
See Section 2.6 for details.

In ballot-polling audits, the Prover may make untrusted claims about whether the contest is on the cards---these are optionally used for auditing.
This is called "card-style" information, and is indicated using a bit value.
A one indicates that the contest is on the card; zero indicates that it is not.

Definition 9 The Prover's **style commitment** is a function

    style : 1..N → {0, 1}

1.6 Connecting Assertions and Assorters

Definition 10. Assorter a expresses assertion A under specific style and CVR
commitments style and CVR when, for all possible sequences of vote_seq := (MVR(i)) i=1..N  drawn from (V ∪ bad),

        1/N * Sigma( a(style(i), CVR(i), MVR(i)) ) > 1/2  implies A {vote_seq} is true  (eq 1)

1.6.1 Assorters for style-based sampling

A different way of dealing with style information is to consider only those
cards that the prover claims to have the contest, but to take a worst-case assorter
value if the card does not in fact contain the contest. This is appropriate for
sampling strategies that depend on which ballots the prover claims to contain
the contest. It is important to understand that these strategies do not sample
only from those ballots, since the prover’s claims about style are untrusted, but
rather from U ballots (U is the trusted upper bound on the total number of
cards that contain the contest).

Definition 11. Assorter a "expresses-with-style" assertion A under specific style and CVR commitments style and CVR
when, for all possible sequences of vote_seq that respect the contest upper bound U, for all index sets I of size U that contain all
the indices i s.t. style(i) = 1,

        1/U * Sigma( a(style(i), CVR(i), MVR(i)) ) > 1/2  implies A {vote_seq} is true  (eq 2)

==========================================
Appendix A Assorter Constructions

(eq 1) Assorter a expressed A when 
    
    1/N Sum[ a(MVR) ] > 1/2 -> A( mvr(i) ) , i = 1..N

(eq 7) As is a scoring function for assertion A when

    1/N Sum[ s(V) ] > 1/2 -> A( V(i) ) , i = 1..N
    V are the set of valid votes

* **nothing** The assorter value that corresponds to having not counted this value at all is 1/2. 
This is appropriate for missing CVRs, which were not included in the reported tally.

* **worst** The assorter value least favourable to the assertion’s reported winner is 0. 
This is appropriate for cards that have been lost, cards that have not been produced when required, or cards with the wrong id.

_nothing has an effect on the average assorter value._

_Definition 20: it is possible to do style vs noStyle for polling._

_Ignoring OneAudit, there are 4 variants: (poll, clca) x (style, noStyle)._

Definition 22. We will extend the scoring function by simply scoring all bad
votes as zero. Cards that do not contain the contest are scored as 0.5. (This
is appropriate for non-style based sampling, but will be overruled for style-based
sampling—see Definition 25.) Define the scoring extension es by:

* es(v) = s(v) if v ∈ V
* es(v) = 0.5 if v = notPresent
* es(v) = 0 if v ∈ bad (not including notPresent)

Definition 23. Define the no-style phantoms-to-zombies comparison assorter
as:

    a(i) = u_s - (s(cvr(i) - s(mvr(i)))) / (2u_s - v_style)   eq (8)
    v_style = 2/N Sum[(s(cvr(i)] - 1

Definition 25. Let I be an index set of size U that contains all the indices i, s.t. style(i) = 1.

Define the stylish phantoms-to-zombies comparison assorter as:

    a(i) = u_s - (s(cvr(i) - s(mvr(i)))) / (2u_s - v_stylish)  if m contains the contest
    a(i) = u_s - s(cvr(i) / (2u_s - v_stylish)                 if m NOT PRESENT
    where v_stylish = 2/U Sum[(s(cvr(i)] - 1, i∈I
          U is the trusted upper bound 

Could add that I has been augmented by phantoms if needed so that |I| = U.

Proposition 27.

    1/2 < 1/U Sum[ u_s - (s(cvr(i) - s(mvr(i)))) / (2u_s - v) ], i i∈I, |I| = U
    1/2 < 1/(2u_s - v) * 1/U Sum[ u_s - (s(cvr(i) - s(mvr(i))))] , because (2u_s - v) is constant
    (2u_s - v)/2 < 1/U Sum[u_s] - 1/U Sum[(s(cvr(i)] + 1/U Sum[s(mvr(i))))] , because (2u_s - v) > 0
    u_s - v/2 < u_s - Ā(cvr) + Ā(mvr)
    v/2 < - Ā(cvr) + Ā(mvr)
    
    -((2 * Ā(cvr) - 1))/2 < -Ā(cvr) + Ā(mvr) , because v = (2 * Ā(cvr) - 1)
    -Ā(cvr) + 1/2 < -Ā(cvr) + Ā(mvr)
    1/2 < Ā(mvr)

=====================================================================================================

Does Ā = (winner - loser) / N  ?

    Not when you include cvrs that do not contain the contest, where a = 1/2
    
Consider the effect of adding 1/2 to Ā

    Ā = Sum(ai) / N
    Ā' = (Sum(ai) + 1/2) / (N + 1)
    Ā' = [(Sum(ai) + 1/2) / N ] * N / (N + 1)
    Ā' = [Sum(ai)/N + 1/2N]     * N / (N + 1)
    Ā' = [Ā + 1/2N) * N / (N + 1)
    Ā' = Ā * N/(N + 1) + 1/(2*(N + 1)))
    Ā' < Ā because N/(N + 1) < 1 
show
    Ā * N/(N + 1) + 1/(2*(N + 1)))


What happens when you have lots of ballots where the contest is not on the ballot? Is 1/2 really "nothing" ?
I think Ā gets closer to 1/2, but all the proofs stay true.
Perhaps show that one can add any number of 1/2s, and the proofs remain valid??

I think when noStyle, Nc = N.

## MoreStyle

"With CSD, there are two relevant “diluted margins”. **_The partially diluted margin is the margin in votes
divided by the number of cards that contain the contest_**, including cards with undervotes
or no valid vote in the contest. **_The fully diluted margin is the margin in votes divided by the number of cards in
the population of cards from which the audit sample is drawn_**. When the sample is drawn
only from cards that contain the contest, the partially diluted margin and the fully diluted
margin are equal; otherwise, the fully diluted margin is smaller. 

If CSD are unavailable, the number of cards in that population is the number of cards cast in the jurisdiction. 
If CSD are available, the number of cards in the population can be reduced to the number of cards that
contain the contest. The availability of CSD drives the sample size through the difference
between the partially and fully diluted margins.

Absent CSD, the sample for auditing contest S would be drawn from the entire population
of N ballots."

((So define Nc as the denominator of the margin))
((I think when noStyle, Nc = N.))

Nc is the trusted upper boumd.
Np is the number of cards that contain the contest.
Nf is number of cards in the population of cards from which the audit sample is drawn.

If hasStyle, the number of cards in the population can be reduced to the number of cards that contain the contest; Nf = Np.
With phantoms we can make Np = Nc.

