# Redaction Notes
last changed 9/06/2026

# Corla County Election

**Given**
- Each county publishes their manifest batch counts. This gives the total number of cards in the election, but no style info.
- Each county publishes their CVRs. These have identifiers that match with the manifest.
- Each county publishes their contest vote totals, but typically not their undervotes.

**Number of cards by contest**
- Each county may or may not publish their card totals (or equivilantly, their undervotes) by contest. I dont think theres any reason not to,
but so far Ive only seen Boulder county do so, in their "Statement of Votes" file. These are not in auditcenter data. 
- This is _Ncast_, the "number of cards cast" by contest. From Boulder SOV, Ncast = (totalVotes + totalUnderVotes) / voteForN.
- The unredacted CVRs give the authoritive count of Ncast, since each CVR is one card.
- SHANGRLA calls for a separate trusted upper limit for each contest = Nc. Colorado does not provide for this; 
we set Nc = Ncast + Nphantoms.

## Redaction

In what follows I outline my understanding of ways to do a style-based CLCA audit when there are redacted CVRS
in the public record. I am ignoring privacy implications for now.

**Definitions:**
- County vote totals by contest = CountyVote(contest)
- Contest "vote for n candidates" = voteForN(contest)
- Tabulation of the unredacted CVRS by contest = CVRtab(contest).
- Number of cards in the unredacted CVRS by contest = CVRncards(contest).
- Aggregation of the redacted CVRS by contest = REDtab(contest).
- Number of cards in the redacted CVRS by contest = REDcards(contest).

1. **Unredacted CVRs**: There are no redactions. Calculate Ncast from CVRs, phantoms = 0.

2. **Redacted CVRs are not in the published CVR file**
- The CVRs can't be used to verify the contest vote totals. 
- We can discover which manifest cards are redacted by comparing to the unredacted CVRs.
- We dont know Ncast, except that Ncast > CVRncards(contest) and Ncast > CVRtab(contest)/voteForN(contest) and 
  Ncast > CountyVote(contest)/voteForN(contest)

3. **Redacted CVRs are aggregated in the published CVR file, but no redacted card counts**
- CVRtab(contest) + REDtab(contest) = CountyVote(contest)
- We dont know Ncast, except that Ncast > CVRncards(contest) and Ncast > (CVRtab(contest) + REDtab(contest))/voteForN(contest)

4. **Redacted CVRs are aggregated in the published CVR file, and redacted card counts are also published**
- Ncast(contest) = CVRncards(contest) + REDncards(contest)
- Nundervotes(contest) = Ncast(contest) * voteForN(contest) - CountyVote(contest)
- Ncast(contest) could also be supplied directly in some other file. 

If we dont know Ncast(contest), I dont think we can do a statistically valid audit. 

A redacted CVR file with REDcards(contest) might look like:

````
...
CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,BallotType, ...
1,108,1,94,108-1-94,Regular,13,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,,1,,,,0,0,1,,1,1,1,0,0,1,1,1,,,,,,,,,,,,,,,,,,,,,,
2,108,1,93,108-1-93,Regular,13,1,0,1,0,1,1,0,1,1,0,0,0,0,1,0,,1,,,,0,1,0,,1,1,0,1,1,1,1,1,,,,,,,,,,,,,,,,,,,,,,
3,108,1,55,108-1-55,Regular,13,0,1,1,0,1,1,0,1,0,0,0,1,0,1,0,,1,,,,0,1,0,,1,1,0,1,1,1,1,1,,,,,,,,,,,,,,,,,,,,,,
...
Redacted Aggregation,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,, 7,18,17,10, 7,16,16,16,16,,, ...
Redacted Cards      ,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,55,55,55,55,22,22,50,50,50,,, ...
...
````
- Redacted Cards shows the number of cards aggregated for each contest (perhaps replicated across choice column for the contest).


## Redacted CVRs are phantoms 

In the above 4 cases, we dont have the identifiers of the redacted CVRs, nor do we have the card style
of the redacted CVRs. The simplest thing to do is to turn all redacted CVRs into phantoms. So for each contest,
add Ncast(contest) - CVRcards(contest) phantoms to the manifest. 

When a phantom is sampled, both the MVR and CVR are phantoms, and the CLCA assort value (aka bassort) is

````
    bassort = .5 * noerror

where noerror = `1.0 / (2.0 - margin / upper)` goes to 1/2 as the margin goes to 0
      upper = upper bound of assorter, e.g. = 1 for plurality assorter
````

Note that we miss the opportunity to discover genuinely missing cards (not in the unredacted CVR file).

## Redaction with a single OneAudit Pool

We can find the manifest cards that are not in the unredacted CVRs. We know that the redacted CVRs must be
contained in this set. If we assume that all these "missing manifest cards" are redacted, we could create a OneAudit
pool that contains them, and use the redacted aggregations as the pool subtotal. 

