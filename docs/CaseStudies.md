# Case Studies
last changed 9/19/2025

While rlauxe is intended to be used in real elections, its primary use currently is to simulate elections for testing
RLA algorithms.

A real election requires human auditors to manually retrieve physical ballots and create MVRs (manual vote records). 
This is done in rounds; the number of ballots needed is estimated based on the contests' reported margins. 
At each round, the MVRS are typically entered into a spreadsheet, and the results are exported to a csv (comma separated value) file,
and copied into the Audit Record for processing.

Managing the MVRS is delegated to an MvrManager.
When testing, the MVRS are typically simulated by introducing "errors" on the corresponding CVRs. See MvrManagerTestFromRecord.
MvrManagerFromRecord is used for real elections.


## AuditRecord

    auditDir/
      auditConfig.json      // AuditConfigJson
      contests.json         // ContestsUnderAuditJson
      sortedCards.csv       // AuditableCardCsv (or)
      sortedCards.zip       // AuditableCardCsv
      ballotPool.csv        // BallotPoolCsv (OneAudit only)

      roundX/
        auditState.json     // AuditRoundJson
        samplePrns.json     // SamplePrnsJson // the sample prns to be audited
        sampleMvrs.csv      // AuditableCardCsv  // the mvrs used for the audit; matches samplePrns.json

For each round, the selected ballot prns are written into samplePrns.json in order. The mvrs are gathered or
simulated and written to sampleMvrs.csv.


## Cast Vote Records

We have several representations of CVRs:

### CvrExport

**CvrExport( val id: String, val group: Int, val votes: Map<Int, IntArray>))**

is an intermediate CVR representation for DominionCvrExportJson. Used by SanFrancisico and Corla.

Can use CardSortMerge to do out-of-memory sorting. From an Iterator\<CvrExport\>, convert to AuditableCard, assign prn, sort and write sortedCards.csv.


### AuditableCard

**AuditableCard( val location: String, val index: Int, val prn: Long, val phantom: Boolean, val contests: IntArray, val votes: List<IntArray>?, val poolId: Int? )**

is a serialization format for CVRs, written to a csv file.

### Cvr

**Cvr( val id: String, val votes: Map<Int, IntArray>, val phantom: Boolean, val poolId: Int?)**

is used by all the core routines.

### CardLocation

**CardLocation(  val location: String, val phantom: Boolean, val cardStyle: CardStyle?, val contestIds: List<Int>? = null)**

is used to make CardLocationManifest (aka Ballot Manifest), especially when there are no CVRs.

## Case Studies

### SanFranciscoCounty 2024

Input is in CVR_Export_20241202143051.zip. This contains the Dominion CVR_Export JSON files, as well as the
Contest Manifest, Candidate Manifest, and other manifests. We also have the San Francisco County summary.xml file from
their website for corroboration.

**createSfElectionFromCards/createSfElectionFromCardsOA/createSfElectionFromCardsOANS**: We read the CVR_Export files 
and write equivilent csv files in our own "AuditableCard" format to a temporary "cvrExport.csv" file.
We make the contests from the information in ContestManifest and CandidateManifest files,
and tabulate the votes from the cvrs. If its an IRV contest, we use the raire-java library to create the Raire assertions.
We write the auditConfig.json (which contains the prn seed) and contests.json files to the audit directory.

**createSortedCards**: Using the prn seed, we assign prns to all cvrs and rewrite the cvrs to sortedCards.csv (optionally zipped), using an out-of-memory
sorting algorithm.

The CVRs are in two groups, "mail-in" and "in-person". The _createSfElectionFromCardsOA_ variant assumes that the mail-in cvrs can be matched to the corresponding
physical ballot, but the in-person cannot. We can use OneAudit by putting the in-person cvrs into pools by precinct, since we can
then calculate the ContestTabulation and assortMean for each pool. For IRV, we can calculate the VoteConsolidator for each pool.
This allows us to calculate the RaireAssertions and assortMean for each pool. 
So we can run a real IRV, at the cost of increased sample sizes to use OneAudit instead of CLCA. 
Note that in this case we can use Card Style Data to do style sampling.

The _createSfElectionFromCards_ variant assumes we can match in-person CVRs to physical ballots, so theres no need to use OneAudit.
This allows us to compare the cost of OneAudit vs CLCA.

The _createSfElectionFromCardsOANS_ ("One Audit, no Styles") variant assumes we cannot match in-person CVRs to physical ballots, so uses OneAudit, 
but assumes that we dont know which cards have which contests for the pooled data. Instead it uses Philip's approach of 
adding contest undervotes to all the cards in the pool (so that all cards have all the contests), and increasing the contest upper limit (Nc). 
This allows us to test the two approaches. Preliminary results shows it may not matter much except at low margins.


### Colorado RLA (CORLA) 2024

* 3,241,120 ballot cast (Colorado 2024 General Election). 64 contests, no IRV.
* CO doesnt publically publish the CVRs, just precinct totals, see **2024GeneralPrecinctLevelResults.csv/zip/xlsx**.
* CORLA does an RLA, so they do have access to the CVRs.
* A "publically verifiable" RLA requires the CVRs to be publically verifiable. But we can still do the RLA as long as they
  are "privately available".

* _detail.xls_ has summary by contest broken out by county, in a multipage excel file. **detail.xml** has same info in xml file

* round1/contest.csv has summary of each round and we use these fields from it to make the contest:
````
  contest_name
  winners_allowed
  ballot_card_count
  contest_ballot_card_count
  winners
  min_margin
  risk_limit
  gamma
  optimistic_samples_to_audit
  estimated_samples_to_audit
