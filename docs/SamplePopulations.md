# Sample Populations
_01/18/26_

## TL;DR

The most efficient audit has CVRs (that include undervotes) for all ballots.

Otherwise, we need to create "Population" card containers that know which contests are in it, and use these when choosing audit samples.

Each population sets "hasSingleCardStyle" = true if all cards in the population have one CardType (i.e all cards in the population have the same contests).  This is set independently on each population, and replaces the global hasStyle (aka use_style) flag.

The population.hasSingleCardStyle field is used when deciding the assort value when an MVR is missing a contest, for all audits (including Polling?).

The use of populations is implicit in the "More styles, less work" paper. Setting hasStyle by population and using hasStyle in Polling audits is new, I think. These complexities arise in multi-contest audits and multi-card ballots.

**Table of Contents**
<!-- TOC -->
* [Sample Populations](#sample-populations)
  * [TL;DR](#tldr)
  * [Definitions](#definitions)
  * [Populations](#populations)
  * [Examples](#examples)
    * [multi-card ballots, Polling audit (MoreStyle section 5)](#multi-card-ballots-polling-audit-morestyle-section-5)
    * [CLCA without undervotes](#clca-without-undervotes)
    * [OneAudit](#oneaudit)
  * [Contest is missing in the MVR](#contest-is-missing-in-the-mvr)
    * [Contest is missing in the MVR for Polling](#contest-is-missing-in-the-mvr-for-polling)
  * [What about Ncast and Nphantom?](#what-about-ncast-and-nphantom)
* [Claims](#claims)
  * [Should noerror use reported or diluted margin?](#should-noerror-use-reported-or-diluted-margin)
<!-- TOC -->

## Definitions

* **physical card** = pcard: the physical ballot or physical card if the ballot has multiple cards and the cards are scanned and stored separately.
* **CVR**: scanned electronic record of a physical card
* **MVR**: human audited physical card
* **auditable card** = card:  internal computer representation of a physical card; contains the CVR if there is one. A card usually has a _location_ 
  which allows a human auditor to locate the physical card, and either a CVR or a _population_ that it belongs to.

* **CardStyle**: the full and exact list of contests on a card.
* **population**: a distinct container of pcards, from which we can retreive named cards (even if its just by an index into an ordered list).
* **population.possibleContests**: list of contests that are in this population.
* **population.hasSingleCardStyle**: is true if all cards in the population have a single known CardStyle, so that "we think we know exactly what contests are on each card".

* **contest upper limit**: For each contest, we have a trusted upper limit Nc = Nupper, of the number of cards containing the contest.
* **phantom card** = auditable card added to ensure number of cards = Nc. Phantom cards arent in a population; they have a list of contests they contain.
* **card manifest** = complete list of auditable cards, one for each physical card and phantom card.

* **Contest population** = P_c: For each contest, the set of cards that may contain the contest. This is the sample population for the contest. 
* **Contest population size** = \|P_c\| = Npop: The size of the contest's population. Npop >= Nc. If hasStyle = true for all populations containing the contest, then Nc = Npop.

* **Reported margin**: Each assorter has a reported margin with Nc as denominator; For Plurality it is (nwinners - nlosers) / Nc.
  Other assorters have numerators somewhat different.
* **Diluted margin**:  Each assorter has a diluted margin with Npop as denominator; for Plurality it is (nwinners - nlosers) / Npop.
  Other assorters have numerators somewhat different.


## Populations

Each contest has a known population P_c of cards that might contain it. |P_c| = Npopulation = Npop is used for the diluted margin. 
When auditing, we sample consistently over P_c.

In the best case, we are running a CLCA audit where the CVRs record the undervotes. Then, the CVRs record the exact contests on the card, and Npop = Nc.

There are other scenarios besides CLCA with undervotes where we know exactly which contests are on each card (even for polling):

1. When theres only one contest.
2. When the cards are divided into populations that have only one CardStyle.
   
In the case that "we know exactly what contests are on all cards", SHANGRLA sets hasStyle = true. So we will take that as the meaning
of hasStyle.

When we dont know the exact list of contests on all the cards, it is still worth narrowing the population size down as much as possible,
to minimize sample sizes.

Populations describe the containers that the physical cards are kept in. 
Using populations minimizes the diluted count (and so maximizes the margins) as much as possible. 

For the same audit and contest, you could have different populations with different values of hasSingleCardStyle. For example, one precinct has a
single CardStyle containing contest c, and another has multiple card styles, not all of which contain contest c.

The calculation of Npop must be transparent so verifiers (or just humans?) can verify it.
The Population list should be published / committed to.

Its worth noting that the EA (election authority) knows the card style for every voter and ballot. 
So we are dealing with the limitations of associating CardStyles with the anonymous physical ballots and scanned cvrs.

## Examples

### multi-card ballots, Polling audit (MoreStyle section 5)

"This is an idealization of precinct-based voting where each voter in a precinct gets the same ballot style and casts
all c cards of the ballot. If voters in that precinct are eligible to vote in contest S, a fraction 1/c of
the cards in the container will have contest S; otherwise, none of the cards in the container will have S."

Suppose a ballot has c cards to it. Suppose all ballots of the same style are kept in the same container, but the cards are
scanned and separately addressable. If there are c cards, we know that only 1/c have the contest.

population.possibleContests = all contests on the ballot (aka ballot style). population.hasSingleCardStyle = false, because there are c distinct CardStyles.

### CLCA without undervotes

We have CVRs but dont record the undervotes. 
Then we need to use populations to specify where the undervotes might be.

### OneAudit

OneAudit handles two somewhat distinct use cases:

1. There are cvrs for all cards, but the precinct cvrs cant be matched to the physical ballot. We know exactly how many cards a contest has in the pool,
and the pool's vote count. This is the SanFrancisco 2024 test case.

2. We have cvrs from some cards, but not all. The remaining cards are in pools with pooled vote counts. 
Undervotes need to be included in the vote count. This is the Boulder 2024 test case. (Boulder 2024 does not
record the undervotes for the redacted pools, so we guess what they are for the simulation.)

The OneAudit pools are the populations.

In San Francisco, the pools do not appear to have one CardStyle, so population.hasSingleCardStyle = false.
In Boulder, each pool appears to have one CardStyle, so population.hasSingleCardStyle = true.

For OneAudit, we know the vote totals for the population. In the general case we dont necessarily know the vote counts
for each population. Also see Ncast section below.

## Contest is missing in the MVR

When the contest is missing on the MVR, we assign 0 to mvr_assort when hasStyle=true, and 0.5 when hasStyle=false.

The first case tanks the audit, and the second may allow attacks(?)

Using population.hasSingleCardStyle instead of global hasStyles should be better. TODO investigate the effect of that change.

But still, note that population.hasSingleCardStyle requires all cards to have the same CardStyle in a population. 
So it is false even if theres only one card thats different.
That could have a big effect on the assort values, but it only increases the diluted count by 1. Seems fishy.

An attacker could falsely claim hasSingleCardStyle=false; could we detect that?


### Contest is missing in the MVR for Polling

Suppose each precinct has one CardStyle, and each precinct stores its own ballots, and we are doing a Polling audit.
You hand count the ballots, and keep all cards for a ballot in the same ballot envelope.
Then it doesnt matter how many cards are in the ballot. Since we know the exact contests on all cards, we
have hasSingleCardStyle = true. The contest audit can sample only from the populations that contain the contest, and Npop = Nc.

Suppose you audit a ballot that turns out not to have that contest? Seems like mvr_assort should be 0, not a 0.5, when hasSingleCardStyle = true.
`if (!cvr.hasContest(info.id)) return if (hasStyle) 0.0 else 0.5`
is in the code for ClcaAssorter, but not for the primitive assorters,which have
`if (!cvr.hasContest(info.id)) return 0.5`

If in the same scenario, but with the cards separated (the example of MoreStyle section 5):
Each ballot puts n seperate cards in the pile, and hasSingleCardStyle = false.
We expect to see (n-1)/n cards without the contest, and 1/n with the contest, so we cant tolerate setting
mvr_assort = 0 when mvr doesnt have the contest, since that will happen a lot. We need to set it to 0.5.

So we need to set mvr_assort based on hasSingleCardStyle, just as with CLCA and OneAudit.


## What about Ncast and Nphantom?

When hasStyles = true, we can count the cards and see how many each contest has. Use that as Ncast, then add phantoms as needed.

When hasStyles = false, where do we get Ncast? If the populations all have vote totals that include undervotes, we can get Ncast from them.
Otherwise we have to assume that both Nc and Ncast are given by the EA.

# Claims

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
    (reportedMargin or dilutedMargin? Jeesh.)

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
Claim this is sufficent to prevent noerror manipulation = "failNoerror claim".


## Should noerror use reported or diluted margin?

Noerror is the credit you get when the mvr matchs the cvr. Using diluted margin will decrease the credit when Npop > Nc.
Noerror uses v/u, where v is the margin. Is it the reported margin or the diluted margin?

The reported margin is (nwinners - nlosers) / Nupper, where Nupper is trusted.
The diluted margin is (nwinners - nlosers) / Npopulation, where Npopulation > Nupper. 

Philip's "More style, less work" paper uses diluted margins. (Paper shows how Npop is smaller when hasStyle = true.
Could also say that Npop is smaller when you know which cards have the contest, and can sample from just those.)

Rlauxe's TestAvgAssortValues.testAvgAssortWithDilutedMargin() shows that dilutedMargin, not reportedMargin, agrees with the
average cvrs assortMargin.

Conclusion: use diluted margin for noerror calculation








