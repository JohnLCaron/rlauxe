# Use Cases
last changed: 9/16/2025

core
  CvrExport    ( val id: String, val group: Int, val votes: Map<Int, IntArray>))
  AuditableCard( val location: String, val index: Int, val prn: Long, val phantom: Boolean, val contests: IntArray, val votes: List<IntArray>?, val poolId: Int? )
  Cvr          ( val id: String, val votes: Map<Int, IntArray>, val phantom: Boolean, val poolId: Int?)

  CardSortMerge: CvrExport -> AuditableCard (sorted)

Also have (but not used yet):
  CardLocation(  val location: String, val phantom: Boolean, val cardStyle: CardStyle?, val contestIds: List<Int>? = null)


# SanFranciscoCounty
  DominionCvrExportJson
  CVR_Export_20241202143051.zip -> CvrExport_xxxx and manifests -> DominionCvrSummary -> CvrExport.csv

The CVRs are in two groups, "mail-in" and "in-person". Its assumed that the mail-in cvrs can be matched to the corresponding
physical ballot, but the in-person cannot. We can use OneAudit by putting the in-person cvrs into pools by precinct, since we can
then calculate the ContestTabulation and assortMean for each pool. For IRV, we can calculate the VoteConsolidator for each pool. 
This allows us to calculate the RaireAssertions and assortMean for each pool.

So we can run a real IRV, at the cost of increased sammple sizes to use OneAudit instead of CLCA.

Note that in this case we can use Card Style Data to do style sampling. 
TODO compare to Philips approach of adding contest undervotes. Preliminary results shows it may net matter much except at 
low margins.
TODO get a python programmer who can do a better job of comparing RLAUXE directly to SHANGRLA code.

# Colorado RLA (CORLA)

* 3,241,120 ballot cast (Colorado 2024 General Election). 64 contests, IRV excluded or none?.
* CO doesnt publically publish the CVRs, just precinct totals, see 2024GeneralPrecinctLevelResults.csv/zip/xlsx. 
* CORLA does an RLA, so they do have access to the CVRs.
* A "publically verifiable" RLA requires the CVRs to be publically verifiable. But can still do the RLA as long as they
are "privately available".

* detail.xls has summary by contest broken out by county, in a multipage excel file
* detail.xml has same info in xml file
* round1/contest.csv has summary of each round and we use these fields from it to make the contest: 
    
    `contest_name,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,gamma,optimistic_samples_to_audit,estimated_samples_to_audit`
  
Note that this gives us the number of samples used in each audit round, from the CORLA software. TODO compare with our estimates

* createColoradoElectionFromDetailXmlAndPrecincts: contestRound, electionDetailXml, precinctResults -> precinctCvrs -> CvrExport.csv
* createCorla2024sortedCards: use CardSortMerge to convert to AuditableCard, assign prn, sort and write sortedCards (900 Mb, 120 Mb zipped)

From "Next Steps for the Colorado Risk-Limiting Audit (CORLA) Program" (Mark Lindeman, Neal McBurnett, Kellie Ottoboni, Philip B. Stark. March 5, 2018):

    It is estimated that by June, 2018, 98.2% of active Colorado voters will be in CVR counties.

    First, the current version (1.1.0) of RLATool needs to be modified to recognize and group together contests that cross jurisdictional boundaries; currently, it treats every contest as if it were entirely contained in a single county. Margins and risk limits apply to entire contests, not to the portion of a contest included in a county. RLATool also does not allow the user to select the sample size, nor does it directly allow an unstratified random sample to be drawn across counties. 

    Second, to audit a contest that includes voters in “legacy” counties (counties with voting systems that cannot export cast vote records) and voters in counties with newer systems, new statistical methods are needed to keep the efficiency of ballot-level comparison audits that the newer systems afford. 

    Third, auditing contests that appear only on a subset of ballots can be made much more efficient if the sample can be drawn from just those ballots that contain the contest. While allowing samples to be restricted to ballots reported to contain a particular contest is not essential in the short run, it will be necessary eventually to make it feasible to audit smaller contests.

* Points 1 and 3: would be best to replace CORLA with RLAUXE. 
* TODO investigate integrating RLAUXE into CORLA.
* TODO investigate integrating RLAUXE into Dominion.
* On point 2, we can use OneAudit, and put each non-CVR county into a pool, as long as we know the undervotes. 
This is an evolution from the time the paper was written (2017), which was investigating statification.

We can use the published precinct level results to create simulated CVRs and run simulated RLAs.
* Need CVRs to do IRV contests.
* TODO: show simulated results


## BoulderCounty
  DominionCvrExportCsv
    CvrNumber, TabulatorNum, BatchId, RecordId, ImprintedId, CountingGroup, BallotType,
    CastVoteRecord(cvrNumber: Int, tabulatorNum: Int, batchId: String, recordId: Int, imprintedId: String, ballotType: String)
    cvrs have ballotType == cardStyle. So does redacted group, which mostly agree.

  BoulderStatementOfVotes has
    (2023R) "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","TotalVotes","TotalBlanks","TotalOvervotes","TotalExhausted"
    (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
    (2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"

  createBoulderElection: BoulderStatementOfVotes, 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx
  we have the cvr undervotes, but not count or undervotes for the redacted votes

  mostly phantoms = 0 = totalBallots - totalVotes - totalUnderVotes - totalOverVotes
  significant number of undervotes are missing. we can assume they are in the redacted batches.
  We can calculate redactedUndervotes = totalUndervotes - cvrUndervotes
  then distribute the redactedUndervotes across the groups in proportion to number of votes

 we cant do a OneAudit RLA unless we know the number of undervotes in each redacted group.
 if we assume the ballotTypes are correct, and that all cards in the batch are of the ballotType, then we can just need to know the ncards in each batch.

 for now, we simulate the RLA by distributing redactedUndervotes across the groups in proportion to number of votes.
 we create simulated cvrs by making random choices until the cvr sums equal the given batch totals, including undervotes.
 we use these simulated cvrs as the mvs in the simulated RLA.

I dont see how we can handle IRV contests in the presence of redacted CVRs. There are lines called "RCV Redacted & Randomly Sorted", but they dont make much sense.
To do IRV with OneAudit you need to create VoteConsolidator for each pool from the real cvrs. 