````

Note that this gives us the number of samples estimated for each audit round, from the CORLA "super simple" algorithm. One can compare these estimates with the CORLA software's estimates (estimates can be seen in the Rlauxe Viewer AuditRoundsTable).

* createColoradoElectionFromDetailXmlAndPrecincts: contestRound, electionDetailXml, precinctResults -> precinctCvrs -> CvrExport.csv

* createCorla2024sortedCards: use CardSortMerge to convert to AuditableCard, assign prn, sort and write sortedCards (900 Mb, 120 Mb zipped)

The file data/2024audit/**targetedContests.xlsx** shows contests selected for audit, eg:

````
  "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
  "Colorado","Presidential Electors",1,"1,374,175","1,084,812","289,363",8.15%,3%,89,"2,554,611","Audited in all 64 counties",,,,,,,,,,,,,,,,1
````

However this doesnt agree with detail.xml, eg:
````
      Choice(key=1, text='Kamala D. Harris / Tim Walz', party='DEM', totalVotes=1728159, voteTypes=[VoteType(name='Total Votes', votes=1728159
      Choice(key=2, text='Donald J. Trump / JD Vance', party='REP', totalVotes=1377441, voteTypes=[VoteType(name='Total Votes', votes=1377441
````
Not sure why its different, but it looks like targetedContests.xlsx is wrong.

detail.xml does not have the total number of ballots for a contest, so we get that from ContestRound.contest_ballot_card_count eg:

````
contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
Presidential Electors,state_wide_contest,in_progress,1,4746866,3239722,"""Kamala D. Harris / Tim Walz""",350348,0.03000000,0,0,0,0,0,0,0,1.03905000,0,99,99
````
Not exactly consistent, eg 1728159 - 1377441 = 350718 != 350348, but close enough for now (we can only do a simulation since we dont have the real CVRs).

#### Next Steps

From "Next Steps for the Colorado Risk-Limiting Audit (CORLA) Program" (Mark Lindeman, Neal McBurnett, Kellie Ottoboni, Philip B. Stark. March 5, 2018):

    It is estimated that by June, 2018, 98.2% of active Colorado voters will be in CVR counties.

    First, the current version (1.1.0) of RLATool needs to be modified to recognize and group together contests that 
    cross jurisdictional boundaries; currently, it treats every contest as if it were entirely contained in a single county. 
    Margins and risk limits apply to entire contests, not to the portion of a contest included in a county. 
    RLATool also does not allow the user to select the sample size, nor does it directly allow an unstratified 
    random sample to be drawn across counties. 

    Second, to audit a contest that includes voters in “legacy” counties (counties with voting systems that cannot 
    export cast vote records) and voters in counties with newer systems, new statistical methods are needed to keep the 
    efficiency of ballot-level comparison audits that the newer systems afford. 

    Third, auditing contests that appear only on a subset of ballots can be made much more efficient if the sample can 
    be drawn from just those ballots that contain the contest. While allowing samples to be restricted to ballots 
    reported to contain a particular contest is not essential in the short run, it will be necessary eventually to 
    make it feasible to audit smaller contests.

As far as anyone can tell me, none of this work has been done. Colorado did hire Democracy Developers to add IRV RLA into
the CORLA software, to be used in 2025 Nov election.

* Points 1 and 3: would be best to replace CORLA with RLAUXE.
  * TODO investigate integrating RLAUXE into CORLA.
  * TODO investigate integrating RLAUXE into Dominion.
* On point 2, we can use OneAudit, and put each non-CVR county into a pool, as long as we know the undervotes.
  This is an evolution from the time the paper was written (2017), which was investigating statification.

We use the published precinct level results to create simulated CVRs and run simulated RLAs.
Note that we need the CVRs to do IRV contests.


### BoulderCounty 2024

````
DominionCvrExportCsv
CvrNumber, TabulatorNum, BatchId, RecordId, ImprintedId, CountingGroup, BallotType,
CastVoteRecord(cvrNumber: Int, tabulatorNum: Int, batchId: String, recordId: Int, imprintedId: String, ballotType: String)
cvrs have ballotType == cardStyle. So does redacted group, which mostly agree.

BoulderStatementOfVotes has
(2023R) "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","TotalVotes","TotalBlanks","TotalOvervotes","TotalExhausted"
(2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
(2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
````

_createBoulderElection_: BoulderStatementOfVotes, 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx

* we have the cvr undervotes, but not count or undervotes for the redacted votes
* mostly phantoms = 0 = totalBallots - totalVotes - totalUnderVotes - totalOverVotes
* a significant number of undervotes are missing. we will assume they are in the redacted batches.
* We calculate redactedUndervotes = totalUndervotes - cvrUndervotes, then distribute the redactedUndervotes across the groups in proportion to number of votes.

We cant do a OneAudit RLA unless we know the number of undervotes in each redacted group.
If we assume the ballotTypes are correct, and that all cards in the batch are of the ballotType, then we just need to know the ncards in each batch.

For now, we simulate the RLA by distributing redactedUndervotes across the groups in proportion to number of votes.
We create simulated cvrs by making random choices until the cvr sums equal the given batch totals, including undervotes.
We use these simulated cvrs as the mvs in the simulated RLA.

I dont see how we can handle IRV contests in the presence of redacted CVRs. There are lines called "RCV Redacted & Randomly Sorted", but they dont make much sense so far.
To do IRV with OneAudit you need to create VoteConsolidator for each pool from the real cvrs. 








