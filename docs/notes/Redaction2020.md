# Redaction notes for Corla 2020 election
last changed 10/26/2026

See [Corla2020notes](Corla2020notes.md) for description of the cvr files.

<!-- TOC -->
* [Redaction notes for Corla 2020 election](#redaction-notes-for-corla-2020-election)
  * [Compare CVR files (from votedatabase) with manifest (auditcenter)](#compare-cvr-files-from-votedatabase-with-manifest-auditcenter)
    * [Style and Aggregated votes, no Ids](#style-and-aggregated-votes-no-ids)
    * [Individual cards with Style and IDs, no vote counts](#individual-cards-with-style-and-ids-no-vote-counts)
    * [No Redactions, but can infer from reported votes and manifest](#no-redactions-but-can-infer-from-reported-votes-and-manifest)
  * [Run Redaction program on 2020 County CVR files as is](#run-redaction-program-on-2020-county-cvr-files-as-is)
  * [Run anomymize_cvr program on 2020 County CVR files as is](#run-anomymize_cvr-program-on-2020-county-cvr-files-as-is)
    * [Summary](#summary)
  * ["Trust the manifest" CVR redaction reading algorithm](#trust-the-manifest-cvr-redaction-reading-algorithm)
  * [Compare redaction variants](#compare-redaction-variants)
  * [Precinct Portion](#precinct-portion)
    * [Summary of Redaction for trial counties 2026](#summary-of-redaction-for-trial-counties-2026)
<!-- TOC -->


## Compare CVR files (from votedatabase) with manifest (auditcenter)

### Style and Aggregated votes, no Ids

This is called "row redaction": the entire row is redacted, the aggregations are given,
in this case aggregated by style. 

* Boulder: 49 redacted rows
* combine rows with same style, gives 30 groups with known styles.
* cant use separate pools because we dont know which cards go to which pool.

### Individual cards with Style and IDs, no vote counts

This is called "column redaction": the ids are retained and the votes are redacted.

* Dolores: has a summary row that python sees as a rare style
* Douglas: 56; has 'X' over contest vote, with style
* Eagle: 20; "Redacted per 24-27-205.5 (4)(b)(III) C.R.S"
* El Paso 44; * on all columns
* Jefferson 47; has "Redacted" over contest vote, with style
* Larimer 57; "REDACTED": no vote counts
* Logan 21; empty votes
* Morgan 5; empty votes
* Phillips : has a summary row that python sees as a rare style
* Pitkin 26; has 'X' over contest vote, with style
* Pueblo 47; empty votes
* Rio Blanco 4; empty votes
* Summit 169; empty votes
* Weld 41; empty votes

### No Redactions, but can infer from reported votes and manifest

A form of "row redaction": the entire row is redacted, no aggregation. 
Derive the missing card ids and count from the manifest. Derive the vote aggregation
from the "voteDifference".

1. Examine the manifest for entries that dont appear in the CVRs; count = missing
2. Subtract CVR tabulations from reported vote totals = "voteDifference". 
3. minCardsForVote = minimum ncards needed for voteDifference

* Arapahoe: missing=100, minCardsForVote=115
* Adams: missing=16, minCardsForVote=16
* Jackson: missing=3

**Arapahoe** has 100 missing cards, but vote difference (reported - cvrs) needs 115.
The reported population agrees with the manifest, but the reported votes are inconsistent in 3 contests.
One thing to do is to increase the population and the redacted pool by 115. This leaves the reported vote intact.
In a real election, we could resolve the discrepency.
In our simulation, it means that the simulated ballots will be short of the reported votes.

## Run Redaction program on 2020 County CVR files as is

Does anonymize_cvr.py think that redactions are needed? Standard paremenets are minBallots = 10, contest_balancing = true.

**needs extra redaction**

* Arapahoe (40)  redacted 16
* Archeluta (11) unredacted
* Denver (475)   unredacted
* Eagle (84)     redacted 20
* Grand (11)     unredacted
* La Plata(24)   unredacted
* Larimer (43)   redacted 57
* Mesa (67)      unredacted
* Montrose (16)  unredacted
* Otero (29)     unredacted
* Rio Grande (10) unredacted
* Routt (15)     unredacted
* Saguache (24)  unredacted
* Sedgwick(16)   unredacted
* Weld (151)     redacted 41
* Yuma (10)      unredacted

The number in parenthesis is the estimate of the extra redaction needed, using precinct level redaction. The comment is whether they did any redaction at all.

## Run anomymize_cvr program on 2020 County CVR files as is


| county      | manifest | cvrUnredacted | cvrRedacted | pythonNredact | totalRedact | extraRedact |
|-------------|----------|---------------|-------------|---------------|-------------|-------------|  
| Denver      | 1181464  | 1181464       | 0           | 475           | 475         | 475         |
| Weld        | 169080   | 169061        | 41          | 201           | 192         | 151         |
| Summit      | 18943    | 18788         | 169         | 169           | 169         | 0           |
| Larimer     | 226896   | 226844        | 57          | 108           | 100         | 43          |
| Eagle       | 58589    | 58571         | 20          | 106           | 104         | 84          |
| Douglas     | 234272   | 234216        | 56          | 70            | 56          | 0           |
| Jefferson   | 381834   | 381793        | 47          | 70            | 47          | 0           |
| Mesa        | 91506    | 91506         | 0           | 67            | 67          | 67          |
| Pueblo      | 89155    | 89108         | 47          | 47            | 47          | 0           |
| El Paso     | 382583   | 383160        | 44          | 44            | 44          | 0           |
| Arapahoe    | 354347   | 354231        | 16          | 40            | 56          | 40          |
| Pitkin      | 12089    | 12063         | 26          | 35            | 26          | 0           |
| Saguache    | 6821     | 6821          | 0           | 31            | 24          | 24          |
| Otero       | 9704     | 9704          | 0           | 29            | 29          | 29          |
| La Plata    | 35995    | 35995         | 0           | 27            | 24          | 24          |
| Logan       | 10685    | 10664         | 21          | 21            | 21          | 0           |
| Sedgwick    | 1496     | 1496          | 0           | 20            | 16          | 16          |
| Dolores     | 1500     | 1500          | 0           | 18            | 0           | 0           |
| Montrose    | 25159    | 25159         | 0           | 18            | 16          | 16          |
| Phillips    | 2561     | 2561          | 0           | 18            | 0           | 0           |
| Routt       | 33008    | 33008         | 0           | 18            | 15          | 15          |
| Grand       | 10483    | 10483         | 0           | 16            | 11          | 11          |
| Archuleta   | 9404     | 9404          | 0           | 13            | 11          | 11          |
| Yuma        | 5029     | 5029          | 0           | 13            | 10          | 10          |
| Rio Grande  | 6451     | 6451          | 0           | 10            | 10          | 10          |
| Morgan      | 13860    | 13855         | 5           | 5             | 5           | 0           |
| Rio Blanco  | 3709     | 3705          | 4           | 4             | 4           | 0           |
| Adams       | 239425   | 239409        | 0           | 0             | 0           | 0           |
| Alamosa     | 7923     | 7923          | 0           | 0             | 0           | 0           |
| Baca        | 0        | 0             | 0           | 0             | 0           | 0           |
| Bent        | 2295     | 2295          | 0           | 0             | 0           | 0           |
| Boulder     | 208445   | 205796        | 0           | 0             | 0           | 0           |
| Broomfield  | 47103    | 47103         | 0           | 0             | 0           | 0           |
| Chaffee     | 13862    | 13862         | 0           | 0             | 0           | 0           |
| Cheyenne    | 1146     | 1146          | 0           | 0             | 0           | 0           |
| Clear Creek | 6608     | 6608          | 0           | 0             | 0           | 0           |
| Conejos     | 4404     | 4404          | 0           | 0             | 0           | 0           |
| Costilla    | 2139     | 2139          | 0           | 0             | 0           | 0           |
| Crowley     | 1769     | 1769          | 0           | 0             | 0           | 0           |
| Custer      | 3670     | 3670          | 0           | 0             | 0           | 0           |
| Delta       | 19553    | 19553         | 0           | 0             | 0           | 0           |
| Elbert      | 19150    | 19150         | 0           | 0             | 0           | 0           |
| Fremont     | 26289    | 26289         | 0           | 0             | 0           | 0           |
| Garfield    | 31245    | 31245         | 0           | 0             | 0           | 0           |
| Gilpin      | 4239     | 4240          | 0           | 0             | 0           | 0           |
| Gunnison    | 11332    | 11332         | 0           | 0             | 0           | 0           |
| Hinsdale    | 640      | 640           | 0           | 0             | 0           | 0           |
| Huerfano    | 4459     | 4459          | 0           | 0             | 0           | 0           |
| Jackson     | 889      | 886           | 0           | 0             | 0           | 0           |
| Kiowa       | 909      | 909           | 0           | 0             | 0           | 0           |
| Kit Carson  | 3892     | 3893          | 0           | 0             | 0           | 0           |
| Lake        | 4010     | 4010          | 0           | 0             | 0           | 0           |
| Las Animas  | 8062     | 106           | 0           | 0             | 0           | 0           |
| Lincoln     | 2665     | 2665          | 0           | 0             | 0           | 0           |
| Mineral     | 767      | 767           | 0           | 0             | 0           | 0           |
| Moffat      | 7074     | 7074          | 0           | 0             | 0           | 0           |
| Montezuma   | 15631    | 15631         | 0           | 0             | 0           | 0           |
| Ouray       | 4050     | 4050          | 0           | 0             | 0           | 0           |
| Park        | 12406    | 12406         | 0           | 0             | 0           | 0           |
| Prowers     | 5601     | 5601          | 0           | 0             | 0           | 0           |
| San Miguel  | 5189     | 5190          | 0           | 0             | 0           | 0           |
| Teller      | 17087    | 17087         | 0           | 0             | 0           | 0           |
| Washington  | 3028     | 3028          | 0           | 0             | 0           | 0           | 

where 

|         field |    value |                                   description |
| ------------- | -------- | --------------------------------------------- |
|        county | Arapahoe |                                   county name |
|      manifest |   354347 |             number of entries in the manifest |
| cvrUnredacted |   354231 |                      count of unredacted Cvrs |
|   cvrRedacted |       16 |       count of redacted Cvrs in existing file |
| pythonNredact |       40 | total redactions seen after python anonimizer |
|   totalRedact |       56 | total redactions seen after rlauxe anonimizer |
|   extraRedact |       40 | number of additional redaction made by rlauxe |

Notes:

* On unredacted files, python and rlauxe get same number of rare ballots; rlauxe a bit more efficient at finding ballots for contest balancing.
* Rlauxe can identify previous redactions, python not really. 
* Dolores, Philips: have a summary row that python sees as a rare style.
* El Paso: "Error building row index: input file contains already-redacted rows; cannot re-redact a previously anonymized file."


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

* Can be used for both row and column redaction, or even when rows are simply deleted from the cvr file.
* It assumes the manifest is accurate, and compares the unredacted cvr ids to the manifest to obtain the redacted cvr ids. 
  This allows using OneAudit instead of just counting all redacted ballots as phantoms.
* It subtracts the cvr tabulation from the reported county contest subtotals to get the redacted pool subtotal.
* We still need an accurate count of the ncards per contest foreach county to be added, in order to run a CLCA 
  audit with mathematical rigor. Adding it to the CVR file would be natural, but it could also be provided
  elsewhere.

Using this algorithm on the 2020 General election, the following table shows which counties had redactions.
* population and manifestCount (almost) always agree. Add phantoms where population > manifest. 
* when there are cvrs not in the manifest, the manifest may not be up to date. This appears to be the case for El Paso (see Corla2020notes)[Corla2020notes.md]).
* Boulder is reather a special case (see Corla2020notes)[Corla2020notes.md]).
* nredacted is the result of running the CVR file (as is) through anonymize_cvr.py.

The column "missing" is the number of cards in the manifest without a corresponding (redacted or unredacted) cvr:

| county      | total   | manifest | cvrUnredacted | cvrRedacted | missing | extraRedact | totalRedact | blankPrecinct |
|-------------|---------|----------|---------------|-------------|---------|-------------|-------------|---------------|
| Denver      | 1181464 | 1181464  | 1181464       | 0           | 0       | 475         | 475         | 0             |
| Weld        | 169080  | 169080   | 169061        | 41          | -22     | 151         | 192         | 0             |
| Eagle       | 58589   | 58589    | 58571         | 20          | -2      | 84          | 104         | 0             |
| Mesa        | 91506   | 91506    | 91506         | 0           | 0       | 67          | 67          | 113           |
| Larimer     | 226896  | 226896   | 226844        | 57          | -5      | 43          | 100         | 0             |
| Arapahoe    | 354347  | 354347   | 354231        | 16          | 100     | 40          | 56          | 0             |
| Otero       | 9704    | 9704     | 9704          | 0           | 0       | 29          | 29          | 0             |
| La Plata    | 35995   | 35995    | 35995         | 0           | 0       | 24          | 24          | 0             |
| Saguache    | 6821    | 6821     | 6821          | 0           | 0       | 24          | 24          | 0             |
| Montrose    | 25159   | 25159    | 25159         | 0           | 0       | 16          | 16          | 0             |
| Sedgwick    | 1496    | 1496     | 1496          | 0           | 0       | 16          | 16          | 0             |
| Las Animas  | 8062    | 8062     | 106           | 0           | 7956    | 15          | 15          | 0             |
| Routt       | 33008   | 33008    | 33008         | 0           | 0       | 15          | 15          | 0             |
| Archuleta   | 9404    | 9404     | 9404          | 0           | 0       | 11          | 11          | 0             |
| Grand       | 10483   | 10483    | 10483         | 0           | 0       | 11          | 11          | 0             |
| Rio Grande  | 6451    | 6451     | 6451          | 0           | 0       | 10          | 10          | 0             |
| Yuma        | 5029    | 5029     | 5029          | 0           | 0       | 10          | 10          | 0             |
| Adams       | 239425  | 239425   | 239409        | 0           | 16      | 0           | 0           | 0             |
| Alamosa     | 7923    | 7923     | 7923          | 0           | 0       | 0           | 0           | 0             |
| Baca        | 2244    | 0        | 0             | 0           | 0       | 0           | 0           | N/A           |
| Bent        | 2295    | 2295     | 2295          | 0           | 0       | 0           | 0           | 0             |
| Boulder     | 208445  | 208445   | 205796        | 2634        | 15      | 0           | 0           | 205796        |
| Broomfield  | 47103   | 47103    | 47103         | 0           | 0       | 0           | 0           | 0             |
| Chaffee     | 13862   | 13862    | 13862         | 0           | 0       | 0           | 0           | 0             |
| Cheyenne    | 1146    | 1146     | 1146          | 0           | 0       | 0           | 0           | 0             |
| Clear Creek | 6608    | 6608     | 6608          | 0           | 0       | 0           | 0           | 0             |
| Conejos     | 4404    | 4404     | 4404          | 0           | 0       | 0           | 0           | 0             |
| Costilla    | 2139    | 2139     | 2139          | 0           | 0       | 0           | 0           | 0             |
| Crowley     | 1769    | 1769     | 1769          | 0           | 0       | 0           | 0           | 0             |
| Custer      | 3670    | 3670     | 3670          | 0           | 0       | 0           | 0           | 0             |
| Delta       | 19553   | 19553    | 19553         | 0           | 0       | 0           | 0           | 0             |
| Dolores     | 1500    | 1500     | 1500          | 0           | 0       | 0           | 0           | 0             |
| Douglas     | 234272  | 234272   | 234216        | 56          | 0       | 0           | 56          | 0             |
| El Paso     | 382583  | 382583   | 383160        | 44          | -621    | 0           | 44          | 0             |
| Elbert      | 19150   | 19150    | 19150         | 0           | 0       | 0           | 0           | 0             |
| Fremont     | 26289   | 26289    | 26289         | 0           | 0       | 0           | 0           | 26289         |
| Garfield    | 31245   | 31245    | 30544         | 0           | 701     | 0           | 0           | 0             |
| Gilpin      | 4239    | 4239     | 4240          | 0           | -1      | 0           | 0           | 0             |
| Hinsdale    | 640     | 640      | 640           | 0           | 0       | 0           | 0           | 0             |
| Huerfano    | 4459    | 4459     | 4459          | 0           | 0       | 0           | 0           | 0             |
| Jackson     | 889     | 889      | 886           | 0           | 3       | 0           | 0           | 0             |
| Jefferson   | 381834  | 381834   | 381793        | 47          | -6      | 0           | 47          | 0             |
| Kiowa       | 909     | 909      | 909           | 0           | 0       | 0           | 0           | 0             |
| Kit Carson  | 3892    | 3892     | 3893          | 0           | -1      | 0           | 0           | 0             |
| Lake        | 4010    | 4010     | 4010          | 0           | 0       | 0           | 0           | 0             |
| Lincoln     | 2665    | 2665     | 2665          | 0           | 0       | 0           | 0           | 0             |
| Logan       | 10685   | 10685    | 10664         | 21          | 0       | 0           | 21          | 0             |
| Mineral     | 767     | 767      | 767           | 0           | 0       | 0           | 0           | 0             |
| Moffat      | 7074    | 7074     | 7074          | 0           | 0       | 0           | 0           | 0             |
| Montezuma   | 15631   | 15631    | 15631         | 0           | 0       | 0           | 0           | 0             |
| Morgan      | 13860   | 13860    | 13855         | 5           | 0       | 0           | 5           | 0             |
| Ouray       | 4050    | 4050     | 4050          | 0           | 0       | 0           | 0           | 0             |
| Park        | 12406   | 12406    | 12406         | 0           | 0       | 0           | 0           | 0             |
| Phillips    | 2561    | 2561     | 2561          | 0           | 0       | 0           | 0           | 0             |
| Pitkin      | 12089   | 12089    | 12063         | 26          | 0       | 0           | 26          | 0             |
| Prowers     | 5601    | 5601     | 5601          | 0           | 0       | 0           | 0           | 0             |
| Pueblo      | 89155   | 89155    | 89108         | 47          | 0       | 0           | 47          | 0             |
| Rio Blanco  | 3709    | 3709     | 3705          | 4           | 0       | 0           | 4           | 0             |
| San Miguel  | 5189    | 5189     | 5190          | 0           | -1      | 0           | 0           | 0             |
| Summit      | 18943   | 18943    | 18788         | 169         | -14     | 0           | 169         | 0             |
| Teller      | 17178   | 17087    | 17087         | 0           | 0       | 0           | 0           | 0             |
| Washington  | 3028    | 3028     | 3028          | 0           | 0       | 0           | 0           | 0             |
| Gunnison    | 0       | 11332    | 11332         | 0           | 0       | 0           | 0           | 0             |

where

|         field | example |                                       description |
| ------------- | ------- | ------------------------------------------------- |
|        county |  Denver |                                       county name |
|         total | 1181464 |     county total cards from round.ballotCardCount |
|      manifest | 1181464 |                 number of entries in the manifest |
| cvrUnredacted | 1181464 |                          count of unredacted Cvrs |
|   cvrRedacted |       0 |           count of redacted Cvrs in existing file |
|       missing |       0 |     manifestCount - (cvrUnredacted + cvrRedacted) |
|   extraRedact |     475 | number of additional redaction made by anonimizer |
|   totalRedact |     475 |               total number of redactions expected |
| blankPrecinct |       0 |         number of rows with blank precinct column |


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

## Precinct Portion
10/05/2026

Boulder has no precinct portion. It also has 1600 redacted ballots. I suspect they used precincts in their redaction, and then removed that column.


### Summary of Redaction for trial counties 2026

Ok heres what I know: none of La Plata, Weld or Morgan had the problem with CVRs (where the same ballotType had different contests, aka card styles), in either the 2026 primary or the 2020 general election. Douglas also did not have that problem in its 2020 CVRs.

In the 2026 Primary, La Plata and Morgan clearly ran our anonymizer which redacted 9 and 25 ballots, respectively. Weld did not run our anonymizer, but they redacted 20 rows by blanking out the votes. Our anonymizer did not find further ballots for Weld it thought should be redacted, but of course it did not have the unredacted CVR file.

In 2020 General, Douglas had 56 redacted ballots and our anonomizer did not find further ballots it thought should be redacted.

In summary, those 4 counties don't appear to have any of the problems that might mess up our Excel macro. And it seems to me that the current macro will work fine on all of these counties without change. The macro can replace what they are doing now. (Lori, am I missing something about that?)

The macro only does rare styles, not "contest balancing", so if those counties were willing to again run our python anonomizer, that would be more accurate. And we also think that with the exception of Boulder, no county is currently doing contest balancing, so the macro isn't making things worse.

I think I will send a pull request to Neal's repo (or should i use Lori's?) to change it to put * only where redacted votes exist. So if that is an option for some counties, they can use the python anonomizer and the file format will look the same as with the macro.

If we only have 4 counties, if needed maybe each of us can be at one of the counties when they do the redaction, in case there is a problem.


PS: there is one factoid not particularly relevant to redaction, but in 2026 Primary, La Plata had 50 cvrs in the CVR file that were not present in their manifest that we have in auditcenter. I mention it only because maybe we can alert them so they can double-check it next time.