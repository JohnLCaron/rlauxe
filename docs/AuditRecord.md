# The AuditRecord
_last changed 03/02/2026_

A complete _Audit Record_ has the following files in it:

    $auditdir/
        auditConfig.json    // auditConfigJson (old way)
        auditCreationConfig.json // auditCreationConfigJson (new way)
        cardManifest.csv      // AuditableCardCsv, may be zipped
        contests.json         // ContestsUnderAuditJson
        electionInfo.json     // ElectionInfoJson 
        oneauditPools.json    // OneAuditPoolCsv, OneAudit only 
        populations.json      // PopulationJson, optional 
        sortedCards.csv       // AuditableCardCsv, sorted by prn, may be zipped
        
        roundX/
            auditRoundConfigX.json  // configuration parameters for round X (new way)
            auditEstX.json       // AuditRoundJson,  the state of the estimation for this round
            auditStateX.json     // AuditRoundJson,  the state of the audit for this round
            sampleCardsX.csv     // AuditableCardCsv, complete cards used for this round; matches samplePrnsX.csv
            sampleMvrsX.csv      // AuditableCardCsv, complete mvrs used for this round; matches samplePrnsX.csv
            samplePrnsX.json     // SamplePrnsJson, complete sample prns for this round, in order

## Commitment Sequence

1. createElectionRecord

The election information is contained in the following files. The EA can modify these until satisfied that they
are correct. Before the seed is chosen in step 2, they are digitally signed and published publically (aka _committed to the Audit Record_), 
and may not be changed.

        electionInfo.json      
        populations.json       
        oneauditPools.csv       
        contests.json
        cardManifest.csv      

2. createAuditRecord : PRNG seed chosen, cards assigned PRNs

The PRNG seed is chosen, and all the cards in the card manifest are assigned a PRN in order.
The cards are then sorted by PRN and written to sortedCards.csv. These are commited to the Audit Record.
The PRNG seed can only be chosen once and the cards immediately committed.
        
        auditCreationConfig.json  // the overall configuration parameters, including the seed
        sortedCards.csv   // all cards sorted by PRN

3. Audit Round X Sample Estimation

The EA decides which contests are in (or will continue to be in) the audit, and what the configuration parameters are for the round.
The EA can calculate estimated sample sizes, and modify contest sample sizes and configuration parameters as often as they want.
The EA cannot hand pick which ballots to sample, only modify how many samples for each contest are used in the round.
This preserves the _canonical ordering_ of each contest, 
see [Deterministic sampling order for each Contest](https://github.com/JohnLCaron/rlauxe#deterministic-sampling-order-for-each-contest)
for more explanation.

Once the EA is satisfied with sample sizes and auditing parameters, the following files are committed to the Audit Record:

        roundX/
            auditRoundConfigX.json  // the configuration parameters for round X
            auditEstX.json          // the estimation of sample sizes of the contests in this round
            samplePrnsX.json        // the chosen sample cards' prns for this round

4. Audit Round X gather MVRs

The physical ballots/cards are found that match samplePrnsX.json. These are hand-audited and their MVRs are written to sampleMvrsX.csv.
The matching cards from the CardManifest are written to sampleCardsX.csv, for completeness and security.
Before the audit is run, these are committed to the Audit Record:

        roundX/
            sampleMvrsX.csv      // complete mvrs used for this round; matches samplePrnsX.csv
            sampleCardsX.csv     // complete cards used for this round; matches samplePrnsX.csv

5. Run Audit Round X

The audit is run for round X, and the following file is committed to the Audit Record.

        roundX/
            auditStateX.json     // the results of the audit for this round

If not all contests are complete at the end of each round, then a new round begins and steps 3,4,5 are repeated.


## Composite AuditRecord

This is an experimental feature for Belgium elections. Each componment is a single contest.

    $compositedir/
        $component1/
           audit/
        $component2/
           audit/      
        ...  

Use AuditRecord.readFrom($compositedir). Each $component/audit is an AuditRecord.
The contests from all components are put into the CompositeRecord. You can view and read, but not run audits on the
CompositeRecord. Run audits independently on the individual components.

## SingleRoundAudit vs Auditing with rounds

When simulation and testing, its convenient to do the audit in a single round, with all MVRs available, skipping the estimation steps,
and keeping everything in memory (no persistence).

_AuditWorkflow_ and its subclasses (esp _PersistedWorkflow_) implement auditing with rounds. 

For real-world workflows, see createSfElection(), createBoulderElection(), createBelgiumClca(), and createColoradoElection()
in the cases module.

### Auditing with rounds workflow
   
      val auditdir = "my/audit/dir"
      val election: CreateElectionIF = CreateYourElection(auditType, ...))
      createElectionRecord("MyElection", election, auditdir)

      val config = AuditConfig(...)
      createAuditRecord(config, election, auditdir)

      val result = startFirstRound(auditdir)
      if (result.isErr) logger.error{ result.toString() }
      logger.info{"startFirstBoulderRound took $stopwatch"}
