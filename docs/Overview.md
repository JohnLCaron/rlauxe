# Rlauxe Implementation Overview
_last changed 10/22/2025_

While Rlauxe is intended to be used in real elections, its primary use currently is to simulate elections for testing
RLA algorithms.

All audits require a ballot manifest that has a complete list of physical ballots, augmented with phantom ballots as needed.
Rlauxe represents this as a list of _AuditableCards_, or _cards_ for short. Each card has a location which allows the
auditor to locate the physical ballot. The ordered list of cards is committed to (publically recorded) before the random seed is
chosen. Rlauxe stores the ordered list of cards in _cardManifest.csv_. After the random seed is selected, the 
PRG (pseudo random generator) assigns a prn (pseudo random number) to each card in canonical order.
Rlauxe sorts the cards by prn and stores them in _sortedCards.csv_.

In Rlauxe, at a minimum each card has a location, an index in the canonical order, and a prn. Optionally it may contain the list
of contests that are on the CVR, and optionally it may contain the list of candidate votes for each contest on the CVR. If the list of contests
is complete, we may do an "audit with style". If the card contains the candidate votes, this constitutes the CVR, and we may do a CLCA audit.

A real election requires human auditors to manually retrieve physical ballots and create MVRs (manual vote records).
This is done in rounds; the number of ballots needed is estimated based on the contests' reported margins.
At each round, the MVRS are typically entered into a spreadsheet, and the results are exported
and copied into the Audit Record for processing.

For testing, we simulate the MVRs and place them into auditDir/private/testMvrs.csv. For a real audit, we might still use simulated
MVRs for estimating sample sizes, but obviously we would only use real MVRs for the actual audit.

Each audit type has specialized processing for creating the AuditableCards and the test Mvrs:

1. **CLCA audit**: we have the full set of CVRs which we use to generate the AuditableCards.
   We can optionally fuzz the CVRS to simulate a desired error rate, and use those fuzzed CVRS as the test MVRs.

2. **OneAudit Pools**: we have some CVRs and some pooled data. For each pool, we create simulated CVRs that exactly match the pool
   vote count, ncards and undervotes. We combine the two types of CVRS (with optional fuzzing), to use as the test MVRs.

    * **CardPoolWithBallotStyle**: Each pool has a given Ballot Style, meaning that each card in the pool has the same
      list of contests on the ballot. The AuditableCard then has a complete contest list but no votes. This allows us to run a OneAudit with styles=true.
      This is currently the situation for Boulder County (see below). IRV contests cannot be audited unless VoteConsolidations
      are given.

    * **CardPoolFromCvrs**: Each card pool has a complete set of CVRs which can be matched to the physical ballots. For privacy reasons,
      the actual vote on each card cannot be published. The card has a complete contest list but the votes are redacted.
      This allows us to run a OneAudit with styles=true. We can use the CVRs for the test MVRs, since these are not published.

3. **OneAudit unmatched CVRs**: Each card pool has a complete set of CVRs, but the CVRS in the pools cannot be matched to the
   physical ballots. The physical ballots are kept in some kind of ordered "pile".  The card location is the name of the pool and the
   index into the pile (or equivilent). This is currently the situation for SanFrancisco County (see below), where each precinct generates CVRs but does not
   associate them with the physical ballot. We can use OneAudit rather than Polling, which performs better when the number of unmatched
   cards is not too high.

    * **CardPoolNoBallotStyle**: The cards in each pool may have different Ballot Style. For each pool, scan the CVRs for that pool
      and form the union of the contests. This union is the psuedo ballot style for the pool, and is added to the list of contests on the AuditableCard.
      Scan the CVRS again and for each contest,
      count the number of cards that do not have that contest. Add that count to the contest maximum number of cards (Nc) and
      adjust the margin accordingly. This allows us to run a OneAudit with styles=true, with the adjusted margins.
      We can use the pooled CVRs as the test MVRs.

    * **CardPoolWithBallotStyle**: If the cards in each pool all have the same Ballot Style, the above algorithm reduces to running
      OneAudit with styles=true, where the margin adjustment is zero.

4. **Polling audit**: we create simulated CVRs that exactly match the reported vote count, ncards and undervotes to use as the test MVRs.


