# Case Studies
_last changed 11/03/2025_

## SanFrancisco County 2024

* 1,603,908 cvrs
* 53 contests, 11 IRV
* 53 contests, 11 IRV
* 4224 pools with 216286 cards (13.5%), using SHANGRLA grouping. 
* Many pools have only a few cards, which may be problematic.

````
The election produced 1,603,908 CVRs, of which 216,286 were for cards
cast in 4,223 precinct batches and 1,387,622 CVRs were for vote-by-mail (VBM) cards.
````

Input is in _CVR_Export_20241202143051.zip_. This contains the Dominion CVR_Export JSON files, as well as the
Contest Manifest, Candidate Manifest, and other manifests. We also have the San Francisco County _summary.xml_ file from
their website for corroboration. The summary.xml ncards match the CVRS exactly, so there are no phantoms.

**CreateSfElection/CreateSfElectionNoStyles**: We read the CVR_Export files 
and write equivilent csv files in our own "AuditableCard" format to a temporary "cvrExport.csv" file.
We make the contests from the information in ContestManifest and CandidateManifest files,
and tabulate the votes from the cvrs. If its an IRV contest, we use the raire-java library to create the Raire assertions.
We write the auditConfig.json (which contains the prn seed) and contests.json files to the audit directory.

**createSortedCards**: Using the prn seed, we assign prns to all cvrs and rewrite the cvrs to sortedCards.csv (optionally zipped), using an out-of-memory
sorting algorithm.

The CVRs are in two groups, "mail-in" and "in-person".

**CreateSfElection(isClca = false) One Audit, hasStyle:** assumes we can match all CVRs to physical ballots, 
but the in-person (precinct) votes must be redacted and not available to be matched against the mvrs. 
We can use OneAudit by putting the in-person cvrs into pools by precinct, since we can
then calculate the ContestTabulation and assortMean for each pool. For IRV, we can calculate the VoteConsolidator for each pool.
This allows us to calculate the RaireAssertions and assortMean for each pool.
So we can run a real IRV, at the cost of increased sample sizes to use OneAudit instead of CLCA.
In this case we use Card Style Data to do style sampling, which is equivilent to assuming we can match the CRV to the MRV,
but the in-person crv vote counts have been redacted for privacy reasons.

**CreateSfElection(isClca = true) CLCA, hasStyle:** assumes we can match all CVRs to physical ballots, so we can do a regular CLCA.
This allows us to compare the cost of OneAudit vs CLCA.

**CreateSfElectionPoolStyle(isPolling = false)_ One Audit, noStyle** assumes we cannot match precinct CVRs to physical ballots, 
so uses OneAudit, and assumes that we dont know which cards have which contests for the pooled data. Instead it uses Philip's approach of 
adding contest undervotes to all the cards in the pool (so that all cards have all the contests in the pool), and increasing the contest upper limit (Nc). 
Consistent sampling is still used, but with increased undervotes in the pool ballots.

**CreateSfElectionPoolStyle(isPolling = true) One Audit, noStyle:** assumes we cannot match any CVRs to physical ballots,
so uses Polling. It is assumed that for each precinct, the set of possible contests for that precinct is known.
When creating the CardManifest, for each precinct, every cvr gets that list of contests on it. Then all the cards are read and for 
each contest, the total number of ballots that may contain the contest is tabulated. This is Nb for that contest.
Consistent sampling can still be used, but the estimated ballots needed for each contest are scaled by Nb/Nc >= 1.

## Colorado RLA (CORLA) 2024

* 3,241,120 ballot cast (Colorado 2024 General Election) in 3199 precincts.
* 260 contests, no IRV.
* CO doesnt publically publish the CVRs, just precinct totals, see _2024GeneralPrecinctLevelResults.csv/zip/xlsx_. 
* CORLA does an RLA, so they do have access to the CVRs. A "publically verifiable" RLA requires the CVRs to be publically verifiable. But we can still do the RLA as long as they are "privately available".
* The _detail.xls_ file has summary by contest broken out by county, in a multipage excel file. _detail.xml_ has same info in xml file
* The _round1/contest.csv_ file has a summary of each round; we use these fields from it to make the contest:
````
  contest_name
  winners_allowed
  ballot_card_count
  contest_ballot_card_count
  winners
  min_margin
  risk_limit
  optimistic_samples_to_audit
  estimated_samples_to_audit
````

Note that this gives us the number of samples estimated for each audit round, from the CORLA "super simple" algorithm. We can compare these estimates with the CORLA software's estimates (estimates can be seen in Rlauxe Viewer _AuditRoundsTable_).

There are 725 contests listed on round1/contest.csv. There are 295 listed in detail.xml. I was told they dont have precinct data (or CVRs?) for contests \>= 260. So we restrict our attention to those 260 contests.


The file corla/2024audit/_targetedContests.xlsx_ shows contests selected for audit, eg:

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

### simulated CVRs for CLCA audit

We use the published precinct level results to create simulated CVRs and run simulated RLAs. Note that we need CVRs to do IRV contests, so we cant handle
  IRV contests.

