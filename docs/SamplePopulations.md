# Sample Populations
_04/11/26_

## TL;DR

The most efficient audit has CVRs (that include undervotes) for all ballots.

Otherwide we need to indicate, for each card in the card manifest, what contests might be on the card. 

For each contest, the set of cards that might contain it is the sample population for the contest. 

One way to do so is to assign to each card a "Style" that simply lists the possible contests. Another way is to divide
the cards into disjoint "Batches", where each Batch knows what contests may be on the contained cards. If the batch also has
a subtotal of the votes on the contained cards, we call that a "Card Pool" or just "Pool". A Pool is a Style so we can
use Style to refer to both.

A Style has a field "hasExactContests" which is true when all cards have exactly the contests in possibleContests(). 
The best case is whan all Styles have this field as true, then the sampled populations are as small as possible.
This field may be set independently on each Style, and replaces SHANGRLA's global _hasStyle_ (aka _use_style_) flag. 

The hasExactContests field is used when deciding the assort value when an MVR is missing a contest, for all audits (TODO including Polling?).

The use of populations is implicit in the "More styles, less work" paper. Setting hasStyle by population and using hasStyle in Polling audits is new. 
These complexities arise in multi-contest audits and multi-card ballots.

**Table of Contents**
<!-- TOC -->
* [Sample Populations](#sample-populations)
  * [TL;DR](#tldr)
  * [Definitions](#definitions)
  * [Populations and Styles and Batches and Pools](#populations-and-styles-and-batches-and-pools)
  * [Examples](#examples)
    * [multi-card ballots, Polling audit (MoreStyle section 5)](#multi-card-ballots-polling-audit-morestyle-section-5)
    * [CLCA without undervotes](#clca-without-undervotes)
    * [OneAudit](#oneaudit)
  * [Polling Modes](#polling-modes)
    * [Example Corla 2024](#example-corla-2024)
  * [The role of hasExactContests aka hasStyle.](#the-role-of-hasexactcontests-aka-hasstyle)
    * [Contest is missing in the MVR](#contest-is-missing-in-the-mvr)
    * [Contest is missing in the MVR for Polling](#contest-is-missing-in-the-mvr-for-polling)
  * [Ncast and Nphantom](#ncast-and-nphantom)
  * [Claims](#claims)
<!-- TOC -->

## Definitions

* **physical card** = pcard: the physical ballot or physical card if the ballot has multiple cards and the cards are scanned and stored separately.
* **CVR**: scanned electronic record of a physical card
* **MVR**: human audited physical card
* **auditable card** = **card**:  internal computer representation of a physical card; contains the CVR if there is one. 
  A card must have a _location_ which allows a human auditor to locate the physical card, and either a CVR or a _style_.

* **Batches**: a complete partition of the cards.
* **Batch**: one of the partitions of the cards with a list of contests that are in the batch.
* **Batch.possibleContests**: list of contests that are on any of the cards in the batch.
* **Batch.hasExactContests**: is true if all cards in the batch have the same Style, so that "we know exactly what contests are on each card".
* **Style**: the list of possible contests on a card.
* **Pool**: a batch that also has a subtotal of the votes and a count of the number of cards in the batch,
  and a container of physical cards, from which we can retreive named cards (eg by an index into an ordered list of pcards).

* **Contest population** = P_c: For each contest, the set of cards that may contain the contest. This is the sample population for the contest.
* **Contest population size** = \|P_c\| = Npop: The size of the contest's population. Npop >= Nc.
* **Contest upper limit**: For each contest, there is a trusted upper limit Nc of the number of cards containing the contest.
* **phantom card** = auditable card added to ensure number of cards = Nc. Phantom cards arent in a batch; they contain their own list of contests.
* **card manifest** = complete list of auditable cards, one for each physical card and phantom card.

* **Reported margin**: Each assorter has a reported margin with Nc as denominator; For Plurality it is (nwinners - nlosers) / Nc.
  Other assorters have numerators possibly different.
* **Diluted margin**:  Each assorter has a diluted margin with Npop as denominator; for Plurality it is (nwinners - nlosers) / Npop.
  Other assorters have numerators possibly different.

## Populations and Styles and Batches and Pools

Each contest has a known **Population** P_c of cards that might contain it. |P_c| = Npop is used for the diluted margin.
When auditing, we sample consistently over P_c.

Each AuditableCard has a **Style**, which has the possibleContests that might be on that card. Read through the manifest and count the cards that might contain a contest: that equals contest.Npop.

A **Batch** emphasizes the partitioning of cards into physical containers. 

A **Pool** is a batch with vote subtotals and
a physical container distinct from other pools. We dont currently need Batches that arent Pools, so we will just use Pool instead of Batch here.

A Pool is disjoint, a Style can be shared by many cards without implying the cards are stored in seperate containers. 

When a card is in a Pool, the card.poolId is set. To find out what cards are in a pool, read through the card manifest.

We allow the possibility that the card style may be different from the card.poolId (although our use cases for that have evaporated).

## Examples

In the best case, we are running a CLCA audit where the CVRs record the undervotes. Then, the CVRs record the exact contests on the card, and Npop = Nc.

In the next best case, we have some cards with CVRs and some without. The non-CVR cards are in CardPools with known vote subtotals. Then we can run
a OneAudit. Otherwise, we can run a Polling audit. In both these case we create Batches to describe what we know about which cards have
which contests. We might still know the exact list of contests on all the cards (Npop = Nc), or only the approximate list of contests (Npop > Nc).
For example, we know exactly which contests are on each card:

1. When theres only one contest.
2. When the cards are divided into batches that have only one Style.
3. When each ballot has a known Style.

In the real world, we have partitions: precincts, counties, possible other districts. These have distinct lists of contests. Consider the case where the physical ballots can be matched to their style by knowing their partition. Perhaps given the ballot id, we can look up the partition and card style.

For the same audit and contest, you could have different batches with different values of hasExactContests. For example, one precinct has a
single Style containing contest c, and another has multiple Styles, not all of which contain contest c.

Its worth noting that the EA (election authority) knows the card style for every voter and ballot. 
So we are dealing with the limitations of associating CardStyles with the anonymous physical ballots and scanned cvrs.

### multi-card ballots, Polling audit (MoreStyle section 5)

"This is an idealization of precinct-based voting where each voter in a precinct gets the same ballot style and casts
all c cards of the ballot. If voters in that precinct are eligible to vote in contest S, a fraction 1/c of
the cards in the container will have contest S; otherwise, none of the cards in the container will have S."

Suppose a ballot has c cards to it. Suppose all ballots of the same style are kept in the same container, but the cards are
scanned and separately addressable. If there are c cards, we know that only 1/c have the contest.

The batch.possibleContests = all contests on the ballot (aka ballot style), and batch.hasExactContests = false, because there are c distinct CardStyles.

### CLCA without undervotes

We have CVRs but dont record the undervotes. cvrsContainUndervotes = false. 
Then we need to use populations to specify where the contests are.

### OneAudit

OneAudit handles two somewhat distinct use cases:

1. There are cvrs for all cards, but the precinct cvrs cant be matched to the physical ballot. We know exactly how many cards a contest has in the pool,
and the pool's vote count. This is the SanFrancisco 2024 test case.

2. We have cvrs from some cards, but not all. The remaining cards are in pools with pooled vote counts. 
Undervotes need to be included in the vote count. This is the Boulder 2024 test case. (Boulder 2024 does not
record the undervotes for the redacted pools, so we guess what they are for the simulation.)

In San Francisco, the pools do not have one Style, so population.hasExactContests = false.
In Boulder, each pool appears to have one Style, so population.hasExactContests = true.

## Polling Modes

With pools, we have a tabulation of the ballots in the pool. With those tabulations we
can run estimations by generating cvrs for the pooled cards that match the pool subtotals.

We also can run Polling audits with pools (mode = withPools). **TODO: characterize OneAudit vs Polling using pools.**

Suppose you don't have cvrs or pools, so you're left with a Polling audit. If you just have one undifferentiated stack
of N ballots, then you have to use N = Npop for all contests. (mode = withoutBatches)

You may have distinct batches, where you know which contests are in each batch, that allows Npop to be smaller, perhaps much smaller. 
To run simulations one can use the card's batch to restrict the simulated Mvr to the possible contests for that card. (mode = withBatches)

**TODO: characterize using Batch vs Pools for Polling audits.**

Note that a Batch is a Style when hasExactContests = true. So this covers the case where you know the Style for each card.
This allows Npop to be as small as possible.

### Example Corla 2024

All ballots are in precints with subtotals, and we simulate cvrs that match the precinct subtotals; then make both cards and Mvrs from these simulated cvrs.

1. CLCA: add cvrs to all cards and run a CLCA audit.
2. OneAudit: add cvrs to some percentage of cards, the remaining cards use OneAudit pooled averages.
3. Polling with pools: none of the cards have cvrs, all reference CardPools with subtotals. Estimation matches the
   pool subtotals.
4. Polling with batches: none of the cards have cvrs, all reference Batches without subtotals.
   Estimation restricts to batch.possibleContests and uses common Vunder across all batches.
5. Polling without batches. Npop = N for all contests.

Note that 3 and 4 differ only in estimation, and one would expect 3 to be somewhat more accurate, ie have a smaller variance.

## The role of hasExactContests aka hasStyle.

1. Vunder is how we simulate the votes for one contest; VunderPool is how we simulate cvrs in a Pool; 
VunderBatches is how we simulate cvrs from Batches:
````
   data class Vunder(val contestId: Int, val poolId: Int?, val voteCounts: List<Pair<IntArray, Int>>, val undervotes: Int, val missing: Int, val voteForN: Int)
      val nvotes = voteCounts.sumOf { it.second }
      val ncards = missing + (undervotes + nvotes) / voteForN
      
   class VunderPool(vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int, val hasExactContests: Boolean)
   class VunderBatches(batches: List<BatchIF>, val onePool: VunderPool)
````

if hasExactContests is false, then Vunder chooses both missing (contest not on the cvr) and undervotes (contest on the cvr but not voted).
if hasExactContests is true, then missing = 0, and undervotes = ncards * voteForN - voteSum. Note that ncards = Npop for the contest.

In the actual mvrs, we expect to see no missing contests when hasExactContests=true.

2. In the CLCA assorter, missing contests are penalized if card.hasStyle() = batch.hasExactContests:

            val nextVal = cassorter.bassort(mvr, card, hasStyle=card.hasStyle())
            val mvr_assort = if (!mvr.hasContest(info.id)) { if (hasStyle) 0.0 else 0.5 } ...

These zeroes can tank the audit. But it seems theres no obvious incentive to declare card.hasStyle() = true (if you want the audit to succeed).
The Batches of the audit are something that could be accidentally or deliberately wrong. They are part of the pre-audit
committment. 

3. You have a pile of physical ballots in a batch. You think that these are all the ballots in a precinct. You think you
   know what contests are in the precinct. Suppose you get the batches mixed up?

    1. Suppose hasExactContests=false. You start seeing missing contests, but these are expected and "harmless" since hasStyles = false.
       You start seeing contests that are not expected to be in that batch. Right now, the software ignores them. A
       warning message should be given so that the humans can fix the problem and restart the audit?
    2. Suppose hasExactContests=true. These are unexpected and tank the audit. A warning message should be given so that the
       humans can fix the problem and restart the audit.

This argues for keeping track of statistics associated with a batch, to tell if a batch is bad.
(Note that each card has a reference to the batch that its from). Perhaps this should all be done before the audit is run,
raher than during the audit. Added this functionality into VerifyMvrs.

### Contest is missing in the MVR

SHANGRLA sets hasStyles for entire audit. In that case, you get the benefit of reduced dilution vs the cost of mvr_assort = 0.

Using Batches gets you the reduced dilution. This happens when calculating Npop, and also in ConsistentSampling, both relying
on card.hasContest(contest.id). But the Npop calculation does not depend on hasExactContests.

Setting hasExactContests=true makes you pay the cost of mvr_assort = 0. It wouldnt be hard to set this to false;
hasExactContests also effects the simulation, allowing missing (false) or not (true) contests in the simulated Mvr. 
But missing and undervote have the same effect when hasStyle = false.

When the contest is missing on the MVR, we assign 0 to mvr_assort when hasStyle=true, and 0.5 when hasStyle=false.

The first case tanks the audit, and the second may allow attacks.

Note that batch.hasExactContests requires all cards to have the same Style in a batch.
So it is false even if theres only one card thats different.
That could have a big effect on the assort values, but it only increases the diluted count by 1. 

**TODO: An attacker could falsely claim hasExactContests=false; could we detect that? Could they use it as an attack?**

### Contest is missing in the MVR for Polling

Suppose each precinct has one Style, and each precinct stores its own ballots, and we are doing a Polling audit.
You hand count the ballots, and keep all cards for a ballot in the same ballot envelope.
Then it doesnt matter how many cards are in the ballot. Since we know the exact contests on all cards, we
have hasExactContests = true. The contest audit can sample only from the batches that contain the contest, and Npop = Nc.

Suppose you audit a ballot that turns out not to have that contest. Seems like mvr_assort should be 0, not a 0.5, when hasExactContests = true.
`if (!cvr.hasContest(info.id)) return if (hasStyle) 0.0 else 0.5`
is in the code for ClcaAssorter, but not for the primitive assorters,which have
`if (!cvr.hasContest(info.id)) return 0.5`

If in the same scenario, but with the cards separated (the example of MoreStyle section 5):
Each ballot puts n seperate cards in the pile, and hasExactContests = false.
We expect to see (n-1)/n cards without the contest, and 1/n with the contest, so we cant tolerate setting
mvr_assort = 0 when mvr doesnt have the contest, since that will happen a lot. We need to set it to 0.5.

**TODO: we need to set mvr_assort based on hasExactContests, just as with CLCA and OneAudit. True?**

## Ncast and Nphantom

When hasStyles = true, we can count the cards and see how many each contest has. Use that as Ncast, then add phantoms as needed.

When hasStyles = false, where do we get Ncast? If the batches are pools with vote totals that include undervotes, we can get Ncast from that.
**Otherwise we have to assume that both Nc and Ncast are given by the EA.** 

## Claims

**failNc claim** The card manifest must have Nc cards; add phantoms until you do.
Sample over those Npop > Nc cards, and if you find an MVR that doesnt contain the contest, give it an assort value of 0.
That ensures that you cant fool the audit by adding bogus CVRs. You have to point to enough legitimate mvrs to pass the statistical test.
Call this claim the "failNc claim".

**failMargin claim**. The reported margin is (nwinners - nlosers) / N, where N = Nc is the trusted upper limit, or Npop (see below).
For CLCA we can read the CVRs and verify there are nwinners and nlosers, ie the vote totals from the CVRs match the reported contest totals.
When auditing, if not enough CVRs match MVRs that have the contest, then the audit will fail due to the failNc claim.
Claim that one cant manipulate the margin because of this = "failMargin claim".

**failNoerror claim**. When an MVR matches the card, you get a clca assort value of

    noerror = 1.0 / (2.0 - margin / assorter.upperBound()).

The question is, could an attacker manipulate the audit by making noerror larger than it really is?

````
    v = margin must be in (0, 1]. 
    u = upperBound() must be > 0.5, so 1/u < 2, then v / u in (0..2)
    so noerror = 1 / (2 - v/u) -> 1/2 as v/u -> 0, -> inf as v/u -> 2
    
    if every vote is for the winner, and there are no phantoms, v = 1.
    theres no upper bound on u, eg 
        above threshold u = 1 / 2t, where t in (0..1), so u -> inf as t -> 0, u in (0.5..inf)
        below threshold u = 1/2(1-t), where t in (0..1), so u -> inf as t -> 1, u in (0.5..inf)
        dhondt u = (first/last+1)/2; first and last go from 1 to nseats, so u could go from (1/nseats + 1)/2 to (nseats + 1)/2 -> (0.5..inf) as nseats -> inf
    so v/u -> 2 when u -> min u
    
    plurality u = 1, so v / u in (0..1)
    so noerror = 1 / (2 - v/u) -> 1/2 as v -> 0, -> 1 as v -> 1
    so plurality is well behaved
````

So could an attacker manipulate noerror by making it larger than it really is?
If you beleive the failMargin claim, then you cant change v. What about u?
The assorter upperBound (= u) are fixed in code.
Plurality u always = 1. (Could limit the other assorters by bounding min t or max nseats).
Make t and nseats part of the public config, that can be checked by the verifier.
Claim this is sufficient to prevent noerror manipulation = "failNoerror claim".








