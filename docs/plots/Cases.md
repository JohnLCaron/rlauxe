9/1/2025

While rlauxe is intended to be used in real elections, its primary use currentlty is to simulate elections for testing.

A real election requires human auditors to manually retrieve physical ballots and create MVRs (manual vote records). 
This is done in rounds; the number of ballots needed is estimated based on the contests' reported margins. 
At each round, the MVRS are typically entered into a spreadsheet, and the results are exported to a cvs file,
and copied into the Audit Record for processing. See MvrManagerFromRecord.

When testing, the MVRS are typically simulated by introducing "errors" on the correcponding CVRs. See MvrManagerTestFromRecord.


# Initialization

## SF2024

In input is in $topDir/CVR_Export_20241202143051.zip. This contains the Dominion CVR_Export files, as well as the
Contest Manifest, Candidate Manifest, and other manifests. We also have the San Francisco County summary.xml file from
their website for corroboration.

**createSfElectionFromCsvExport**: We read the CVR_Export files and write equivilent csv files in our own "AuditableCard" format to a
temporary "cvrExport.csv" file. 

**createSfElectionFromCsvExport**: We make the contests from the information in ContestManifest and CandidateManifest files,
and tabulate the votes from the cvrs. If its an IRV contest, we use the raire-java library to creae the Raire assertions.
We write the auditConfig.json (which contains the prn seed) and contests.json files to the audit directory.

**createSortedCards**: Using the prn seed, we assign prns to all cvrs and rewrite the cvrs to sortedCards.csv (optionally zipped), using an out-of-memory
sorting algorithm.

## SF2024 OneAudit

For testing OneAudit, we assume that the vote-by-mail ballots have CVRs, and the vote-in-person ballots are in pools
based on the precinct ("${session.TabulatorId}-${session.BatchId}"). **createSfElectionFromCsvExportOA**: Creates OneAudit 
contests instead of regular contests, but otherwise follows the SF2024 case above.


## SF2024 OneAudit find variance

Here we modify the usual testing harness to repeatedly find samplesUsed for SF2024 OneAudit mayoral contest, for different seeds.
We suspect that the variance may be higher for OneAudit vs CLCA. But OneAudit is highly dependent on the specifics of the pool averages,
so the usual simulations are artificial. So we will use the real case of the SF2024 OneAudit mayoral contest.



# AuditRecord

    auditDir/
      auditConfig.json      // AuditConfigJson
      contests.json         // ContestsUnderAuditJson
      sortedCards.csv       // AuditableCardCsv (or)
      sortedCards.zip       // AuditableCardCsv
      ballotPool.csv        // BallotPoolCsv (OneAudit only)

      roundX/
        auditState.json     // AuditRoundJson
        samplePrns.json     // SamplePrnsJson // the sample prns to be audited
        sampleMvrs.csv      // AuditableCardCsv  // the mvrs used for the audit; matches sampleNumbers.json

For each round, the selected ballot prns are written into samplePrns.json in order. The mvrs are gathered or
simulated and written to sampleMvrs.csv. 

Managing the MVRS is delegated to an MvrManager

