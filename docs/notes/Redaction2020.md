# Redaction notes for Corla 2020 election
last changed 9/11/2026

See [Corla2020notes](Corla2020notes.md) for description of the cvr files.

<!-- TOC -->
* [Redaction notes for Corla 2020 election](#redaction-notes-for-corla-2020-election)
  * [Compare CVR files (from votedatabase) with manifest (auditcenter)](#compare-cvr-files-from-votedatabase-with-manifest-auditcenter)
    * [Style and Aggregated votes, no Ids](#style-and-aggregated-votes-no-ids)
    * [Individual cards with Style and IDs, no vote counts](#individual-cards-with-style-and-ids-no-vote-counts)
    * [No Redactions, but can infer from reported votes and manifest](#no-redactions-but-can-infer-from-reported-votes-and-manifest)
    * [Summary](#summary)
  * ["Trust the manifest" algorithm](#trust-the-manifest-algorithm)
  * [Experiments with Redaction programs](#experiments-with-redaction-programs)
<!-- TOC -->


## Compare CVR files (from votedatabase) with manifest (auditcenter)

### Style and Aggregated votes, no Ids

This I think is called "row redaction": the entire row is redacted, the aggregations are given,
in this case seperated by style. 

* Boulder: 49 redacted rows
* combine rows with same style, gives 30 groups with known styles.
* cant use seperate pools because we dont know which cards go to which pool.

### Individual cards with Style and IDs, no vote counts

This I think is called "column redaction": the ids are retained and the votes are redacted.

* Douglas: 56; has 'X' over contest vote, know style
* Eagle: 20; "Redacted per 24-27-205.5 (4)(b)(III) C.R.S"
* El Paso 44; *
* Jefferson 47; has "Redacted" over contest vote, know style
* Larimer 57; "REDACTED"
* Logan 21; empty votes
* Morgan 5; empty votes
* Pitkin 26; has 'X' over contest vote, know style
* Pueblo 47; empty votes
* Rio Blanco 4; empty votesIndividual cards with Style and IDs, no vote counts
* Summit 169; empty votes
* Weld 41; empty votes

### No Redactions, but can infer from reported votes and manifest

A form of "row redaction": the entire row is redacted, no aggregation. 
Derive the missing card ids and count from the manifest. Derive the vote aggregation
from the "voteDifference".

1. Examine the manifest for entries that dont appear in the CVRs; count = missing
2. Subtract CVR tabulations from reported vote totals = "voteDifference". 
3. minCardsForVote = minimum ncards needed for voteDifference

* Adams: missing=16, minCardsForVote=16
* Arapahow: missing=100, minCardsForVote=115
* Garfield: missing=3, minCardsForVote=3
* Jackson: missing=701, minCardsForVote=690
* Teller: missing=91, minCardsForVote=91

### Summary

````
totalManifest = 4104185
   totalNcvrs = 4100064
 totalMissing =    4121

redactedCvrs = 3171
totalNgroups = 42
ncounties with missing > 0 = 18
````

## "Trust the manifest" algorithm

1. Discard CVRs that dont appear in the manifest.
2. Create redacted pool for Manifest entries that dont appear in the CVRs. Use voteDifference for the pool subtotals.
3. Verify that when redacted rows are present in the CVRs, they are also present in the manifest.
4. Make a single redacted pool for each contest; ignore style information for now. Compare using OneAudit vs marking 
   redacted cards as phantoms

Using this algorithm, The column "missing" in the following table is the number of cards in each county's OneAudit pool:

| county      | population | manifestCount | ncvrs   | missing | minCardsForVote | nredactedCvrs |
|-------------|------------|---------------|---------|---------|-----------------|---------------|
| Boulder     | 208445     | 208445        | 205796  | 2649    | 2628            | 2634          |
| Garfield    | 31245      | 31245         | 30544   | 701     | 690             | 0             |
| Summit      | 18943      | 18943         | 18774   | 169     | 154             | 169           |
| El Paso     | 382583     | 382583        | 382424  | 159     | 8               | 44            |
| Arapahoe    | 354347     | 354347        | 354247  | 100     | 115             | 0             |
| Teller      | 17178      | 17087         | 17087   | 91      | 91              | 0             |
| Larimer     | 226896     | 226896        | 226839  | 57      | 52              | 57            |
| Douglas     | 234272     | 234272        | 234216  | 56      | 56              | 56            |
| Jefferson   | 381834     | 381834        | 381787  | 47      | 32              | 47            |
| Pueblo      | 89155      | 89155         | 89108   | 47      | 46              | 47            |
| Weld        | 169080     | 169080        | 169039  | 41      | 27              | 41            |
| Pitkin      | 12089      | 12089         | 12063   | 26      | 23              | 26            |
| Logan       | 10685      | 10685         | 10664   | 21      | 21              | 21            |
| Eagle       | 58589      | 58589         | 58569   | 20      | 9               | 20            |
| Adams       | 239425     | 239425        | 239409  | 16      | 16              | 0             |
| Morgan      | 13860      | 13860         | 13855   | 5       | 5               | 5             |
| Rio Blanco  | 3709       | 3709          | 3705    | 4       | 4               | 4             |
| Jackson     | 889        | 889           | 886     | 3       | 3               | 0             |
| Alamosa     | 7923       | 7923          | 7923    | 0       | 0               | 0             |
| Archuleta   | 9404       | 9404          | 9404    | 0       | 0               | 0             |
| Bent        | 2295       | 2295          | 2295    | 0       | 0               | 0             |
| Broomfield  | 47103      | 47103         | 47103   | 0       | 0               | 0             |
| Chaffee     | 13862      | 13862         | 13862   | 0       | 0               | 0             |
| Cheyenne    | 1146       | 1146          | 1146    | 0       | 0               | 0             |
| Clear Creek | 6608       | 6608          | 6608    | 0       | 0               | 0             |
| Conejos     | 4404       | 4404          | 4404    | 0       | 0               | 0             |
| Costilla    | 2139       | 2139          | 2139    | 0       | 0               | 0             |
| Crowley     | 1769       | 1769          | 1769    | 0       | 0               | 0             |
| Custer      | 3670       | 3670          | 3670    | 0       | 0               | 0             |
| Delta       | 19553      | 19553         | 19553   | 0       | 0               | 0             |
| Denver      | 1181464    | 1181464       | 1181464 | 0       | 0               | 0             |
| Dolores     | 1500       | 1500          | 1500    | 0       | 0               | 0             |
| Elbert      | 19150      | 19150         | 19150   | 0       | 0               | 0             |
| Fremont     | 26289      | 26289         | 26289   | 0       | 0               | 0             |
| Gilpin      | 4239       | 4239          | 4239    | 0       | 0               | 0             |
| Grand       | 10483      | 10483         | 10483   | 0       | 0               | 0             |
| Hinsdale    | 640        | 640           | 640     | 0       | 0               | 0             |
| Huerfano    | 4459       | 4459          | 4459    | 0       | 0               | 0             |
| Kiowa       | 909        | 909           | 909     | 0       | 0               | 0             |
| Kit Carson  | 3892       | 3892          | 3892    | 0       | 0               | 0             |
| La Plata    | 35995      | 35995         | 35995   | 0       | 0               | 0             |
| Lake        | 4010       | 4010          | 4010    | 0       | 0               | 0             |
| Lincoln     | 2665       | 2665          | 2665    | 0       | 0               | 0             |
| Mesa        | 91506      | 91506         | 91506   | 0       | 0               | 0             |
| Mineral     | 767        | 767           | 767     | 0       | 0               | 0             |
| Moffat      | 7074       | 7074          | 7074    | 0       | 0               | 0             |
| Montezuma   | 15631      | 15631         | 15631   | 0       | 0               | 0             |
| Montrose    | 25159      | 25159         | 25159   | 0       | 0               | 0             |
| Otero       | 9704       | 9704          | 9704    | 0       | 0               | 0             |
| Ouray       | 4050       | 4050          | 4050    | 0       | 0               | 0             |
| Park        | 12406      | 12406         | 12406   | 0       | 0               | 0             |
| Phillips    | 2561       | 2561          | 2561    | 0       | 0               | 0             |
| Prowers     | 5601       | 5601          | 5601    | 0       | 0               | 0             |
| Rio Grande  | 6451       | 6451          | 6451    | 0       | 0               | 0             |
| Routt       | 33008      | 33008         | 33008   | 0       | 0               | 0             |
| Saguache    | 6821       | 6821          | 6821    | 0       | 0               | 0             |
| San Miguel  | 5189       | 5189          | 5189    | 0       | 0               | 0             |
| Sedgwick    | 1496       | 1496          | 1496    | 0       | 0               | 0             |
| Washington  | 3028       | 3028          | 3028    | 0       | 0               | 0             |
| Yuma        | 5029       | 5029          | 5029    | 0       | 0               | 0             |


where:

| field           | example | description                                  |
|-----------------|---------|----------------------------------------------|
| county          | Teller  | county name                                  |
| population      | 17178   | county population from round.ballotCardCount |
| manifestCount   | 17087   | number of entries in the manifest            |
| ncvrs           | 17087   | number of cvrs that are in the manifest      |
| missing         | 91      | manifestCount - ncvrs                        |
| minCardsForVote | 91      | minimum cards needed for missing votes       |
| nredactedCvrs   | 0       | number of redacted cvrs given in CVR file    |

## Experiments with Redaction programs

Run the redaction program on the existing CVRs to see what happens.