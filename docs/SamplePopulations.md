# What does hasStyle mean?
_12/14/25_

## Preliminaries

Define:

* physical card = pcard = the physical ballot or a physical card if the ballot has multiple cards and the cards are scanned and stored seperately
* CVR = scanned electronic record of a physical card
* MVR = human audited physical card
* auditable card = acard = internal computer representation of a physical card. contains the CVR is there is one. 
At minimum it has a _location_ which allows a human auditor to locate the physical card.
* phantom card = auditable card added to ensure number of cards = Nupper.
* card manifest = complete list of auditable cards, one for each physical card and phantom, size = Nupper.

**The upper limit.** We have a trusted upper limit Nc = Nupper, for each contest. One must have Nc acards; add phantoms until you do. 
Now you sample over those Nc acards, and if you get find an mvr that doesnt contain the contest, give it an assort value of 0.
That ensures that you cant fool the audit by adding bogus cvrs. You have to point to enough legitimate mvrs to pass the statistical test. 
Call this claim the "failNc claim".

**The reported margin**. The reported margin is (nwinners - nlosers) / Nupper, where Nupper is trusted. 
We read the cvrs and verify there are nwinners and nlosers, ie the vote totals from the cvrs match the reported contest totals.
If not enough cvrs match mvrs with the contest, then failNc. Claim that one cant manipulate the margin because of this = "failMargin claim".

**Clca noerror**. When an mvr matches the card, you get a clca assort value of 

    noerror = 1.0 / (2.0 - margin / assorter.upperBound()).
    (reportedMargin or dilutedMargin? Jeesh.)

The question is, could an attacker manipulate noerror by making it larger than it really is?

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
The assort upperBound (= u) are fixed in code. 
Plurality always 1. Could bound the other assorters by bounding t or nseats.
Make t and nseats part of public config, that can be checked by the verifier.
Claim this is sufficent to prevent noerror manipulation = "failNoerror claim".

**Diluted or Reported margin** Should noerror use reported or diluted margin?

Noerror is the credit you get when the mvr matchs the cvr. Using diluted margin will decrease the credit when Npop > Nc.

The reported margin is (nwinners - nlosers) / Nupper, where Nupper is trusted.
The diluted margin is (nwinners - nlosers) / Npopulation, where Npopulation > Nupper. (discussed next)

Philip's "More style, less work" paper uses diluted margins. But Npop is smaller when hasStyle = true. You could say Npop is smaller when you know
which cards have the contest, and can sample from just those.

# Population Batches

Each contest has a known population P_c of cards it might be on. |P_c| = Npopulation = Npop is used for the diluted margin. 
When auditing, we sample consistently over P_c.

In the ideal case, we are running a CLCA audit where the CVRs record the undervotes. Then the CVR records the exact contests on the card.
Then Npop = Nc.
In this case SHANGRLA sets hasStyle = true.

There are other scenarios besides CLCA with undervotes where we know exactly which contest are on all cards  (even for polling):

1. When theres only one contest.
2. When the cards are divided into "containers", and each container has only one CardStyle.
3. what else?

When we dont know the exact list, it still is worth narowwing the population size don as much as possible.
To do that, we need to capture the information about which cards might contain C.

Define:

* CardStyle = the full and exact list of contests on a card.
* card.exactContests = list of contests that are on this card = CardStyle.
* card.possibleContests = list of contests that might be on this card.
* "population batch" = batch = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
* batch.possibleContests = list of contests that might be in this batch.
* batch.hasCardStyle = true if all cards in the batch have a single known CardStyle.

For the same audit and contest, you could have different batches with different values of hasCardStyle. For example, one precinct has a
single CardStyle containing contest c, and another has multiple card styles, not all of which contain contest c.


## Examples

### multi-card ballots (MoreStyle section 5)

"This is an idealization of precinct-based voting where each voter in a precinct gets the same ballot style and casts
all c cards of the ballot. If voters in that precinct are eligible to vote in contest S, a fraction 1/c of
the cards in the container will have contest S; otherwise, none of the cards in the container will have S."

