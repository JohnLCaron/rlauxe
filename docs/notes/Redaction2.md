# Auditing with Redactions and Style-based sampling
09/17/2026

AFAIU, there are 3 ways to rigorously do a card-comparison audit when there are redacted ballots in the public CVR file. Note that the official audit would use the unredacted CVRs, so doesn't have this problem.

Ill use the term "cards" instead of ballots, but when the cards are kept together, or there is only one card on the ballot, then I really mean "ballot".

The "county manifest" is a list of all the cards that were cast. Expand the list of "tabulator/batch/number of records" form of the manifest into a list of "tabulator/batch/recordId" = "imprinted ids". You also keep the location field which presumably helps locate the physical card. The ordering of the county manifest is the "canonical ordering". Once you commit to it, it cannot be changed. You have to commit to it before the seed is chosen, to prevent cheating. The ordering doesnt matter until you commit to it, then it really matters, and becomes the canonical ordering.

The seed is used to create a "psuedo random number generator" (PRNG). This is a cryptographic algorithm that generates a sequence of "psuedo random numbers" (PRN). No one without the seed can predict what the next number in the sequence is (so they are random), but anyone with the seed can recreate the sequence (so they are deterministic or "psuedo random").

No matter what the sampling strategy is, or whether there are redactions or not, the PRNG is used to assign a PRN to each of the cards in the manifest in canonical order. The state does exactly the same thing that the public verifier does, so that verification is possible. 

## Sampling strategies

The _sampling strategy_ is choosing which cards to sample for the audit. The samples must be randomly chosen from the _sampling population_, namely the entries in the manifest. 

**Uniform sampling** of N cards chooses the N cards with the lowest PRN. If the _sorted manifest_ is the manifest sorted by PRN, then the algorithm is _choose the first N from the sorted manifest_.

When there are multiple contests on a card, **style-based sampling** defines for each contest the _canonical contest sequence_ as the _sequence of cards from the sorted manifest that contain the contest_. To do style-based sampling, you need to know each card's style, which is the set of contests on that card. 

To simultaneously audit multiple contests, you have to ensure that for each contest, the set of chosen cards are randomly selected across the population of cards that contain the contest. The **consistent sampling** algorithm is as follows:

1. For each contest, decide if you want to audit it, and how many samples you need = N(contest). 
2. Examine the sorted manifest in order. If the card contains any contest that needs more samples, then add the card to the sample list, and decrement the contests' needMore counter.
3. Iterate until all contests have enough samples.

This creates an ordered list of samples that, for each contest, contains the first N(contest) cards from the canonical contest sequence. This is what has to be checked when validating the samples chosen in a consistent sampling. 

The advantage of consistent sampling is that if a sampled card contains more than one audited contest, then the audit uses the card to audit all those contests at once.

The number of cards needed for each contest is an estimate, and may be too high or too low, depending on the result of comparing the physical cards with the CVRs. If an audit needs more samples than estimated, and there are further samples that contain that contest, the audit can use those samples for the contest until the canonical contest sequence is broken. If a card is not chosen for the audit, that breaks the canonical sequence for all the contests in the card's style. This detail only affects the audit, not the validation of samples.

TODO add psuedocode

## Redaction strategies

Since the county manifest does not have style information, the CVR file supplements the manifest. The unredacted CVR file has a 1-1 correspondence with the manifest. The presence of redactions complicates this.

Create a style-based manifest by attaching each unredacted CVR to the manifest entry, preserving the manifest ordering. The _redacted entries_ are the ones without a CVR attached. 

Then there are three strategies:

### 1. use phantoms

The redacted entries are marked as _phantoms_. The style is the set of contests the card could possibly contain. In the worst case, it is the set of all contests in the county. Its also possible that you can narrow down that set by subtracting the unredacted CVRs from the reported county totals. The set of possible contests on the phantom card is the contests with totalCards(contest) - cvrCards(contest) > 0. This can only be done rigorously if the county reports the total number of cards per contest.

If a phantom is selected to be sampled, the audit assumes a worst case score. That makes the audit risk estimate "conservative" meaning that the estimated risk is an upper bound on the true risk. So you can say "we know that the chance of the reported outcome being false is not greater than x %".

### 2. use a single OneAudit pool

If the redacted CVRs are retained in the CVR file, and their votes are replaced with a "\*", then we can see what contests are present, and use that as the style. If the votes are simply removed or all columns are marked  with a "\*", then calculate the possible contests as in "use phantoms" section above.

Attach the redacted CVRs to the style-based manifest, using the redacted CVR style, with no votes.

The redacted entries are placed in a single OneAuditPool. The pool subtotal is the redacted aggregation subtotal, along with the ncards per contest in the redaction. If the redacted aggregation subtotal is not given, it can be calculated by subtracting the unredacted CVRs from the reported county subtotal. This can only be done rigorously if the county reports the total number of cards per contest.

If a redacted entry is selected to be sampled, the audit uses the OneAudit algorithm to compute the score. On average, this requires fewer samples than assuming the worst case all the time.


### 3. use multiple style-based OneAudit pools 

To use this option, the redaction must be divided into multiple pools, typically by card style, although card styles can also be combined. There must be a way to identify which group each redacted CVR belongs to. The simplest thing is to create unique group names and put those into the ballotStyle field of both the redacted CVRs and the aggregation lines. This unambigously defines which group a redacted CVR belongs to.

Attach the redacted CVRs to the style-based manifest, using the redacted CVR style, with no votes.

The redacted entries are placed in multiple OneAuditPools. The pool subtotal is the redacted aggregation subtotal, along with the ncards per contest in the redaction. A normal OneAudit then can proceed, which gives the best results for minimizing sample sizes.


## Validating the samples chosen with consistent sampling

The chosen samples from a consistent sampling can only be validated if all the redacted CVRs have accurate style information. This is because presumably the unredacted CVRs were used by the state in the selection process. If the public verifier doesn't have exactly the same style information, then things could diverge.

Deciding if there is enough information to validate samples needs more thought. For example, if a county uses a single ballot style, then you know the style for all ballots. If the redacted CVRs are retained in the CVR file, and their votes are replaced with a "\*", then you also have enough information to accurately validate the samples chosen.







