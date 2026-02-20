# Boulder County 2024
02/20/2026

## Downloaded files

These files have already been downloaded into cases/src/test/data/Boulder2024 and converted to csv files by reading into 
Libre Office and exporting to csv.

From: https://bouldercounty.gov/elections/results/

    https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Statement-of-Votes.xlsx
    https://assets.bouldercounty.gov/wp-content/uploads/2025/01/2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx
    
    https://assets.bouldercounty.gov/wp-content/uploads/2024/12/2024G-Boulder-County-Amended-Statement-of-Votes.xlsx
    https://assets.bouldercounty.gov/wp-content/uploads/2025/01/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.xlsx

## Generating the election

Using _cases/src/test/kotlin/org/cryptobiotic/rlauxe/util/TestGenerateAllUseCases.kt_:

* run createBoulder24oa() to create a OneAudit elction in  _$testdataDir/cases/boulder24/oa/audit_
* run createBoulder24clca() to create a CLCA elction in  _$testdataDir/cases/boulder24/clca/audit_

### Boulder election notes:

The XXX_Cast-Vote-Record.xlsx files may be a standard "export to excel" function from Dominion. The CvrExport_xxxxx.json files are not available on the website. These are read by readDominionCvrExportCsv().

The XXX_Statement-of-Votes.xlsx files may be in a bespoke format specific to Boulder County. Each election Ive looked at is slightly different. These
are read by readBoulderStatementOfVotes(), which uses the filename to choose the variation.

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

## Boulder2024 characteristics

* Both the cvrs and the redacted pool totals reference a BallotType, which can be used as the Card Style Data.
* We are not given the count of ballots or undervotes in the redacted pools.
* The StatementOfVotes gives us enough information to calculate Nc.
* We estimate the undervotes and pool counts as explained below, and adjust the contest Nc to be consistest with the card manifest.

**createBoulder24oa()** creates a OneAudit by assuming that each pool has a single CardStyle, which allows us to
use style based sampling. This constrains the way that undervotes are added to the pools. We need to know the number of cards in each batch. We dont,
so we approximate it, and adjust Nc.

**createBoulder24clca():** creates a CLCA by simulating cvrs in each pool and adding to the regular cvrs.
This allows us to characterize how OneAudit and CLCA compare.

Its not possible to run an IRV audit with redacted CVRs. There are lines called "RCV Redacted & Randomly Sorted", but they dont make much sense so far. To do IRV with OneAudit you need to create a VoteConsolidator for each pool from the real cvrs.

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

Some of these assumptions may not be right, but they are sufficient for our purposes. In a real audit, we would work with the EA to track each down.
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




