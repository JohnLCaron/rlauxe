# Rlauxe Implementation Overview
_last changed 12/20/2025_

While Rlauxe is intended to be used in real elections, its primary use currently is to simulate elections for testing
RLA algorithms.

A real audit requires human auditors to manually retrieve physical ballots and create MVRs (manual vote records).
This is done in rounds; the number of ballots needed is estimated based on the contests' reported margins.
At each round, the list of ballots are chosen and given to the human auditors for retrieval.
The sampled MVRs might be entered into a spreadsheet, and the results exported and copied into the Audit Record,
which is the "persisted record" for the audit.

The ballot counting software may produce a digital record for each scanned ballot, called the CVR (Cast Vote Record),
used for CLCA and OneAudit type audits. If not, then only a Polling audit can be done.

A simulated audit uses simulated data for the MVRs. It may use real election data, or simulated data, for the CVRs.
It may use rounds or do the entire audit in one round. It may write an Audit Record or not.

All the information on a physical ballot might be scanned to a single CVR and all the parts of the ballot stored together.
Or, a physical ballot might consist of multiple ballot cards that are each scanned to a seperate CVR, and stored independently of one another. 
While the first case makes for a more efficient audit in terms of the number of samples needed, rlauxe handles both cases. 
For convenience we will use the term "_physical card_" to refer to either a single card or the whole ballot (when the cards stay together). 
The essential point is that one card means a single CVR and a single location identifier that allows it to 
be retrieved for the audit. 

## The Card Manifest

All audits require a "_card manifest_" that has a complete list of the physical cards for the election.
Rlauxe represents this as a list of _AuditableCards_, or _cards_ for short. Each card has a location which allows the
auditor to locate the physical card. The ordered list of cards is "committed to" (publically recorded) before the random 
seed is chosen. After the random seed is selected, the PRG (pseudo random generator) assigns a prn (pseudo random number) 
to each card in canonical order. Rlauxe sorts the cards by prn and stores them in _sortedCards.csv_, from which the
samples are drawn.

Each contest has a "trusted upper bound" (Nupper) on the number of cards that contain it, derived independently of the election software.
The election software must supply what it thinks is the "number of votes cast" (Ncast) for the contest. It must have entries in
the card manifest for each vote cast. When Ncast < Nupper, then (Nupper - Ncast) "phantom cards" are added to the manifest. In this way
we have accounted for all possible ballots for the contest, so that we are sampling over the entire population of ballots
containing the contest.

A sorted card has a location, an index in the canonical order, and a prn. It also contains the CVR when that exists.
It also contains the list of "possible contests" that may be on the card. When it contains a CVR that includes undervotes (contests 
that werent voted on), then the contests on the CVR consitute the possible contests. There are many other ways that
the election authorities might know which contests might be on each card. It is the responsibility of the election software to
correctly add this information.

For each contest audit, the card manifest is read in sorted order, and the first cards that may contain the contest are taken as the 
cards to be sampled. The more accurate the cards are, the more efficient the audit will be, since you wont be wasting time
auditing cards that dont contain the contest.

The _population size_ (Npop) for a contest is the count of the cards in the cardManifest that may have that contest on it. This
number is used as the denominator when calculating the _diluted margin_ for the contest's assertions. A smaller denominator
makes a bigger margin, so again the more accurate the cards are, the more efficient the audit will be.

Note that Npop is independent of Nupper and Ncast. It differs when we dont know exactly which cards contain the contest.
In general, Npop >= Nupper >= Ncast.

When all the cards in the manifest have "possible contests" that equal the actual contests on the physical card, 
then Npop == Nupper and the audit's hasStyle flag is set to true. This will be true, for example, when we have complete CVRs, 
in a one contest election, if the physical cards are kept in batches with a single card style, or other cases.

See Sample Population for more details.

## The AuditRecord

    $auditdir/
        auditConfig.json      // AuditConfigJson
        auditSeed.json        // PrnJson
        cardManifest.csv      // AuditableCardCsv, may be zipped
        cardPools.json        // CardPoolJson (OneAudit only)
        contests.json         // ContestsUnderAuditJson
        sortedCards.csv       // AuditableCardCsv, sorted by prn, may be zipped
        
        roundX/
            auditStateX.json     // AuditRoundJson,  the state of the audit for this round
            sampleCardsX.csv     // AuditableCardCsv, complete cards used for this round; matches samplePrnsX.csv
            sampleMvrsX.csv      // AuditableCardCsv, complete mvrs used for this round; matches samplePrnsX.csv
            samplePrnsX.json     // SamplePrnsJson, complete sample prns for this round, in order

### Commitment Sequence

At each stage, the AuditRecord is zipped, digitally signed and published publically

1. Pre-audit

        auditConfig.json      
        cardManifest.csv      
        cardPools.json        // (OneAudit only)
        contests.json

2. PRN chosen
        
        auditSeed.json
        sortedCards.csv   // sorted by prn

3. Audit Round X