## AuditRecord

    $auditdir/
        auditConfig.json      // AuditConfigJson
        cardPools.json        // CardPoolJson (OneAudit only)
        contests.json         // ContestsUnderAuditJson
        cardManifest.csv.zip  // AuditableCardCsv 
        sortedCards.csv.zip   // AuditableCardCsv 
    
    private/
       sortedMvrs.csv.zip     // AuditableCardCsv, for tests only
       testMvrs.csv.zip       // AuditableCardCsv, for tests only
    
    roundX/
        auditState.json     // AuditRoundJson
        samplePrns.json     // SamplePrnsJson, the sample prns to be audited
        sampleMvrs.csv      // AuditableCardCsv, the mvrs used for the audit; matches samplePrns.csv

For each round, the selected ballot prns are written into samplePrns.json in order. The mvrs are gathered or
simulated and written to sampleMvrs.csv.


## Cast Vote Records

There are several representations of CVRs:

**CvrExport( val id: String, val group: Int, val votes: Map<Int, IntArray>))**

is an intermediate representation for DominionCvrExportJson. It serializes compactly to CvrExportCsv. Used by SanFrancisico and Corla.

Use CardSortMerge to do out-of-memory sorting: using an Iterator\<CvrExport\>, this converts to AuditableCard, assigns prn, sorts and writes
to _sortedCards.csv_.

**AuditableCard( val location: String, val index: Int, val prn: Long, val phantom: Boolean, val contests: IntArray, val votes: List<IntArray>?, val poolId: Int? )**

is a serialization format for both CVRs and CardLocations, written to a csv file. Optionally zipped. Rlauxe can read from the zipped file directly.
Note that _votes_ may be null, which is used for for polling audits and for pooled data with no CVRs.

**Cvr( val id: String, val votes: Map<Int, IntArray>, val phantom: Boolean, val poolId: Int?)**

is the core abstraction, used by assorters and all the core routines. It represents both CVRs and MVRs.

**CardLocation(val location: String, val phantom: Boolean, val cardStyle: CardStyle?, val contestIds: List<Int>? = null)**

is used to make the CardLocationManifest (aka Ballot Manifest), especially when there are no CVRs.

## Audit Workflow

An audit is performed in _rounds_, as outlined here:

For each contest:
- Count the votes in the usual way. The reported winner(s) and the reported margins are based on this vote count.
- Determine the total number of valid ballots, including undervotes and overvotes (Nc).
- For a Card Level Comparison Audit (CLCA), extract the Cast Vote Records (CVRs) from the vote tabulation system.

For the election:
- Create a Card Manifest (aka Ballot Manifest), in which every physical ballot has a unique entry. If this is a CLCA, attach the
  Cvr to its CardLocation.
- If necessary, add phantoms to the Card Manifest following SHANGRLA section 3.4.
- Write the Card Manifest to cardManifest.csv.zip

- initialize the audit by choosing the contests to be audited, the risk limit, and the random seed.
- sort the Card Manifest by prn and wrte to to cardManifest.csv.zip.


The purpose of the audit is to determine whether the reported winner(s) are correct, to within the chosen risk limit.
Contests are removed from the audit if:
    - The contest has no losers (e.g. the number of candidate <= number of winners); the contest is marked NoLosers.
    - The contest has no winners (e.g. no candidates receive minFraction of the votes in a SUPERMAJORITY contest); the contest is marked NoWinners.
    - The contest is a tie, or its reported margin is less than _auditConfig.minMargin_; the contest is marked MinMargin.
    - The contest's reported margin is less than its phantomPct (Np/Nc); the audit is marked TooManyPhantoms.
    - The contest internal fields are inconsistent; the audit is marked ContestMisformed.
    - The contest is manually removed by the Auditors; the audit is marked AuditorRemoved.

For each audit round:
1. _Estimation_: for each contest, estimate how many samples are needed to satisfy the risk function,
   by running simulations of the contest with its votes and margins, and an estimate of the error rates.
2. _Choosing sample sizes_: the Auditor decides which contests and how many samples will be audited.
   This may be done with an automated algorithm, or the Auditor may make individual contest choices.
3. _Random sampling_: The actual ballots to be sampled are selected from the sorted Manifest until the sample size is satisfied.
4. _Manual Audit_: find the chosen paper ballots that were selected to audit and do a manual audit of each.
5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
6. _Run the audit_: For each contest, calculate if the risk limit is satisfied, based on the manual audits.
7. _Decide on Next Round_: for each contest not satisfied, decide whether to continue to another round, or call for a hand recount.