When a card in the pool is sampled, the CVR assort value = the pool average of the assorter. This card refers to a
card in the manifest, so it is found and hand audited as usual. The MVR has an
assort value of \[0,1/2,1] depending if the hand audit has a vote for the loser, other, or winner of the assertion.
The CLCA assort value is:

````
    bassort = (1-(CVR-MVR)/upper)*noerror 
            = [1-avg, 1.5-avg, 2-avg] * noerror (when upper=1)
````

Note that noerror depends on the margin of the contest over all counties, while the average pool value depends on
just the redacted cards in this county. So noerror > .5, while avg can be anything from 0 to 1. But typically these values 
are higher than 0.5, eg:

| avg | 1-avg | 1.5-avg | 2-avg |
|-----|-------|---------|-------|
| 0.4 | 0.6   | 1.1     | 1.6   |
| 0.5 | 0.5   | 1.0     | 1.5   |
| 0.6 | 0.4   | 0.9     | 1.4   |

and so the assertion will usually converge faster than when using phantoms.

However, cards in the pool have as "possible contests" all the contests that have a redacted CVR. This usually makes 
the contest sampling population larger, and so the margin is smaller. Whether this effect outweighs the advantage
over phantoms depends on the composition of the redacted cards.

Also, by assuming that the "missing manifest cards" are redacted, we miss the opportunity to discover genuinely missing cards
(not in the unredacted CVR file).


## Redaction by Card Style

Instead of creating a single redaction aggregation for all card styles, the redacted cards could be grouped by
card style, and seperate aggregations made for each style. OneAuditPool are made for each group and aggregation, so
that the contest sampling population stays as small as possible. However, we then need to know the identifiers of the
CVRs in each group, in order to assign them to the correct pool.

Note that we now have the opportunity to discover genuinely missing cards (not in the unredacted CVR file).

A redacted CVR file might look something like:

````
...
CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,BallotType, ...
1,108,1,94,108-1-94,Regular,13,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,,1,,,,0,0,1,,1,1,1,0,0,1,1,1,,,,,,,,,,,,,,,,,,,,,,
2,108,1,93,108-1-93,Regular,13,1,0,1,0,1,1,0,1,1,0,0,0,0,1,0,,1,,,,0,1,0,,1,1,0,1,1,1,1,1,,,,,,,,,,,,,,,,,,,,,,
3,108,1,55,108-1-55,Regular,13,0,1,1,0,1,1,0,1,0,0,0,1,0,1,0,,1,,,,0,1,0,,1,1,0,1,1,1,1,1,,,,,,,,,,,,,,,,,,,,,,
,,,,108-1-54,Redacted,10,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
...
,,,,111-19-12,Redacted,"01, 19, and 25",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
...
Redacted Aggregation,,,,,,10,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,, 7,18,17,10, 7,16,16,16,16,,,
Redacted Cards,,,,,,      10,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,55,55,55,55,22,22,50,50,50,,,
Redacted Aggregation,,,,,,"01, 19, and 25",8,10,15,13,5,12,5,17,9,4,4,1,8,5,3,9,8,,10,,,,,7,18,17,10,7,16,16,16,16,,,,,,,,,,,,,,,,,,,,,,
Redacted Cards,,,,,,      "01, 19, and 25",111,111,111,111,111,111,111,111,19,19,14,14,28,28,31,31,31,,10,,,,,70,70,70,70,70,16,16,16,16,,,,,,,,,,,,,,,,,,,,,,
...

````

- CVRs that are redacted have the id and style left in, but the votes removed
- "Redacted Aggregation" shows the style and the group tabulation
- "Redacted Cards" show the style and the ncards (replicated across choice column for the contest)
- Styles may be combined and given new names, as long as the redacted CVR style matches the aggregation style

This is somewhat similar to what Boulder County did in 2025 and 2026 primary, eg:

````
...
Redacted and Consolidated 18 Ballots,,,,,,"01, 19, and 25",8,10,15,13,5,12,5,17,9,4,4,1,8,5,3,9,8,,10,,,,,7,18,17,10,7,16,16,16,16,,,,,,,,,,,,,,,,,,,,,,
Redacted and Consolidated 23 Ballots,,,,,,"02, 20, 26, and STATE",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,17,15,2,0,12,11,0,0,18,18,8,14,15,8,5,,,8,,
Redacted and Consolidated 9 Ballots,,,,,,31 and STATE,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,5,4,,,,,,,,,,,,,,,,,,,,
Redacted and Consolidated 9 Ballots,,,,,,32,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,6,3
Redacted and Consolidated 9 Ballots,,,,,,STATE,7,2,,6,1,8,1,6,0,7,2,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
Redacted,,,,,,14,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,1,0,0,1,0,0,0,1,1,0,1,1,,1,,1,,,
Redacted,,,,,,08,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,1,1,0,0,0,0,0,0,1,0,0,,,0,,,,
Redacted,,,,,,11,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,1,0,0,0,1,0,0,0,0,1,0,0,,0,0,,,,
Redacted,,,,,,05,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,1,0,0,0,1,0,0,1,1,1,0,1,,,,,,,
...
````

