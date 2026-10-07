# Auditing with Redactions and Style-based sampling
09/24/2026

AFAIU, there are 3 ways to rigorously do a card-comparison audit when there are redacted ballots in the public CVR file. Note that the official audit would use the unredacted CVRs, so doesn't have this problem.

I'll use the term "cards" instead of ballots, but when the cards are kept together, or there is only one card on the ballot, then I really mean "ballot".

The "county manifest" is a list of all the cards that were cast. Expand the list of "tabulator/batch/number of records" form of the manifest into a list of "tabulator/batch/recordId" = "imprinted ids". You also keep the location field which presumably helps locate the physical card. The ordering of the county manifest is the "canonical ordering". Once you commit to it, it cannot be changed. You have to commit to it before the seed is chosen, to prevent cheating. The ordering doesn't matter until you commit to it, then it really matters, and becomes the canonical ordering.

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

The number of cards needed for each contest is an estimate, and may be too high or too low, depending on the result of comparing the physical cards with the CVRs. If an audit needs more samples than estimated, and there are further samples that contain that contest, the audit can use those samples for the contest until the canonical contest sequence is broken. If a card is not chosen for the audit, that breaks the canonical sequence for all the contests in the card's style. This ensures that each contest's sample is statistically independent. This detail only affects the audit, not the validation of samples.

````
// simplified example for consistent sampling with styles
// return list of Pair(index, prn) that is the canonical sequence

fun consistentSamplingByStyle(
    wantNmvrs: Map<Int, Int>, // contest id -> number of samples wanted. contests not in this are ignored
    maxSamples: Int,
    sortedCards: Iterator<SamplingCardIF>, // sorted by prn
): List<Pair<Int, Long>> {
    val canonSequence = mutableListOf<Pair<Int,Long>>()

    // how many we still need
    val needNmvrs = wantNmvrs.toMutableMap().withDefault{ 0 }
    
    var cardIndex = 0 
    val samplingCardIter = sortedCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        canonSequence.size < maxSamples &&
        needNmvrs.any { it.value > 0 }
    ) {
        // get the next card in sorted order
        val card = samplingCardIter.next()

        // do we want it?
        var include = false
        for (contestId in needNmvrs.keys) {
            if (card.hasContest(contestId) && (needNmvrs[contestId]!! > 0)) {
                include = true
                break
            }
        }

        if (include) {
            canonSequence.add(Pair(cardIndex, card.prn()))
            
            // decrement needNmvrs for any contests on the card
            for (contestId in needNmvrs.keys) {
                if (card.hasContest(contestId)) {
                    needNmvrs[contestId] = needNmvrs[contestId]!! - 1
                }
            }
        }

        cardIndex++
    }
    
    return canonSequence
}
````

## Redaction strategies

Since the county manifest does not have style information, the CVR file supplements the manifest. The unredacted CVR file has a 1-1 correspondence with the manifest. The presence of redactions complicates this.

Create a style-based manifest by attaching each unredacted CVR to its manifest entry, preserving the manifest ordering. The _redacted entries_ are the ones without a CVR attached. 

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


//////////////////////////////////////////////////////////////////////////////////////////////////////////

# possible problems with unredacted cvr files

1. Some spreadsheets may convert some CVR imprintedId fields like  "2-2-86" to "02/02/1986". The "Detect special numbers" option should be unchecked. 

2. The file could be in different cvr format (eg Garfield 2020 file in votedatabase had a different (older?) cvr format with differnt column names). One could apply a few "well-formedness" checks. 
    
    - I check that rows 2 and 3 (contest and choice) have same column range of non-blank entries.
    - I check that ImprintedId = Tabulator-Batch-Record (thats where I noticed item 1).

3. There may be mistakes in the headers, for example Las Platas 2020 was missing the last 5 contest headers. This should not affect redaction, though it may affect the ability of public verifiers to read the redacted file.

4. The macro makes assumptions about the layout of the cvr file:

    - macro scans row 4 to find BallotType (btCol) and optionally PrecinctPortion (ppCol).
    - All columns to the right of BallotType (voteStartCol = btCol + 1) are treated as vote choice columns. To allow for the possibility of different headers, I use the first non-blank column of the contest header (row 2) to indicate where the vote columns start. However, I havent seen anything other than the non-standard Garfield file with this problem.

