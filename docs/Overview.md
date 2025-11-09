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
        cardManifest.csv.zip  // AuditableCardCsv    cardManifst committment
        sortedCards.csv.zip   // AuditableCardCsv    sorted by prn
    
    private/
       sortedMvrs.csv.zip     // AuditableCardCsv, for tests only, sorted by prn
       testMvrs.csv.zip       // AuditableCardCsv, for tests only, test mvrs
    
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


## Audit Types and hasStyle

1. Physical card has location id that is recorded on the CVR. (CLCA)
   1. CVR has complete info (or references a card style). (hasStyle)
   2. CVR does not have undervotes, does not reference a card style. (noStyle) TODO

2. Some/all ballots are in pools where CVR does not exist or CVR id is not recorded on the physical card. (OneAudit)
   1. CVR exists and has complete info (or references a card style), but id not recorded. (hasStyle) 
      * If ncards > 1, and physical card location knows which card it is, divide pool so each has one card style. (SF OneAudit)
      * Otherwise, form the union of contests == pool card style. (SF OneAudit)
   2. CVR exists but does not record undervotes or reference a card style. (noStyle) TODO
   3. CVR does not exist, only Pool totals. Pool cardStyle is the Union of all contests in the precinct. (noStyle) (Boulder OneAudit?)

3. There are no CVRs (Polling)

    When polling, you can reduce the sample size by
    choosing ballots that contain the contests you want. So then the diluted margin comes back in when estimating.

   1. Theres only 1 pool of cards, unknow card styles. (noStyle)
   2. There are multiple pools and each pool has one card style. (hasStyle) (Boulder Polling?)
   3. There are multiple pools that can be used to narrow the population size. (OneAudit noStyle vs Polling?)

* In all variants, for each card, the list of possible contests that are on it is recorded (and made as tight as possible). Card
  styles can be factored out or not. The diluted margin of a contest is calculated by summing over the cards.

In ClcaAssorter.overstatementError(), hasStyle penalises MVR not having the contest, but noStyle treats it as a non-vote,
since you are sampling a population where you expect non-votes.

Otherwise, hasStyle only affects the calculation of the dilutedMargin.

When hasStyle, margin = dilutedMargin, else margin > dilutedMargin.
always use dilutedMargin when estimating the sample size.

Ballot vs Card : If the physical cards are stored and processed separately (common case), then everything is done with cards. 
If the cards are kept together, we can pretend the ballot is one card.
The only exception (possibly) is if the card style can be used in forming the CardManifest, see p.13 below.

From MoreStyle

p.6 (c = 1)
There are N ballots cast in the jurisdiction, of which N_B = N contain contest B and
N_S = p * N < N contain contest S, where p ∈ (0, 1).

The reported margin of contest B is M_B votes and the reported margin of contest S is M_S votes. 
Let m_B ≡ M_b /N_B and m_S ≡ M_S /N_S be the two _partially diluted margins_.

p.9 (c > 1)
Now suppose that each ballot consists of c > 1 cards. For simplicity, suppose that every
voter casts all c cards of their ballot. Contest B is on all N ballots and on N of the N * c cards.
Contest S is on N * p of the N * c cards.

fully diluted margin (noStyle) = 

    for B: M_B/(N*c) = (1/c) * m_B, B assumed to be on all ballots
    for S: M_S/(N*c) = (p/c) * m_S, p is proportion of ballot containing S

p. 13 (polling audit)

Suppose for each precinct, we know which ballots contain S but not which particular cards contain S, and
that the c cards comprising each ballot are kept in the same container. (This is an idealization
of precinct-based voting where each voter in a precinct gets the same ballot style and casts
all c cards of the ballot.) 

So we have a set of precinct pools, and when auditing S, we dont sample pools that dont contain S.

We can reduce the sampling universe for contest S from the original population of N * c cards to a smaller
population of p * N * c cards, of which p * N actually contain contest S.


Notes

1. Keep the CardLocationManifest -> CardManifest. May include a CardStyles record.
2. Add CardStyle reference to AuditableCard I think. Used when card style is used. Maybe "all" always returns contains(contest) = true ??
3. Pools are used for both OA and Polling.
4. Have to use diluted margin in estimation and audit, and in the ClcaAssorter..


Where is hasStyle used? 

ClcaAssorter.overstatementError()  // needed
ContestSimulation.makeBallotManifest()  // needed

MultiContestTestData ??
AuditRound estSampleSize else estSampleSizeNoStyles // maybe can get rid of estSampleSizeNoStyles?

not needed in:
ClcaWithoutReplacement
PollWithoutReplacement

======================

For use_style=true, the contest is sampled from just the cards claiming to contain that contest. If the MVR does not contain the contest, the overstatement_error() penalizes by assuming a vote for the loser.

For use_style=false, the contest is sampled from all cards that might contain that contest, and we use the fully diluted margin to account for this (and match the assorter mean). We expect some of the MVRs to not have the contest, so the MVR is considered to be a non-vote in the contest

For OneAudit, the contest is sampled from all CVRs that claim to have that contest, plus all the cards in pools that might contain the contest.

So when calculating the overstatement_error, use hasStyles=true if the card has a CVR, and hasStyles=false if the card is from a pool.

If using CSD, the Prover must commit to the CSD before the PRN is chosen.
A verifier needs to check that cards are chosen in order of smallest PRN, and satisfy the CSD if used.
