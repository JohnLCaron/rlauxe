# Compare Anonomicer.kt and anonimize_cvr.py
10/13/2026

## Differences in algorithm

* Anonomicer removes uncontested contests from rare contests
* Anonomicer contest balancing is somewhat more efficient in locating donor ballots.

## Summary of counties 2020

| county      | population | manifestCount | cvrUnredacted | cvrRedacted | missing | blankPrecinct     | pythonNredact | rlauxeNredact | ballotTypesUnique |
|-------------|------------|---------------|---------------|-------------|---------|-------------------|---------------|---------------|-------------------|
| Denver      | 1181464    | 1181464       | 1181464       | 0           | 0       | 0                 | 475           | 475           | false             | 
| Weld        | 169080     | 169080        | 169061        | 41          | -22     | 0                 | 201           | 151           | true              | 
| Eagle       | 58589      | 58589         | 58571         | 20          | -2      | 0                 | 106           | 84            | false             | 
| Arapahoe    | 354347     | 354347        | 354247        | 0           | 100     | 0                 | 84            | 77            | false             | 
| Mesa        | 91506      | 91506         | 91506         | 0           | 0       | 113               | 67            | 67            | false             | 
| Larimer     | 226896     | 226896        | 226844        | 57          | -5      | 0                 | 108           | 43            | true              | 
| Otero       | 9704       | 9704          | 9704          | 0           | 0       | 0                 | 29            | 29            | true              | 
| La Plata    | 35995      | 35995         | 35995         | 0           | 0       | 0                 | 27            | 24            | true              | 
| Saguache    | 6821       | 6821          | 6821          | 0           | 0       | 0                 | 31            | 24            | false             | 
| Montrose    | 25159      | 25159         | 25159         | 0           | 0       | 0                 | 18            | 16            | true              | 
| Sedgwick    | 1496       | 1496          | 1496          | 0           | 0       | 0                 | 20            | 16            | true              | 
| Routt       | 33008      | 33008         | 33008         | 0           | 0       | 0                 | 18            | 15            | false             | 
| Archuleta   | 9404       | 9404          | 9404          | 0           | 0       | 0                 | 13            | 11            | true              | 
| Grand       | 10483      | 10483         | 10483         | 0           | 0       | 0                 | 16            | 11            | true              | 
| Rio Grande  | 6451       | 6451          | 6451          | 0           | 0       | 0                 | 10            | 10            | true              | 
| Yuma        | 5029       | 5029          | 5029          | 0           | 0       | 0                 | 13            | 10            | true              | 
| Adams       | 239425     | 239425        | 239409        | 0           | 16      | 0                 | 0             | 0             | true              | 
| Alamosa     | 7923       | 7923          | 7923          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Bent        | 2295       | 2295          | 2295          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Boulder     | 208445     | 208445        | 205796        | 0           | 2649    | 205796            | 63            | 0             | true              | 
| Broomfield  | 47103      | 47103         | 47103         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Chaffee     | 13862      | 13862         | 13862         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Cheyenne    | 1146       | 1146          | 1146          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Clear Creek | 6608       | 6608          | 6608          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Conejos     | 4404       | 4404          | 4404          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Costilla    | 2139       | 2139          | 2139          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Crowley     | 1769       | 1769          | 1769          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Custer      | 3670       | 3670          | 3670          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Delta       | 19553      | 19553         | 19553         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Dolores     | 1500       | 1500          | 1500          | 0           | 0       | 0                 | 18            | 0             | true              | 
| Douglas     | 234272     | 234272        | 234216        | 56          | 0       | 0                 | 70            | 0             | true              | 
| El Paso     | 382583     | 382583        | 383160        | 44          | -621    | 0                 | 0             | 0             | true              | 
| Elbert      | 19150      | 19150         | 19150         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Fremont     | 26289      | 26289         | 26289         | 0           | 0       | 26289             | 0             | 0             | true              | 
| Garfield    | 31245      | 0             | 0             | 0           | 0       | N/A               | 0             | 0             | unknown           | 
| Gilpin      | 4239       | 4239          | 4240          | 0           | -1      | 0                 | 0             | 0             | true              | 
| Hinsdale    | 640        | 640           | 640           | 0           | 0       | 0                 | 0             | 0             | true              | 
| Huerfano    | 4459       | 4459          | 4459          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Jackson     | 889        | 889           | 886           | 0           | 3       | 0                 | 0             | 0             | true              | 
| Jefferson   | 381834     | 381834        | 381793        | 47          | -6      | 0                 | 70            | 0             | true              | 
| Kiowa       | 909        | 909           | 909           | 0           | 0       | 0                 | 0             | 0             | true              | 
| Kit Carson  | 3892       | 3892          | 3893          | 0           | -1      | 0                 | 0             | 0             | true              | 
| Lake        | 4010       | 4010          | 4010          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Lincoln     | 2665       | 2665          | 2665          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Logan       | 10685      | 10685         | 10664         | 21          | 0       | 0                 | 21            | 0             | true              | 
| Mineral     | 767        | 767           | 767           | 0           | 0       | 0                 | 0             | 0             | true              | 
| Moffat      | 7074       | 7074          | 7074          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Montezuma   | 15631      | 15631         | 15631         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Morgan      | 13860      | 13860         | 13855         | 5           | 0       | 0                 | 5             | 0             | true              | 
| Ouray       | 4050       | 4050          | 4050          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Park        | 12406      | 12406         | 12406         | 0           | 0       | 0                 | 0             | 0             | true              | 
| Phillips    | 2561       | 2561          | 2561          | 0           | 0       | 0                 | 18            | 0             | true              | 
| Pitkin      | 12089      | 12089         | 12063         | 26          | 0       | 0                 | 35            | 0             | true              | 
| Prowers     | 5601       | 5601          | 5601          | 0           | 0       | 0                 | 0             | 0             | true              | 
| Pueblo      | 89155      | 89155         | 89108         | 47          | 0       | 0                 | 47            | 0             | true              | 
| Rio Blanco  | 3709       | 3709          | 3705          | 4           | 0       | 0                 | 4             | 0             | true              | 
| San Miguel  | 5189       | 5189          | 5190          | 0           | -1      | 0                 | 0             | 0             | true              | 
| Summit      | 18943      | 18943         | 18788         | 169         | -14     | 0                 | 169           | 0             | true              | 
| Teller      | 17178      | 17087         | 17087         | 0           | 91      | 0                 | 0             | 0             | true              | 
| Washington  | 3028       | 3028          | 3028          | 0           | 0       | 0                 | 0             | 0             | true              | 