5. The default is to use the PrecinctPortion (if present) in the key PrecinctPortion|BallotType. I think the PrecinctPortion should be redacted for privacy. Im not sure where the idea came from to not only _not_ redact PrecinctPortion, but use it for the rare row grouping, which could make many more redactions, as well as compromising voter privacy. Lori, am I misunderstanding this?

6. The macro assumes that the file is sorted by BallotStyle. By making two passes through the in-memory rows, one could eliminate the need to externally sort.

7. Excel assumes that the file fits into memory. Not sure how the internal memory requirement compares to the uncompressed size on disk. Largest file size Ive noticed is 280 Mb (Denver 2020). Windows itself takes 2-4 Gb; I guess an 8 Gb machine might be useable, and 16 GB probably safe.

8. It would be a simple modification to change the row redaction to only put * where the row has non-blank columns. I dont see any guidelines indicating to put * in all columns. Where did that come from?

## From Lori

John and I have been going back on audit issues related to whether we recommend the Excel macro in the near term and plan for the following in the longer term:
* changes to the ballot selection code in CORLA to use card-based sampling, and
* a corresponding change to redaction that works with card-based sampling, also integrated into CORLA.

I'll try to boil down the conversation (so the rest of you don't have to read it) and add additional information:
1. I suggested that we recommend the Excel macro redaction solution to counties as a way to enable them to produce redacted CVRs more easily. The state-recommended procedure for redacting CVRs uses Excel.
2. John looked at the macro and sees some issues. In particular, the procedure for importing a CVR file that I showed in the video does not work if any of the ImprintedIds looks like a date (An imprintedId of 9-11-26 gets turned into 9/11/2026.) The issues are probably manageable, but they complicate the process.
3. I did find the state-recommended redaction procedure.  It's from 2017. It's mostly the same as what the Excel macro does except for one additional step:

   1. On page 3 of that document, in the bulleted text at the bottom of the page, it is recommended that the county should sum the contest votes in both the unredacted CVR and the redacted CVR and compare them. If the difference in votes is the same as the number of redacted ballots, that reveals how that contest was voted on the redacted ballots. The procedure recommends that a few more ballots (with votes that are different from the votes in the original redacted ballots) should be redacted in order to conceal the votes in the original redacted ballots. That's one of the things (approximately) that our python-based redaction program does. I don't think we should try to do that in the Excel macro (it's hard enough to read as it is).
   2. On page 11 of that document is the hand-wavy instruction to search for short runs of ballots with the same BallotType, like it's easy.  But that's the part that's tedious and hard for a human!  Imagine searching through 130,000 rows to find runs of less than ten ballots. That's the worst part of the process. but the macro makes it fast and easy.
   
I've sent mail to Daniel Millsap in the Broomfield elections department to see if he can tell me how he redacts CVRs.

Summary: I still think that the Excel macro is the least-common-denominator solution for redacting CVRs. It probably needs to be wrapped in an overall redaction procedure like:
* County staff member does their usual redaction procedure, right up to the point where they would normally start the long and tedious process of searching through every CVR for short runs of identical BallotTypes/PrecinctPortion values.
* At this point, they install the macro and run it, which redacts rare style/precinct combinations.
* Then they finish their usual procedure, which might include the work described in 3.1 above.

It's not as nice as a one-step redaction, but it makes their job much easier than it is now.

## my reply

With Lori's help, and the state-recommended redaction procedure, Ive come to understand this lovely legalese:
````
Notwithstanding any other provision of this section, no ballot, or any portion
thereof, may be made available of inspection where the ballot, or any portion
thereof, is identical in printed form, considering a combination of the election
contests at issue and precinct coding, to only nine or fewer ballots, or
comparable portions thereof, among all ballots used in the same election.
However, any such ballot, or any requested portion thereof, that is identical in
printed form to ten or more ballots, or comparable portions thereof, used in the
same election may be inspected.
````
in my native language as:

**_A ballot card must be redacted when it is identical in district or precinct style to only 9 or fewer ballots cards among all the ballot cards used in the same election._**

where

_"district or precinct style" is another way of saying "ballot style and precinct (or just ballot style, if the precinct is not encoded on the ballot)"_

So I better understand the need to redact on precincts. I would still recommend "dont encode the precinct on any public-facing ballot" but thats a different issue.

Along these lines, some things to report:

* The sorting instruction example from the SoS document does not include sorting on the precinct, so it is redacting on ballot styles, not precinct styles.
* For the 2020 elections, all counties except Boulder and Fremont have a precinct column. Mesa has a precinct column  which is blank on 113 cvrs (out of 91500).
* For the 2020 elections, the following counties have ballotTypes which dont have a unique set of contests:: Arapahoe, Denver, Eagle, Mesa, Routte, Saguache. For these counties, the standard instructions will not work correctly.
    - Arapahoe is using BallotType is some unknown way
    - Denver, Eagle, Routte, Saguache look like might be using a single ballotType for multiple-cards on the ballot
    - Mesa looks like I'm not parsing the redaction correctly NOT TRUE
* For the four counties we have for 2026 primary election, Boulder and Morgan dont have a precinct column, and all 4 have unique ballot types.

From my POV, putting * where the votes are in the redacted ballots (i.e. preserve the style) would be a big win for style-based audits. Among other things, it would let me accurately simulate style-based public audits and compare to current practice. If we do ever get to using style-based public audits, I would recommend a separate AGGREGATION line for each district or precinct style, unless you need to combine some (or all) for privacy. This would minimize the effects of redaction on sample size, esp as the percent redactions / total increases.

## Notes on "REVIEWING AND REDACTING CAST VOTE RECORDS AND BALLOT IMAGES"

p.3. 

If in your election at least 10 ballots were cast for each and every district _(= county I assume)_ style (if you
reported results by district or ballot style) or precinct style (If you reported results by precinct)...

We propose one optional solution, which retains the unique CVR identifying information in Columns A – G, and then
deletes the voting choices from traceable ballots.

it may be advisable to redact a handful of other CVRs with “yes” votes for Ballot Question 1A,
  to preserve ballot secrecy.

p.4. 

If an individual CVR is redacted, the corresponding ballot image should not be produced in
response to a CORA request.

p.5.

````
Notwithstanding any other provision of this section, no ballot, or any portion
thereof, may be made available of inspection where the ballot, or any portion
thereof, is identical in printed form, considering a combination of the election
contests at issue and precinct coding, to only nine or fewer ballots, or
comparable portions thereof, among all ballots used in the same election.
However, any such ballot, or any requested portion thereof, that is identical in
printed form to ten or more ballots, or comparable portions thereof, used in the
same election may be inspected.
````

_where the ballot, "or any portion thereof" == card_

_"combination of the election contests at issue" == ballot style_

_"and precinct coding" == if the precint is encoded on the ballot_

**_No ballot card may be made available for inspection where the ballot card is identical in ballot style and precinct (or just ballot style, if the precinct is not encoded on the ballot) to only 9 or fewer ballots cards among all the ballot cards used in the same election._**

The easiest way to determine whether your county has less than 10 counted ballots of a
particular district or precinct style is...

_"district or precinct style" another way of saying "ballot style and precinct (or just ballot style, if the precinct is not encoded on the ballot)"_

**_No ballot card may be made available for inspection where the ballot card is identical in district or precinct style to only 9 or fewer ballots cards among all the ballot cards used in the same election._**

p 10.

Sort on Ballot Type, then on CvrNumber, TabulatorNum, BatchId, ImprintedId.

_Note that Precinct Portion is not included in the sorting instructions_

p 11.

Clear all vote columns to blank.

p 13.

Save this file as "Redacted_countyName.csv"

If a county finds it necessary to redact its CVR file, the county should produce this redacted CVR
file in response to any CORA request.

p 14.

Now save the Redacted CVR file with a new name to reflect that the next version will be
summed. E.g., Summed_Redacted_countyName.csv. You are doing this because you
should produce the redacted CVR file in response to the CORA request, not the version you will
create to independently tabulate all unredacted votes in the CVR file

p 16.

_Sum all vote columns_

You can now compare the vote totals of the redacted file to the final votes reflected in your statement of
ballots cast or ENR results, to ensure that your redactions resolve all traceable ballot issues, and
do not create any new ones.

B. Ballots marked on an ICX device are obviously different “in printed form” than pre‐printed mail ballots.

_Whats an ICX ballot?_

///////////////////////////////////////////////////////
kotlin no precinct
County La Plata from /home/stormy/datadrive/votedatabase/cvr/Colorado/La Plata/cvr.csv
*** Pass 1: Looking for rare ballot styles.

Rare ballot styles (2 of 7 total):
4 ballot(s)  [ballot type: "6", style #6]
6 ballot(s)  [ballot type: "7", style #5]

Redaction is needed.
*** Pass 2: Building the aggregate row.

ndonors = 200
Rare ballots: 10.
Borrowing from common styles to satisfy these requirements:
- the ballot aggregation must contain at least 10 ballots.
- every contest in the rare styles must appear on at least 10 ballots in the aggregation.

Contests below minimum:
'Forest Lakes Metropolitan District Ballot Issue 6A': needs 6 more ballot(s)
'Los Pinos Fire Protection District Ballot Issue 7A': needs 4 more ballot(s)

Selecting ballots to borrow from common styles (this can take a few minutes)...
Ballots borrowed for minimum counts: 10

*** Balancing near-unanimous contests.

Make sure that the following constraint is met:
- No contest in the aggregate may be near-unanimous. 'Near-unanimous'
  means all but 2 votes go to a single choice.

There are no near-unanimous contests.

*** Pass 2 complete.
Ballots from rare styles/precincts: 10
Ballots borrowed from common styles: 10
Total ballots in aggregate: 20

*** Pass 3: Writing output.
Output written to /home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/La Plata.csv.
That took 610.4 ms
AGGREGATED,,,,,,AGGREGATED,7,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,7,3,0,0,0,0,0,0,0,2,7,1,0,7,3,3,7,9,7,3,7,2,6,1,6,1,6,1,5,2,6,1,6,1,6,4,6,4,6,4,5,5,8,2,8,2,8,2,5,5,3,7,4,6,6,4,,,5,5,4,6
///////////////////////////////////////////////////////////////////////////////

| Excel macro - rare rows only
|             | unredacted group min size |           | redacted group min size |
 
| La Plata                  | precinct | ballot Style | nredactions | precinct | ballot Style |
|---------------------------|----------|--------------|-------------|----------|--------------|
| Excel precinct/rare only  | 4        | 4            | 14          | 25       | 235          | 
| Python precinct/rare only | 4        | 4            | 14          | 25       | 235          |
| Python precint/balancing  | 4        | 4            | 27          | 25       | 235          |  
| Python style/balancing    | 4        | 4            | 20          | 4        | 235          |  
| Kotlin precinct/balancing | 4        | 4            | 19          | 23       | 235          |  

| Sedgwick                  | precinct | ballot Style | nredactions | precinct | ballot Style |
|---------------------------|----------|--------------|-------------|----------|--------------|
| Excel precinct/rare only  | 1        | 9            | 13          | 14       | 29           |  
| Python precinct/rare only | 1        | 9            | 13          | 14       | 29           |
| Python precint/balancing  | 4        | 4            | 20          | 14       | 29           |  
| Python style/balancing    | 1        | 9            | 18          | 1        | 29           |  
| Kotlin style/balancing    | 1        | 9            | 17          | removed  | 29           |  

| Denver                    | precinct | ballot Style | nredactions | precinct | ballot Style |
|---------------------------|----------|--------------|-------------|----------|--------------|
| Excel precinct/rare only  |          |              |             |          |              |  
| Python precinct/rare only | 281      | 2            | 473+2       | 279      | 10           |
| Python precint/balancing  | 281      | 2            | 473+2       | 279      | 10           |  
| Kotlin precinct/balancing | 281      | 2            | 473+12      | 279      | 10           |  