Suppose a ballot has c cards to it. Suppose all ballots of the same style are kept in the same container, but the cards are
scanned and stored separately. If there are c cards, we know that only 1/c have the contest.

batch.possibleContests = all contests on the ballot (aka ballot style). batch.hasCardStyle = false, because there are c distinct cards

### CLCA without undervotes

We have CVRs but without undervotes. So we cant rely on the CVR to tell us which cards have a contest.
To do that, we need batches.

### general batches

The EA likely knows somthing about the batches, but what? If its a precinct, the precinct likely has a limited set of card styles. 

Can you separate the ballots by ballot style or card style? 
In SF, we have the damn CVRs. So we know the alleged vote count, and the alleged contest count in the batch.
In Boulder, the redacted ballots appear to have a single card style.

## OneAudit

Pools are the batches, and we only have vote totals for the pool.

OneAudit handles two somewhat distinct use cases:

1. (SF2024) there are cvrs for all cards, but the precinct cvrs cant match the physical ballot. We know exactly how many cards a contest has in the pool. If there are multiple card styles per pool, then the sample size for a contest will be larger than the contest pool count.

2.1 (Boulder24) We have a vote count for the "redacted" pools but no cvrs. Undervotes are not included. We do not know how many cards contain the contest, but it is bounded by the pool size.

2.2 (Boulder25?) Undervotes are included in the vote count for each contest and pool. We know exactly how many cards a contest has in the pool.


## Contest is missing in the MVR

When the contest is missing, we assign 0 to mvr_assort when hasStyle=true, and 0.5 when hasStyle=false.

The first case tanks the audit, and the second may allow attacks?

Could this depend only on batch.hasCardStyle specific ?? Does that solve the problem?


### contest is missing for Polling

Suppose each precinct has one CardStyle, and each precinct stores its own ballot.
You hand count each ballot, but keep all cards for one ballot in the same ballot envelope.
Then it doesnt matter how many cards are in the ballot. Since we know the exact contests on all cards, we
have hasStyle = true. The contest audit can sample only from the batches that contain the contest, and Npop = Nc.

Suppose you audit a ballot that turns out not to have that contest? Seems like its a 0, not a 0.5, when hasStyle = true.
`if (!cvr.hasContest(info.id)) return if (hasStyle) 0.0 else 0.5`
Thats in the code for ClcaAssorter, but not for the primitive assorters.

Same scenario, but the cards are seperated and all are kept in the same pile.
This is the example of MoreStyle section 5.
So each ballot puts n cards in the pile, and the card's pcontests = BallotStyle, and hasStyle = false.
We expect to see (n-1)/n cards without the contest, and 1/n with the contest, so !cvs.contest = 0.5.

## What about Ncast and Nphantom?

Perhaps we have to assume that both Nc and Ncast are given. Why is Ncast != Nc  ??

Do we only need phantoms when hasStyle = true ??

Can we assume that Npop > Nc,and so we dont need phantoms??

Independently we have Nupper = trusted upper bound of number of cards containing C.
Independently we have Ncast = number of cards we know contain C. (hmmm this is a problem. where do we get it if we dont have CVRs with undervotes? Yikes!)
If Ncast < Nc, we add phantoms so Ncast + Nphantoms = Nc.
Npop >= Nc >= Ncast.

Then Ncast = Sum(card.hasContest(c)). If Ncast < Nc, add phantom cards, so Sum(card.hasContest(c)) = Nc = Npop.


## So many questions

Without changing the CardManifest, could you claim hasStyles = false, in order to get a 0.5 score?
Could you use that for an attack?

What if some precincts have ballots with one card, and some have > 1 card
Could each batch have a different value of hasStyle ??
The calculation of Npop must be transparent so humans (verifiers?) can double check it. Eg "precinct 11 only has these possible contests."
The set of PopulationBatchs should be published and committed to. Can a verifier check somehow?




