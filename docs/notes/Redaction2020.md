# Redaction notes for Corla 2020 election
last changed 9/13/2026

See [Corla2020notes](Corla2020notes.md) for description of the cvr files.

<!-- TOC -->
* [Redaction notes for Corla 2020 election](#redaction-notes-for-corla-2020-election)
  * [Compare CVR files (from votedatabase) with manifest (auditcenter)](#compare-cvr-files-from-votedatabase-with-manifest-auditcenter)
    * [Style and Aggregated votes, no Ids](#style-and-aggregated-votes-no-ids)
    * [Individual cards with Style and IDs, no vote counts](#individual-cards-with-style-and-ids-no-vote-counts)
    * [No Redactions, but can infer from reported votes and manifest](#no-redactions-but-can-infer-from-reported-votes-and-manifest)
    * [Summary](#summary)
  * ["Trust the manifest" CVR redaction reading algorithm](#trust-the-manifest-cvr-redaction-reading-algorithm)
  * [Experiments with Redaction programs](#experiments-with-redaction-programs)
  * [Compare redaction variants](#compare-redaction-variants)
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
* Arapahoe: missing=100, minCardsForVote=115
* Garfield: missing=3, minCardsForVote=3
* Jackson: missing=701, minCardsForVote=690
* Teller: missing=91, minCardsForVote=91

Arapahoe has 100 missing cards, but vote difference (reported - cvrs) need 115.
The reported population agrees with the manifest, but the reported votes are inconsistent in 3 contests.
One thing to do is to increase the population and the redacted pool by 115. This leaves the reported vote intact.
In a real election, we could resolve the discrepency.
In our simulation, it means that the simulated ballots will be short of the reported votes.

### Summary

````
totalManifest = 4104185
   totalNcvrs = 4100064
 totalMissing =    4121

redactedCvrs = 3171
totalNgroups = 42
ncounties with missing > 0 = 18
````

## "Trust the manifest" CVR redaction reading algorithm

1. Discard CVRs that dont appear in the manifest.
2. Create a redacted pool for Manifest entries that dont appear in the CVRs. Use voteDifference for the pool subtotals.
3. Verify that when redacted rows are present in the CVRs, they are also present in the manifest.
4. Make a single redacted pool for each contest (onepool variant); ignore style information for now. The pool is a 
  OneAudit pool with subtotal = voteDifference. Use county reported vote total. 
5. When doing the phantom variant, I think you have to use the cvr vote totals (not reported), leave out the
   redacted cards, and add nphantoms = nredactions. Use cvr tab for the county reported vote total.
6. Make the state total from the county totals.  Note that the phantom variant changes the state vote totals, since you are using
  cvr total without redactions.

In summary:

* This for both row and column redaction. One can even just remove redacted rows from the cvr file.
* It assumes the manifest is accurate, and compares the unredacted cvr ids to the manifest to obtain the redacted cvr ids. 
  This allows using OneAudit instead of just counting all redacted ballots as phantoms.
* It subtracts the cvr tabulation from the reported county contest subtotals to get the redacted pool subtotal.
* We still need an accurate count of the ncards per contest foreach county to be added, in order to run a CLCA 
  audit with mathematical rigor. Adding it to the CVR file would be natural, but it could also be provided
  elsewhere.

Using this algorithm on the 2020 General election, the following table shows which counties had redactions.
The column "missing" is the number of cards in each county's redacted pool:

| county      | population | manifestCount | ncvrs   | missing | minCardsForVote | nredactedCvrs | cvrNoManifest |
|-------------|------------|---------------|---------|---------|-----------------|---------------|---------------|
| Boulder     | 208445     | 208445        | 205796  | 2649    | 2628            | 2634          | 0             |
| Garfield    | 31245      | 31245         | 30544   | 701     | 690             | 0             | 0             |
| Summit      | 18943      | 18943         | 18774   | 169     | 154             | 169           | 14            |
| El Paso     | 382583     | 382583        | 382424  | 159     | 8               | 44            | 736           |
| Arapahoe    | 354347     | 354347        | 354247  | 100     | 115             | 0             | 0             |
| Teller      | 17178      | 17087         | 17087   | 91      | 91              | 0             | 0             |
| Larimer     | 226896     | 226896        | 226839  | 57      | 52              | 57            | 5             |
| Douglas     | 234272     | 234272        | 234216  | 56      | 56              | 56            | 0             |
| Jefferson   | 381834     | 381834        | 381787  | 47      | 32              | 47            | 6             |
| Pueblo      | 89155      | 89155         | 89108   | 47      | 46              | 47            | 0             |
| Weld        | 169080     | 169080        | 169039  | 41      | 27              | 41            | 22            |
| Pitkin      | 12089      | 12089         | 12063   | 26      | 23              | 26            | 0             |
| Logan       | 10685      | 10685         | 10664   | 21      | 21              | 21            | 0             |
| Eagle       | 58589      | 58589         | 58569   | 20      | 9               | 20            | 2             |
| Adams       | 239425     | 239425        | 239409  | 16      | 16              | 0             | 0             |
| Morgan      | 13860      | 13860         | 13855   | 5       | 5               | 5             | 0             |
| Rio Blanco  | 3709       | 3709          | 3705    | 4       | 4               | 4             | 0             |
| Jackson     | 889        | 889           | 886     | 3       | 3               | 0             | 0             |
| Alamosa     | 7923       | 7923          | 7923    | 0       | 0               | 0             | 0             |
| Archuleta   | 9404       | 9404          | 9404    | 0       | 0               | 0             | 0             |
| Bent        | 2295       | 2295          | 2295    | 0       | 0               | 0             | 0             |
| Broomfield  | 47103      | 47103         | 47103   | 0       | 0               | 0             | 0             |
| Chaffee     | 13862      | 13862         | 13862   | 0       | 0               | 0             | 0             |
| Cheyenne    | 1146       | 1146          | 1146    | 0       | 0               | 0             | 0             |
| Clear Creek | 6608       | 6608          | 6608    | 0       | 0               | 0             | 0             |
| Conejos     | 4404       | 4404          | 4404    | 0       | 0               | 0             | 0             |
| Costilla    | 2139       | 2139          | 2139    | 0       | 0               | 0             | 0             |
| Crowley     | 1769       | 1769          | 1769    | 0       | 0               | 0             | 0             |
| Custer      | 3670       | 3670          | 3670    | 0       | 0               | 0             | 0             |
| Delta       | 19553      | 19553         | 19553   | 0       | 0               | 0             | 0             |
| Denver      | 1181464    | 1181464       | 1181464 | 0       | 0               | 0             | 0             |
| Dolores     | 1500       | 1500          | 1500    | 0       | 0               | 0             | 0             |
| Elbert      | 19150      | 19150         | 19150   | 0       | 0               | 0             | 0             |
| Fremont     | 26289      | 26289         | 26289   | 0       | 0               | 0             | 0             |
| Gilpin      | 4239       | 4239          | 4239    | 0       | 0               | 0             | 1             |
| Grand       | 10483      | 10483         | 10483   | 0       | 0               | 0             | 0             |
| Hinsdale    | 640        | 640           | 640     | 0       | 0               | 0             | 0             |
| Huerfano    | 4459       | 4459          | 4459    | 0       | 0               | 0             | 0             |
| Kiowa       | 909        | 909           | 909     | 0       | 0               | 0             | 0             |
| Kit Carson  | 3892       | 3892          | 3892    | 0       | 0               | 0             | 1             |
| La Plata    | 35995      | 35995         | 35995   | 0       | 0               | 0             | 0             |
| Lake        | 4010       | 4010          | 4010    | 0       | 0               | 0             | 0             |
| Lincoln     | 2665       | 2665          | 2665    | 0       | 0               | 0             | 0             |
| Mesa        | 91506      | 91506         | 91506   | 0       | 0               | 0             | 0             |
| Mineral     | 767        | 767           | 767     | 0       | 0               | 0             | 0             |
| Moffat      | 7074       | 7074          | 7074    | 0       | 0               | 0             | 0             |
| Montezuma   | 15631      | 15631         | 15631   | 0       | 0               | 0             | 0             |
| Montrose    | 25159      | 25159         | 25159   | 0       | 0               | 0             | 0             |
| Otero       | 9704       | 9704          | 9704    | 0       | 0               | 0             | 0             |
| Ouray       | 4050       | 4050          | 4050    | 0       | 0               | 0             | 0             |
| Park        | 12406      | 12406         | 12406   | 0       | 0               | 0             | 0             |
| Phillips    | 2561       | 2561          | 2561    | 0       | 0               | 0             | 0             |
| Prowers     | 5601       | 5601          | 5601    | 0       | 0               | 0             | 0             |
| Rio Grande  | 6451       | 6451          | 6451    | 0       | 0               | 0             | 0             |
| Routt       | 33008      | 33008         | 33008   | 0       | 0               | 0             | 0             |
| Saguache    | 6821       | 6821          | 6821    | 0       | 0               | 0             | 0             |
| San Miguel  | 5189       | 5189          | 5189    | 0       | 0               | 0             | 1             |
| Sedgwick    | 1496       | 1496          | 1496    | 0       | 0               | 0             | 0             |
| Washington  | 3028       | 3028          | 3028    | 0       | 0               | 0             | 0             |
| Yuma        | 5029       | 5029          | 5029    | 0       | 0               | 0             | 0             |

where

|           field | example |                                           description |
| --------------- | ------- | ----------------------------------------------------- |
|          county | Larimer |                                           county name |
|      population |  226896 |          county population from round.ballotCardCount |
|   manifestCount |  226896 |                     number of entries in the manifest |
|           ncvrs |  226839 |      count of Cvrs that match entries in the Manifest |
|         missing |      57 |                                 manifestCount - ncvrs |
| minCardsForVote |      52 |                minimum cards needed for missing votes |
|   nredactedCvrs |      57 |             number of redacted cvrs given in CVR file |
|   cvrNoManifest |       5 | count of Cvrs that dont match entries in the Manifest |

## Experiments with Redaction programs

Run the redaction program on the existing CVRs to see what results. (TODO)

## Compare redaction variants

(Skipping "styles" variant for now, as none of the existing cvr files can support it).

|                          | ncontests | nredactions | ncards  | npools | phantoms    | onePool | unredacted |
|--------------------------|-----------|-------------|---------|--------|-------------|---------|------------|
| Corla2020                | 562       | 4121        | 4104185 | 18     | 19721 (561) | 17182   | 16789      |

TODO test that extra cards in phantoms dont affect the diluted margin, ie the population size remains constant:
read the cardManifest and tabulate npops. Alos, could add that to CorlaStateElection.

* Redactions are around 1% of total votes which accounts for the small effect. 
* phantoms/unredacted = 1.174 = 17% more samples needed.
* phantoms/onePool = 1.147 = 15% more samples needed.
* TODO sample distributions need to be generated to characterize the mean and the variance.