where:

|             field |    example |                                              description |
| ----------------- | ---------- | -------------------------------------------------------- |
|            county | San Miguel |                                              county name |
|        population |       5189 |             county population from round.ballotCardCount |
|     manifestCount |       5189 |                        number of entries in the manifest |
|     cvrUnredacted |       5190 |                                 count of unredacted Cvrs |
|       cvrRedacted |          0 |                  count of redacted Cvrs in existing file |
|           missing |         -1 |            manifestCount - (cvrUnredacted + cvrRedacted) |
|     blankPrecinct |          0 |                number of rows with blank precinct column |
|     pythonNredact |          0 | number of additional redaction made by python anonimizer |
|     rlauxeNredact |          0 | number of additional redaction made by rlauxe anonimizer |
| ballotTypesUnique |       true |         ballotTypes have unique styles (contest pattern) |


### Missing < 0

This happens when there are more cvrs than in the manifest. 

Seems to be real, for example San Miguel has as its last CVR.imprintedId "2-208-1", but the manifest in auditcenter ends on batch 2-207.

````
5190,2,208,1,"2-208-1",3065857006 (3065857006),1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,0,0,0,1,0,0
````
Likely the cvrs were finalized after the manifest and after the RLA was run.

* In order to do a public RLA verification, we have to use the published manifests, and ignore CVRs that are not in the manifest.

### Arapahoe 2020

There are 35347 total cards, of which 100 are missing from the cvr file. We assume the county redacted these, and we can infer their vote subtotals by subtracting the cvr vote totals from the reported county vote totals. But we dont know how/why they were redacted.

If we run our standard precinct redaction on the 354247 unredacted cvrs, we find 39848 cards that need redaction = 11% of total. This is not auditable.

Arapahoe County starts in the Denver suburbs and stretches east halfway to the Kansas border. Its area includes 7 district court judges, 9 state representatives, and 5 state senators.  However, you might expect that the number of ballot styles might still be small in any given precinct.

But no, there are 412 precincts, and all but 11 have 14 or more styles; all but 26 have more than 40 styles. 

There are 20237 distinct precinct/style combinations, and half of them have less than 10 cards each:
````
unique precinct/styles = 20237
count rare precinct/styles = 10407 out of 20237 = 39848 cards
````
If you leave off the precinct and redact on unique combinations of ballotType and styles:
````
unique ballotType/styles = 3529
count rare ballotType/style = 1121 out of 3529 = 4219 cards
````
If you leave off the precinct and the ballotType and redact on unique styles:
````
unique styles = 80
count rare styles = 14 out of 80 = 31 cards
````

We dont know what we would get if we started with an unredacted file, but it's probably bounded by these values plus the 100 missing cards. Currently seeing 31 rare ballots and 46 donor ballots when run with styleOnly = true.

I think this problem is due to the size and composition of Arapahoe County, and it seems likely that ongoing audits will see this problem.

This is probably the stellar argument for allowing a "style-only" redaction option.

