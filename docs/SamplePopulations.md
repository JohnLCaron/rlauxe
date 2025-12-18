# What does hasStyle mean?
_12/18/25_

# TL;DR

The best case is to have CVRs that record undervotes. Then things are simple and as efficent as possible.

config.hasStyles -> config.cvrsHaveUndervotes (aka cvrsAreComplete)

Otherwise, we need to create "Population" containers that know which contests are in it, and use these 
when choosing audit samples. Set hasStyle independently on each population (rather than globally on the audit),
and use those when deciding the assort value when an MVR is missing a contest. This should be done for Polling assorters
as well as Clca an OneAudit assorters.

population.hasStyles -> population.knowExactContests = "we know exactly what contests are on all the cards in this population".

## Preliminaries

Define:

* physical card = pcard = the physical ballot or a physical card if the ballot has multiple cards and the cards are scanned and stored seperately
* CVR = scanned electronic record of a physical card
* MVR = human audited physical card
* auditable card = card = internal computer representation of a physical card. contains the CVR if there is one. 
At minimum it has a _location_ which allows a human auditor to locate the physical card.
* phantom card = auditable card added to ensure number of cards = Nc.
* card manifest = complete list of auditable cards, one for each physical card and phantom; size = Nupper.

**The upper limit.** We have a trusted upper limit Nc = Nupper, for each contest. The card manifest must have Nc cards; add phantoms until you do. 
Sample over those Npop > Nc cards, and if you find an MVR that doesnt contain the contest,  give it an assort value of 0.
That ensures that you cant fool the audit by adding bogus CVRs. You have to point to enough legitimate mvrs to pass the statistical test. 
Call this claim the "failNc claim".

**The reported margin**. The reported margin is (nwinners - nlosers) / N, where N = Nc is the trusted upper limit, or Npop (see below). 
For CLCA we can read the CVRs and verify there are nwinners and nlosers, ie the vote totals from the CVRs match the reported contest totals.
When auditing, if not enough CVRs match MVRs that have the contest, then the audit will fail due to the failNc claim. 
Claim that one cant manipulate the margin because of this = "failMargin claim".

**Clca noerror**. When an MVR matches the card, you get a clca assort value of 

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

**Diluted or Reported margin** Should noerror use reported or diluted margin?

Noerror is the credit you get when the mvr matchs the cvr. Using diluted margin will decrease the credit when Npop > Nc.

The reported margin is (nwinners - nlosers) / Nupper, where Nupper is trusted.
The diluted margin is (nwinners - nlosers) / Npopulation, where Npopulation > Nupper. (discussed next)

Philip's "More style, less work" paper uses diluted margins. (Paper shows how Npop is smaller when hasStyle = true. 
Could also say that Npop is smaller when you know which cards have the contest, and can sample from just those.)

In TestAvgAssortValues, testAvgAssortWithDilutedMargin() tests that dilutedMargin, not reportedMargin, agrees with cvrs.assortMargin.

Conclusion: use diluted margin for nerror.

# Populations

Each contest has a known population P_c of cards it might be in. |P_c| = Npopulation = Npop is used for the diluted margin. 
When auditing, we sample consistently over P_c.

In the best case, we are running a CLCA audit where the CVRs record the undervotes. Then, the CVR records the exact contests on the card, and Npop = Nc.

There are other scenarios besides CLCA with undervotes where we know exactly which contests are on each card (even for polling):

1. When theres only one contest.
2. When the cards are divided into "containers", and each container has only one CardStyle.
3. what else?
   
In the case that "we know exactly what contests are on all cards", SHANGRLA sets hasStyle = true. So we will take that as the meaning
of hasStyle.

When we dont know the exact list of contests on all the cards, it is worth narrowing the population size down as much as possible,
to minimize sample sizes.

Define:

* CardStyle = the full and exact list of contests on a card.
* card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
* card.possibleContests = list of contests that might be on this card.

