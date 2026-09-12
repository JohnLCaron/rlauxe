# Redaction notes for Corla 2020 election

See [Corla2020notes](Corla2020notes.md) for description of the cvr files.

## Manifest Files (from auditcenter)

There are no manifest files in auditcenter.
Oops, just found github/nealmcb/auditcenter/2020/general/round_1/manifest-**County**.csv

However it appears that batch_count_comparison.csv has the equivilent:

````
county_name,scanner_id,batch_id,count_per_manifest,count_per_cvr_file,difference
Adams,1,1,100,100,0
Adams,1,100,100,100,0
Adams,1,101,100,100,0
...
````
(from github/nealmcb/auditcenter/2020/general/batch_count_comparison.csv)


## Final Report (from auditcenter)

Each county has a final report in pdf
that contains:

````
Total Ballot Cards In Manifest
Total CVRs in CVR Export File
Total Ballot Cards Audited
Number of Audit Rounds

Round Summary (for each round)
Ballot Cards Audited
Discrepancies (Audited Contests)
Discrepancies (Non‐Audited Contests)
Disagreements (Audited Contests)
Disagreements (Non‐Audited Contests)

Audited Contests 
    Choice Votes Margin Diluted Margin %

List of Discrepencies (for each round)
    Discrepancies Recorded
    No Disagreements Recorded
    Ballot Cards Selected Imprinted ID
````
The Discrepencies show which ballots, but not which contests

Spot check (need to parse pdf to do this systematically) 
    Total Ballot Cards In Manifest == Total CVRs in CVR Export File
    Total Ballot Cards In Manifest == county population (total cards) from round.ballotCardCount

TODO most of this data is also in github/nealmcb/auditcenter/2020/general/**round_last**/stateReport.xlsx

ElPaso has 382583

## Examination of the CVR files (from votedatabase)

### Style and Aggregated votes, no Ids
* Boulder: 49 redacted rows
* combine rows with same style

# Individual cards with Style and IDs, no vote counts
* Douglas: 56; has 'X' over contest vote, know style
* Eagle: 20; "Redacted per 24-27-205.5 (4)(b)(III) C.R.S"
* El Paso 44; *
* Jefferson 47; has "Redacted" over contest vote, know style
* Larimer 57; "REDACTED"
* Logan 21; empty votes
* Morgan 5; empty votes
* Pitkin 26; has 'X' over contest vote, know style
* Pueblo 47; empty votes
* Rio Blanco 4; empty votes
* Summit 169; empty votes; 1 card with 4/25/2023
    - missing 155
* Weld 41; empty votes

do styles match?