For each round, the selected ballot prns are written into samplePrnsX.json in order. The mvrs are gathered or
simulated and written to sampleMvrsX.csv. The matching cards are written to sampleCardsX.csv, for completeness and security.

   3.1 sample estimation

        roundX/
            auditStateX.json     // the state of the audit with sample estimation
            samplePrnsX.json     // complete sample prns for this round

   3.2 audit results

        roundX/
            auditStateX.json     // replace the state of the audit with sample estimation and audit results
            sampleCardsX.csv     // complete cards used for this round; matches samplePrnsX.csv
            sampleMvrsX.csv      // complete mvrs used for this round; matches samplePrnsX.csv

4. Private data for testing and simulation only, not part of the public record

        $auditdir/private/  
           sortedMvrs.csv       // AuditableCardCsv, sorted by prn, matches sortedCards.csv, may be zipped


## Creating a Persistent Audit 

1.  Pre-audit committment

implement CreateElectionIF and call CreateAudit

        class CreateAudit(val name: String, val topdir: String, val config: AuditConfig, election: CreateElectionIF, auditdir: String? = null, clear: Boolean = true) {

        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        writeAuditableCardCsvFile(election.cardManifest(), publisher.cardManifestFile())
        writeCardPoolsJsonFile(election.cardPools(), publisher.cardPoolsFile())

        val contestsUA = election.contestsUA()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        writeContestsJsonFile(contestsUA, publisher.contestsFile())


    interface CreateElectionIF {
        fun contestsUA(): List<ContestUnderAudit>
        fun cardPools(): List<CardPoolIF>? // only if OneAudit
    
        // if you immediately write to disk, you only need one pass through the iterator
        fun cardManifest() : CloseableIterator<AuditableCard>
    }

2. PRN chosen

        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed) // TODO use configSeed.json

3. 
3.1 sample estimation for round X

          abstract class AuditWorkflow {
              abstract fun auditConfig() : AuditConfig
              abstract fun mvrManager() : MvrManager
              abstract fun auditRounds(): MutableList<AuditRound>
              abstract fun contestsUA(): List<ContestUnderAudit>
 
          PersistedWorkflow.startNewRound()
              val auditRound
                  // first time, create the round
                  // next time, create from previous round

              estimateSampleSizes(
                  auditConfig(),
                  auditRound,
                  cardManifest = mvrManager().sortedCards(),
                  cardPools = mvrManager().cardPools(),
              )
                  makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardManifest,  vunderFuzz)
                  // put results into assertionRounds, contestRounds

              fun sampleWithContestCutoff(
                  auditConfig(),
                  mvrManager(),
                  auditRound,
                  auditRounds.previousSamples(roundIdx),
                  quiet)

              writeAuditRoundJsonFile(nextRound, publisher.auditStateFile(nextRound.roundIdx))
              writeSamplePrnsJsonFile(nextRound.samplePrns, publisher.samplePrnsFile(nextRound.roundIdx))


3.2 audit results for round X

        // for non-test:
        //   if private/sortedMvrsFile exists, must call CreateAudit.writeMvrsForRound
        //   else manual enter: AuditRecord.enterMvrs (eg from EnterMvrsCli) TODO
    
        workflow(useTest).runAuditRound(auditRound, quiet)

        if (mvrManager is PersistedMvrManagerTest) {
            val sampledMvrs = mvrManager.setMvrsForRoundIdx(roundIdx)
                writeAuditableCardCsvFile(sampledMvrs, publisher.sampleMvrsFile(publisher.currentRound())) // test sampleMvrs
        }

        val complete =  when (config.auditType) {
            AuditType.CLCA -> runClcaAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx, auditor = ClcaAssertionAuditor(quiet))
            AuditType.POLLING -> runPollingAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
                                      auditor = OneAuditAssertionAuditor(mvrManager().cardPools()!!, quiet))
        }
            PersistedMvrManager.makeMvrCardPairsForRound()
                writeAuditableCardCsvFile(Closer(sampledCards.iterator()), publisher.sampleCardsFile(round)) // sampleCards


        writeAuditRoundJsonFile(auditRound, publisher.auditStateFile(roundIdx)) // replace auditState


4. Private data for testing and simulation only, not part of the public record

        eg startTestElectionOneAudit() or cardManifestAttack()
            CreateAudit.writeSortedMvrs()


## SingleRoundAudit vs auditing with rounds

When simulating and testing, its convenient to do the audit in a single round, with all mvrs available, skipping the estimation steps,
and keeping everything in memory (no peristence).

This is used for plot generation in the _plots_ module. See _ClcaSingleRoundAuditTaskGenerator, OneAuditSingleRoundAuditTaskGenerator,
PollingSingleRoundAuditTaskGenerator,_ and _RaireSingleRoundAuditTaskGenerator_ and their uses in _plots_.

_AuditWorkflow_ and its subclasses (esp _PersistedWorkflow_) implement auditing with rounds. 

### MvrManager

mvrManager abstracts the handling of mvrs.


///////////////////////////////////////////////////////////////////////////
TODO rewrite


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

## MVRs 

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