**ColoradoOneAudit(isClca = true) CLCA, hasStyle:** assumes we can match the CVRs to physical ballots and does a regular CLCA.
This allows us to compare the cost of OneAudit vs CLCA.

### precinct pools for OneAudit

We also run a OneAudit with the precinct as the pools. 

* They do record when a Candidate total votes = 0.
* We have Nc from contestRound.contestBallotCardCount
* We dont have the number of cards for each precinct, so we add undervotes to minimize phantoms.

**ColoradoOneAudit(isClca = false) One Audit, hasStyle:** has all ballots in OneAudit pools by precinct.
Assume that the contest list constitutes the ballot style for that precinct pool.
In this case we use Card Style Data to do style sampling, which is equivilent to assuming we can match the CardLocations to the MRV,
but there are no votes.

### precinct styles for Polling audit

**ColoradoOneAuditPolling() Polling, noStyle:** assume that the precinct contest list constitutes the ballot style for that precinct pool.
When creating the CardManifest, for each precinct, every cvr gets that list of contests on it. Then all the cards are read and for
each contest, the total number of ballots that may contain the contest is tabulated. This is Nb - Np for that contest.
Consistent sampling is used, but the estimated ballots needed for each contest are scaled by Nb/Nc >= 1.

Because every ballot is in a pool, Nb = Nc. Not too interesting.

### Next Steps

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
  This is an evolution from the time the paper was written (2017), before OneAudit was developed.

## Boulder County 2024

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

* Both the cvrs and the redacted pool totals reference a BallotType, which can be used as the Card Style Data.
* We are not given the count of ballots or undervotes in the redacted pools.
* The StatementOfVotes gives us enough information to calculate Nc.
* We estimate the undervotes and pool counts as explained below, and adjust the contest Nc to be consistest with the card manifest.

**createBoulderElection(isClca = false) OneAudit, hasStyle** create a OneAudit by assuming that each pool has a single CardStyle, which allows us to 
use style based sampling. This constrains the way that undervotes are added to the pools. We need to know the number of cards in each batch. We dont,
so we approximate it, and adjust Nc. 

**createBoulderElection(isClca = true)  CLCA, hasStyle:** creates a CLCA by simulating cvrs in each pool and adding to the regular cvrs.
This allows us to characterize the pool variances.

Its not possible to run an IRV audit with redacted CVRs. There are lines called "RCV Redacted & Randomly Sorted", but they dont make much sense so far. To do IRV with OneAudit you need to create VoteConsolidator for each pool from the real cvrs.

### Calculating the redacted undervotes and pool counts

We have two source of information: **sovo** is Boulder's StatementOfVotes, and DominionCvrExportCsv contain the CVRs and
the redacted pool counts.

* For most contests, phantoms = 0 = sovoContest.totalBallots - ((sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes)
* We can calculate missing undervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN - cvr.undervotes.
* We assume missing undervotes are in the redacted pools.
* We assume all ballots in each pool contain the same set of contests, called the Ballot Style. In that case, we just need to
  estimate the number of cards for each pool, that gives the correct number of undervotes.
* We create the Ballot Manifest with the published CVRS, and entries for each card in the pools, which contain CSD and pool ids,
but no vote information.

Some of these assumptions may not be right, but they are sufficient for our purposes. In a real audit, we would to work with the EA to track each down.
We would advocate that the number of cards in each pool be published.

### Corrections to StatementOfVotes

On contest 20, sovo.totalVotes and sovo.totalBallots is wrong vs the cvrs. (only one where voteForN=3, but may not be related)

````
'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes
sovoContest=Town of Superior - Trustee, 7, 9628, 8254, 16417, 8246, 33
cvrTabulation={0=3121, 1=3332, 2=3421, 3=2097, 4=805, 5=657, 6=3137} nvotes=16570 ncards=7865 undervotes=7025 overvotes=0 novote=1484 underPct= 29%
redTabulation={0=130, 1=87, 2=111, 3=50, 4=25, 5=36, 6=101} nvotes=540 ncards=180 undervotes=0 overvotes=0 novote=0 underPct= 0%
  sovoCards= 8254 = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
  phantoms= 0  = sovoContest.totalBallots - sovoCards
  sovoUndervotes= 8345 = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
  cvrUndervotes= 7025
  redUndervotes= 1320  = sovoUndervotes - cvr.undervotes
  redVotes= 540 = redacted.votes.map { it.value }.sum()
  redNcards= 620 = (redVotes + redUndervotes) / info.voteForN
  totalCards= 8485 = redNcards + cvr.ncards
  diff= -231 = sovoContest.totalBallots - totalCards
````
Assume sovo.totalBallots is wrong, so Nc = max(totalCards, sovoContest.totalBallots)

### Corrections to Redacted Ballots

We assume all ballots in each pool contain the same set of contests, called the Ballot Style. Seems true except for
RedactedGroup '06, 33, & 36-A'. We can compare the BallotStyle for CV RS vs the redacted group:

````
RedactedGroup=[0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42]
         CVRS=[0, 1, 2, 3, 5, 10, 11, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42]
````

Note that contest 12 is in RedactedGroup but not CVRs. Its value in the RedactedGroup is zero, so we assume that it is a mistake to be there.
This matters for the undervote count of that contest.