| contestIdx | contestName                                                     |
|------------|-----------------------------------------------------------------|
| 0          | Presidential Electors                                           |
| 1          | United States Senator                                           |
| 2          | Representative to the 117th United States Congress-District 1   |
| 3          | Representative to the 117th United States Congress-District 4   |
| 4          | Representative to the 117th United States Congress-District 6   |
| 5          | State Board of Education Member - Congressional District 1      |
| 6          | Regent of the University of Colorado - Congressional District 6 |
| 7          | State Senator - District 26                                     |
| 8          | State Senator - District 27                                     |
| 9          | State Senator - District 28                                     |
| 10         | State Senator - District 29                                     |
| 11         | State Senator - District 31                                     |
| 12         | State Representative - District 3                               |
| 13         | State Representative - District 9                               |
| 14         | State Representative - District 36                              |
| 15         | State Representative - District 37                              |
| 16         | State Representative - District 38                              |
| 17         | State Representative - District 40                              |
| 18         | State Representative - District 41                              |
| 19         | State Representative - District 42                              |
| 20         | State Representative - District 56                              |
| 21         | District Attorney - 18th Judicial District                      |
| 22         | Regional Transportation District Director - District A          |
| 23         | Regional Transportation District Director - District D          |
| 24         | Regional Transportation District Director - District E          |
| 25         | Regional Transportation District Director - District F          |
| 26         | Regional Transportation District Director - District G          |
| 27         | Regional Transportation District Director - District H          |
| 28         | Arapahoe County Commissioner - District 1                       |
| 29         | Arapahoe County Commissioner - District 3                       |
| 30         | Arapahoe County Commissioner - District 5                       |
| 31         | Justice of the Colorado Supreme Court - Hart                    |
| 32         | Justice of the Colorado Supreme Court - Samour                  |
| 33         | Colorado Court of Appeals Judge - Tow                           |
| 34         | Colorado Court of Appeals Judge - Welling                       |
| 35         | District Court Judge - 18th Judicial District - Amico           |
| 36         | District Court Judge - 18th Judicial District - Baum            |
| 37         | District Court Judge - 18th Judicial District - Mares           |
| 38         | District Court Judge - 18th Judicial District - Scipione        |
| 39         | District Court Judge - 18th Judicial District - Slade           |
| 40         | District Court Judge - 18th Judicial District - Vahle           |
| 41         | District Court Judge - 18th Judicial District - Volz            |
| 42         | Arapahoe County Court - Contiguglia                             |
| 43         | Arapahoe County Court - Ollada                                  |
| 44         | Arapahoe County Court - Williford                               |
| 45         | Amendment B (Constitutional)                                    |
| 46         | Amendment C (Constitutional)                                    |
| 47         | Amendment 76 (Constitutional)                                   |
| 48         | Amendment 77 (Constitutional)                                   |
| 49         | Proposition EE (Statutory)                                      |
| 50         | Proposition 113 (Statutory)                                     |
| 51         | Proposition 114 (Statutory)                                     |
| 52         | Proposition 115 (Statutory)                                     |
| 53         | Proposition 116 (Statutory)                                     |
| 54         | Proposition 117 (Statutory)                                     |
| 55         | Proposition 118 (Statutory)                                     |
| 56         | Ballot Question 2A                                              |
| 57         | Ballot Issue 2B                                                 |
| 58         | Referred Ballot Question No. 3A                                 |
| 59         | Initiated Ballot Question No. 300                               |
| 60         | Ballot Issue 4C                                                 |
| 61         | Ballot Issue 5A                                                 |
| 62         | Ballot Issue 4A                                                 |
| 63         | Ballot Issue 4B                                                 |
| 64         | Ballot Issue 5B                                                 |
| 65         | Ballot Issue 6A                                                 |
| 66         | Ballot Issue 6B                                                 |


### Boulder 2020

Boulder has no precinct column, no redacted rows, and summary rows at the botton with vote totals of the redacted ballots, by BallotType.
The summary rows have 0's everywhere (no style info), but its likely we could infer the style by looking at unredacted rows
with the same ballotType. However we still dont know the ncards in each summary row.

````
// (2020) Boulder: has 0's everywhere (no style info). no redacted rows.
// ,,,,,,DS-27,6,3,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3,0,0,1,0,0,0,0,6,2,2,0,0,0,0,0,5,2,2,0,0,0,0,6,3,5,4,0,0,0,0,0,0,0,0,0,6,7,0,5,2,5,2,6,1,6,1,4,3,5,2,5,2,5,2,4,3,4,6,7,3,0,0,0,0,0,0,0,4,5,6,3,5,5,5,5,6,4,6,3,8,2,3,7,6,3,3,6,6,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3
````
* Use single redacted pool.
* Use missing as size of redacted pool.
* Use sum of groups for the pool subtotal.

### Counties with redacted lines

All the counties with redacted line mess up python reader. Rlauxe ignores / removes them.
Dolores and Philips have a subtotal line that rlauxe ignores, but python picks up as a row.