* "population" = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a ordered list).
* population.possibleContests = list of contests that are in this population.
* population.hasCardStyle = true if all cards in the population have a single known CardStyle = "we know exactly what contests are on each card".
* population.exactContests = population.hasCardStyle

Populations describe the containers that the physical cards are kept in.

For the same audit and contest, you could have different populations with different values of hasCardStyle. For example, one precinct has a
single CardStyle containing contest c, and another has multiple card styles, not all of which contain contest c.

The calculation of Npop must be transparent so verifiers (or just humans?) can verify it.
The Population list should be published / committed to.

Its worth noting that the EA (election authority) knows the card style for every voter and ballot. 
So we are dealing with the limitations of associating CardStyles with the anonymous physical ballots and scanned cvrs.

The point is that one has to deal with the specifics of how each election stores their ballots. 
Possibly influence how ballots are handled to make the audit more efficient.

## Examples

### multi-card ballots, Polling audit (MoreStyle section 5)

"This is an idealization of precinct-based voting where each voter in a precinct gets the same ballot style and casts
all c cards of the ballot. If voters in that precinct are eligible to vote in contest S, a fraction 1/c of
the cards in the container will have contest S; otherwise, none of the cards in the container will have S."

Suppose a ballot has c cards to it. Suppose all ballots of the same style are kept in the same container, but the cards are
scanned and separately addressable. If there are c cards, we know that only 1/c have the contest.

population.possibleContests = all contests on the ballot (aka ballot style). population.hasCardStyle = false, because there are c distinct CardStyles.

### CLCA without undervotes

We have CVRs but dont record the undervotes. We dont know which cards have undervotes.
To do that, we need populations.

### OneAudit

OneAudit handles two somewhat distinct use cases:

1. There are cvrs for all cards, but the precinct cvrs cant match the physical ballot. We know exactly how many cards a contest has in the pool,
and the vote count. SanFrancisco 2024.

2. We have a vote count for the "redacted" pools but no cvrs. Undervotes need to be included in the vote count. (Boulder 2024 does not
record the undervotes for the redacted pools, so we guess what they are for the simulation.)

In both cases we know the vote totals for the population. Is the vote count needed for non-OA populations?

## Contest is missing in the MVR

When the contest is missing, we assign 0 to mvr_assort when hasStyle=true, and 0.5 when hasStyle=false.

The first case tanks the audit, and the second may allow attacks(?)

Could this assignment use population.hasCardStyle? Does that solve the problem?


### Contest is missing for Polling

Suppose each precinct has one CardStyle, and each precinct stores its own ballot.
You hand count each ballot, but keep all cards for one ballot in the same ballot envelope.
Then it doesnt matter how many cards are in the ballot. Since we know the exact contests on all cards, we
have hasStyle = true. The contest audit can sample only from the populations that contain the contest, and Npop = Nc.

Suppose you audit a ballot that turns out not to have that contest? Seems like its a 0, not a 0.5, when hasStyle = true.
`if (!cvr.hasContest(info.id)) return if (hasStyle) 0.0 else 0.5`
Thats in the code for ClcaAssorter, but not for the primitive assorters.

If in the same scenario, but with the cards seperated, we have the example of MoreStyle section 5.
Each ballot puts n cards in the pile, and the card's pcontests = BallotStyle, and hasStyle = false.
We expect to see (n-1)/n cards without the contest, and 1/n with the contest, so we cant tolerate setting
assort = 0 when mvr doesnt have the contest, since that will happen a lot. 

It seems that using population.hasCardStyle would solve this problem?


## What about Ncast and Nphantom?

When hasStyles = true, we can count the cards and see how many each contest has. Use that as Ncast, then add phantoms as needed.

When hasStyles = false, where so we getr Ncast? Perhaps we have to assume that both Nc and Ncast are given by the EA?

Or do we only need phantoms when hasStyle = true ??  Can we assume that Npop > Nc,and so we dont need phantoms??

## So many questions

Without changing the CardManifest, could you claim hasStyles = false, in order to get a 0.5 score?
Could you use that for an attack?