- Aggregation done by style(s)
- The individual redacted CVRS have identifiers removed and votes left in (instead of the opposite)
- Redacted number of cards are not given

### Compare redaction methods

Using CorlaCountyElection

|              | ncontests | nredactions | ncards | ngroups | phantoms  | onePool   | styles | unredacted |
|--------------|-----------|-------------|--------|---------|-----------|-----------|--------|------------|
| Boulder2024  | 58        | 12127       | 396511 | 60      | 3523 (54) | 2116 (54) | 5439   | 1245       |
| Boulder2024  | 56        | 12127       | 396511 | 60      | 4356      | 1527      | 795    | 561        | 
| Boulder2026p | 11        | 2966        | 100423 | 21      | 1502 (9)  | 1175      | 725    | 432        |
| Boulder2026p | 10        | 2966        | 100423 | 21      | 2019      | 1227      | 613    | 425        |
| Morgan2026p  | 6         | 25          | 5220   | 1       | 192       | 211       | 169    | 168        |
| LaPlata2026p | 9         | 9           | 16146  | 1       | 299       | 284       | 284    | 284        |
| Weld2026p    | 12        | 20 (est)    | 69620  | 1       | 560       | *         | *      | 570        | 

where
  - _ncontests_ : number of contests successfuly audited
  - _nredactions_: number of redacted CVRs
  - _nrgroups_: number of redacted groups
  - _phantoms_: all redactions become phantoms
  - _onePool_: all redactions are placed in a single OneAudit pool
  - _styles_: redactions are placed in multiple OneAudit pools
  - _unredacted_: simulate audit without redactions

* Each result is a single trial. Multiple trials are needed to find average and variance of the distribution.
* In each contest, some of the contests are unauditable because number of phantoms > vote margin. For Boulder2023,
  phantoms variant was only able to audit 26 contests.

## IRV Redactions

IRV contest votes cannot be aggregated like Plurality contests. Further, IRV contest assertions cannot be formed without the
full set of ranked choices, and so an IRV audit cannot be done. However, the aggregated data needed for IRV has
a simple form, just the number of times a particular ranking was chosen, something like (for each contest):

count: rankings
123: 4 1 5 8
 42: 2 4 6 8
...

If any row with an IRV contest on it is aggregated, these counts from the redacted rows will have to be published, likely
in a seperate format from the CVRs.

## Example problem Boulder 2023, contest 9

````
Statement of Votes

id,                                    contestTitle     precinctCount, activeVoters, totalBallots,   totalVotes, totalUnderVotes, totalOverVotes, calcNc
9,    City of Louisville City Council Ward 2 (4-year term),        7,         9543,         3451,         3043,          406,            2,        3449,  

id,                                                         name,       Nc,   ncvrs,    diff
1,                           City of Boulder Council Candidates,     34248,   34248,       0
2,                    City of Lafayette City Council Candidates,     11356,   11356,       0
3,                                     City of Longmont - Mayor,     33606,   33606,       0
4,              City of Longmont - City Council Member At-Large,     33606,   33606,       0
5,                     City of Longmont - Council Member Ward 1,     10473,   10473,       0
6,                     City of Longmont - Council Member Ward 3,     12203,   12203,       0
7,              City of Louisville Mayor At-Large (4 Year Term),      8866,    8866,       0
8,         City of Louisville City Council Ward 1 (4-year term),      2634,    2634,       0
9,         City of Louisville City Council Ward 2 (4-year term),      3449,    2992,     457
...

````
there are only 2992 cvrs, but should be 3449, creating 457 phantoms. Note SOV has 406 undervotes.

The only redacted group with contest 9 is:

RedactedGroup('DS-11', ncards=67, nlines=1, minCards= 67 totalVotes=579 singleCards = false, contests=[7, 9, 11, 12, 13, 14, 21, 22, 23, 24, 33] )
assigned 67 cards using the "min cards needed for the votes shown" because undervotes arent given.

The unredacted CVRs have

`9: ContestTabulation(id=9 isIrv=false, voteForN=1, votes=[0=1389, 1=1185], nvotes=2574 ncards=2925, undervotes=351, novote=351, overvotes=0)`

So contest 8 has 2925 + 67 = 2992. missing 406 - 351 = 55 undervotes, so presume that there should have been 55 undervotes in group DS-11. Then
there would be 52+55 = 107 redacted cards for contest 9 instead of 67, so 107 - 67 = 40 cards are accounted for.

Where are the other 457 - 40 = 417 cards? Can only assume there are other errors in the CVR file.

