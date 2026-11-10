# Single County Audit

Audit results in one county, not multicounty, so uses the margin and population just from the county.

Should rlauxe remove that county contest? Or duplicate it? Or add it in?

## 2020 General

From "$ac/2020/general/targetContests.xlsx"

| County     | Contest                                        | Vote For | Lowest Winner | Highest Loser | Contest Margin | Diluted Margin      | Risk Limit | Estimated # of CVRs to audit | # of CVRs | Remarks            |
|------------|------------------------------------------------|----------|---------------|---------------|----------------|---------------------|------------|------------------------------|-----------|---------------------|
| Jackson    | Justice of the Colorado Supreme Court - Samour | 1        | 416           | 266           | 150            | 0.16930022573363432 | 0.04       | 39.5156                      | 886       | Single county audit |
| Kiowa      | Proposition 117 (STATUTORY)                    | 1        | 512           | 322           | 190            | 0.2092511013215859  | 0.04       | 31.971157894736844           | 908       | Single county audit |
| Lincoln    | Justice of the Colorado Supreme Court - Hart   | 1        | 1295          | 868           | 427            | 0.16149773071104387 | 0.04       | 41.42473067915691            | 2644      | Single county audit |
| Prowers    | Amendment C (CONSTITUTIONAL)                   | 1        | 2827          | 2380          | 447            | 0.0806131650135257  | 0.04       | 82.98892617449665            | 5545      | Single county audit |
| Teller     | Amendment 77 (CONSTITUTIONAL)                  | 1        | 8968          | 7071          | 1897           | 0.11199669382453654 | 0.04       | 59.73390616763311            | 16938     | Single county audit |
| Washington | Proposition EE (STATUTORY)                     | 1        | 1713          | 1152          | 561            | 0.18939905469277515 | 0.04       | 35.32224598930481            | 2962      | Single county audit |

* Teller doesnt match manifest
* Kiowa off by 1

This is a preliminary count to choose the targeted contests, guess the Estimated # of CVRs to audit; The manifest can be updated, presumably before the dice roll.

Examples:

We switched

````
Jackson: "Justice of the Colorado Supreme Court - Samour" -> "Jackson - Justice of the Colorado Supreme Court - Samour" // diverted target
Kiowa: "Proposition 117 (STATUTORY)" -> "Kiowa - Proposition 117 (STATUTORY)"
Lincoln: "Colorado Supreme Court Justice - Hart" -> "Lincoln - Justice of the Colorado Supreme Court - Hart" // diverted target
Prowers: "Amendment C (CONSTITUTIONAL)" -> "Prowers - Amendment C (CONSTITUTIONAL)" // diverted target
Washington: "Proposition EE (Statutory)" -> "Washington - Proposition EE (STATUTORY)" // diverted target
````

Maybe better to leave it in statewide, because the statewide contests are the sum of the county contests.

Then we can check the "single county audit" using the "county sampling/contests in selected counties" tab?

Where are we using the statewide totals from auditcenter ?? Presumably from the tabulate_county.csv ? 

We should check that the sum of the county tabulations equals the official counts?
And that the county cvrs + missing + redaction = county tabulation.

###

In canonicalTitleCase.csv :

Jackson,Amendment 76 (CONSTITUTIONAL),"Yes/For, No/Against"
Jackson,Amendment 77 (CONSTITUTIONAL),"Yes/For, No/Against"
Jackson,Amendment B (CONSTITUTIONAL),"Yes/For, No/Against"
Jackson,Amendment C (CONSTITUTIONAL),"Yes/For, No/Against"
Jackson,Colorado Court of Appeals Judge - Tow,"Yes, No"
Jackson,Colorado Court of Appeals Judge - Welling,"Yes, No"
Jackson,District Attorney - 8th Judicial District,"Gordon McLaughlin, Mitch Murray"
Jackson,District Court Judge - 8th Judicial District - Blanco,"Yes, No"
Jackson,District Court Judge - 8th Judicial District - Field,"Yes, No"
Jackson,District Court Judge - 8th Judicial District - Jouard,"Yes, No"
Jackson,District Court Judge - 8th Judicial District - Villasenor,"Yes, No"
Jackson,Jackson County Commissioner - District 2,Daniel E Manville
Jackson,Jackson County Commissioner - District 3,Jeff Benson
Jackson,Justice of the Colorado Supreme Court - Hart,"Yes, No"
**Jackson,Jackson - Justice of the Colorado Supreme Court - Samour,"Yes, No"**
Jackson,Presidential Electors,"Joseph R. Biden / Kamala D. Harris, Donald J. Trump / Michael R. Pence, ...
Jackson,Proposition 113 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition 114 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition 115 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition 116 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition 117 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition 118 (STATUTORY),"Yes/For, No/Against"
Jackson,Proposition EE (STATUTORY),"Yes/For, No/Against"
Jackson,Representative to the 117th United States Congress - District 3,"Lauren Boebert, Diane E. Mitsch Bush, John Ryan Keil, Critter Milton"
Jackson,State Board of Education Member - Congressional District 3,"Mayling Simpson, Joyce Rankin"
Jackson,State Representative - District 13,"Kevin Sipple, Judy Amabile, James E. ""Jed"" Gilman"
Jackson,State Senator - District 8,"Karl Hanlon, Bob Rankin"
Jackson,United States Senator,"John W. Hickenlooper, Cory Gardner, Daniel Doyle, Stephan ""Seku"" Evans, Raymon Anthony Doane, Danny Skelly, Bruce Lohmiller, Michael Sanchez"

in contest round:

Jackson - Justice of the Colorado Supreme Court - Samour,county_wide_contest,risk_limit_achieved,1,889,889,"""Yes""",151,0.04000000,41,0,0,0,0,0,0,1.03905000,0,40,40
Justice of the Colorado Supreme Court - Samour,opportunistic_benefits,in_progress,1,4113693,3278291,"""Yes""",1250355,0.04000000,0,0,0,0,0,0,0,1.03905000,0,23,23

(Im guessing that line 2 does not include line 1. But I dont see an offical total anywhere in the archive.)

in tabulate:

Jackson - Justice of the Colorado Supreme Court - Samour,Yes,418
Jackson - Justice of the Colorado Supreme Court - Samour,No,267

Justice of the Colorado Supreme Court - Samour,Yes,1958827
Justice of the Colorado Supreme Court - Samour,No,708472

in county_tabulate.csv : 

Jackson,Jackson County Commissioner - District 2,Daniel E Manville,656
Jackson,Jackson County Commissioner - District 3,Jeff Benson,701
Jackson,Justice of the Colorado Supreme Court - Hart,Yes,435
Jackson,Justice of the Colorado Supreme Court - Hart,No,256
**Jackson,Jackson - Justice of the Colorado Supreme Court - Samour,Yes,418
Jackson,Jackson - Justice of the Colorado Supreme Court - Samour,No,267**

in Jackson cvr schema:

| contestIdx | contestName                                                   | canonName                                                       | match |
|------------|---------------------------------------------------------------|-----------------------------------------------------------------|-------|
| 0          | Presidential Electors                                         | Presidential Electors                                           |       |
| 1          | United States Senator                                         | United States Senator                                           |       |
| 2          | Representative to the 117th United States Congress-District 3 | Representative to the 117th United States Congress - District 3 | *     |
| 3          | State Board of Education Member - Congressional District 3    | State Board of Education Member - Congressional District 3      |       |
| 4          | State Senator - District 8                                    | State Senator - District 8                                      |       |
| 5          | State Representative - District 13                            | State Representative - District 13                              |       |
| 6          | District Attorney--8th Judicial District                      | District Attorney - 8th Judicial District                       | *     |
| 7          | Jackson County Commissioner Dist 2                            | Jackson County Commissioner - District 2                        | *     |
| 8          | Jackson County Commissioner Dist 3                            | Jackson County Commissioner - District 3                        | *     |
| 9          | Justice of the Colorado Supreme Court - Hart                  | Justice of the Colorado Supreme Court - Hart                    |       |
| 10         | **Justice of the Colorado Supreme Court - Samour**                | Jackson - Justice of the Colorado Supreme Court - Samour        | *     |
| 11         | Colorado Court of Appeals Judge - Tow                         | Colorado Court of Appeals Judge - Tow                           |       |
| 12         | Colorado Court of Appeals Judge - Welling                     | Colorado Court of Appeals Judge - Welling                       |       |
| 13         | District Court Judge - 8th Judicial District - Blanco         | District Court Judge - 8th Judicial District - Blanco           |       |
| 14         | District Court Judge - 8th Judicial District - Field          | District Court Judge - 8th Judicial District - Field            |       |
| 15         | District Court Judge - 8th Judicial District - Jouard         | District Court Judge - 8th Judicial District - Jouard           |       |
| 16         | District Court Judge - 8th Judicial District - Villaseñor     | District Court Judge - 8th Judicial District - Villasenor       | *     |
| 17         | Amendment B (CONSTITUTIONAL)                                  | Amendment B (CONSTITUTIONAL)                                    |       |
| 18         | Amendment C (CONSTITUTIONAL)                                  | Amendment C (CONSTITUTIONAL)                                    |       |
| 19         | Amendment 76 (CONSTITUTIONAL)                                 | Amendment 76 (CONSTITUTIONAL)                                   |       |
| 20         | Amendment 77 (CONSTITUTIONAL)                                 | Amendment 77 (CONSTITUTIONAL)                                   |       |
| 21         | Proposition EE (STATUTORY)                                    | Proposition EE (STATUTORY)                                      |       |
| 22         | Proposition 113 (STATUTORY)                                   | Proposition 113 (STATUTORY)                                     |       |
| 23         | Proposition 114 (STATUTORY)                                   | Proposition 114 (STATUTORY)                                     |       |
| 24         | Proposition 115 (STATUTORY)                                   | Proposition 115 (STATUTORY)                                     |       |
| 25         | Proposition 116 (STATUTORY)                                   | Proposition 116 (STATUTORY)                                     |       |
| 26         | Proposition 117 (STATUTORY)                                   | Proposition 117 (STATUTORY)                                     |       |
| 27         | Proposition 118 (STATUTORY)                                   | Proposition 118 (STATUTORY)                                     |       |

in Statewide2020General:

````
    "Jackson" -> when (name) {
            "Jackson County Commissioner Dist 2" -> "Jackson County Commissioner - District 2"
            "Jackson County Commissioner Dist 3" -> "Jackson County Commissioner - District 3"
            "District Court Judge - 8th Judicial District - Villaseñor" -> "District Court Judge - 8th Judicial District - Villasenor"
            "Jackson - Justice of the Colorado Supreme Court - Samour" -> "Justice of the Colorado Supreme Court - Samour" // single county target
            else -> null
````

## 2026 primary

In canonical:

Morgan,Attorney General - DEM (Morgan),"Hetal Doshi,Michael Dougherty,David Seligman,Jena Griswold"
Morgan,Attorney General - REP (Morgan),"David Willson,Michael J. Allen"

county_tabulate:

Morgan,Attorney General - DEM (Morgan),Jena Griswold,765
Morgan,Attorney General - DEM (Morgan),Michael Dougherty,314
Morgan,Attorney General - DEM (Morgan),David Seligman,217
Morgan,Attorney General - DEM (Morgan),Hetal Doshi,173
Morgan,Attorney General - REP (Morgan),Michael J. Allen,1939
Morgan,Attorney General - REP (Morgan),David Willson,1283

in county schema (unmerged):

| contestIdx | contestName                                                           | canonName                                                             | match |
|------------|-----------------------------------------------------------------------|-----------------------------------------------------------------------|-------|
| 0          | United States Senator - DEM                                           | United States Senator - DEM                                           |       |
| 1          | Representative to the 120th United States Congress - District 4 - DEM | Representative to the 120th United States Congress - District 4 - DEM |       |
| 2          | Governor - DEM                                                        | Governor - DEM                                                        |       |
| 3          | Secretary of State - DEM                                              | Secretary of State - DEM                                              |       |
| 4          | State Treasurer - DEM                                                 | State Treasurer - DEM                                                 |       |
| 5          | Attorney General - DEM                                                | Attorney General - DEM                                                |       |
| 6          | State Senator - District 1 - DEM                                      | State Senator - District 1 - DEM                                      |       |
| 7          | Secretary of State - LBR                                              | Secretary of State - LBR                                              |       |
| 8          | United States Senator - REP                                           | United States Senator - REP                                           |       |
| 9          | Representative to the 120th United States Congress - District 4 - REP | Representative to the 120th United States Congress - District 4 - REP |       |
| 10         | Governor - REP                                                        | Governor - REP                                                        |       |
| 11         | Secretary of State - REP                                              | Secretary of State - REP                                              |       |
| 12         | State Treasurer - REP                                                 | State Treasurer - REP                                                 |       |
| 13         | Attorney General - REP                                                | Attorney General - REP                                                |       |
| 14         | State Senator - District 1 - REP                                      | State Senator - District 1 - REP                                      |       |
| 15         | State Representative - District 63 - REP                              | State Representative - District 63 - REP                              |       |
| 16         | Morgan County Commissioner District 2 - REP                           | Morgan County Commissioner District 2 - REP                           |       |
| 17         | Morgan County Clerk and Recorder - REP                                | Morgan County Clerk and Recorder - REP                                |       |
| 18         | Morgan County Treasurer - REP                                         | Morgan County Treasurer - REP                                         |       |
| 19         | Morgan County Assessor - REP                                          | Morgan County Assessor - REP                                          |       |
| 20         | Morgan County Sheriff - REP                                           | Morgan County Sheriff - REP                                           |       |
| 21         | Morgan County Coroner - REP                                           | Morgan County Coroner - REP                                           |       |
| 22         | Governor - UNI                                                        | Governor - UNI                                                        |       |

in targetContestSelection.xlsx

| County      | Contest                                                                            | Vote For | Lowest Winner | Highest Loser | Contest Margin | Diluted Margin       | Risk Limit | Estimated # of CVRs to audit | # of CVR | Remarks             |
|-------------|------------------------------------------------------------------------------------|----------|---------------|---------------|----------------|----------------------|------------|------------------------------|----------|---------------------|
| Adams       | Attorney General - Republican Party                                                | 1        | 14626         | 10845         | 3781           | 0.04482460196085405  | 0.03       | 162                          | 84351    | Single county audit |
| Adams       | State Senator - District 21 - Democratic Party                                     | 1        | 9112          | 5011          | 4101           | 0.04861827364228047  | 0.03       | 149                          | 22271    | Single county audit |
| Alamosa     | Attorney General - Republican Party                                                | 1        | 885           | 541           | 344            | 0.10017472335468841  | 0.03       | 72                           | 3434     | Single county audit |
| Arapahoe    | Attorney General - Republican Party                                                | 1        | 15616         | 10357         | 5259           | 0.058861715820695056 | 0.03       | 123                          | 89345    | Single county audit |
| Archuleta   | Attorney General - Democratic Party                                                | 1        | 1010          | 284           | 726            | 0.15813548246569376  | 0.03       | 46                           | 4591     | Single county audit |
| Bent        | Governor - Democratic Party                                                        | 1        | 233           | 85            | 148            | 0.13962264150943396  | 0.03       | 52                           | 1060     | Single county audit |
| Bent        | Attorney General - Republican Party                                                | 1        | 369           | 231           | 138            | 0.13018867924528302  | 0.03       | 55                           | 1060     | Single county audit |
| Boulder     | Attorney General - Democratic Party                                                | 1        | 22274         | 18019         | 4255           | 0.0626795315607277   | 0.03       | 116                          | 67885    | Single county audit |
| Boulder     | Representative to the 120th United States Congress - District 2 - Republican Party | 1        | 4984          | 3025          | 1959           | 0.02885762686896958  | 0.03       | 252                          | 67881    | Single county audit |
| Broomfield  | Attorney General - Republican Party                                                | 1        | 3287          | 2260          | 1027           | 0.04547668600274543  | 0.03       | 160                          | 22583    | Single county audit |
| Broomfield  | State Representative - District 33 - Democratic Party                              | 1        | 8684          | 7117          | 1567           | 0.06938847805871673  | 0.03       | 105                          | 22581    | Single county audit |
| Chaffee     | Secretary of State - Democratic Party                                              | 1        | 2575          | 1550          | 1025           | 0.13836393088552915  | 0.03       | 52                           | 7408     | Single county audit |
| Chaffee     | Regent of the University of Colorado - Congressional District 7 - Republican Party | 1        | 1365          | 704           | 661            | 0.08922786177105832  | 0.03       | 81                           | 7408     | Single county audit |
| Cheyenne    | Governor - Republican Party                                                        | 1        | 256           | 189           | 67             | 0.11551724137931034  | 0.03       | 63                           | 580      | Single county audit |
| Clear Creek | Representative to the 120th United States Congress - District 2 - Republican Party | 1        | 439           | 278           | 161            | 0.06284153005464481  | 0.03       | 115                          | 2559     | Single county audit |
| Clear Creek | Secretary of State - Democratic Party                                              | 1        | 952           | 551           | 401            | 0.15651834504293521  | 0.03       | 46                           | 2562     | Single county audit |
| Conejos     | Governor - Democratic Party                                                        | 1        | 705           | 399           | 306            | 0.1244408296055307   | 0.03       | 58                           | 2459     | Single county audit |
| Costilla    | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 195           | 119           | 76             | 0.054285714285714284 | 0.03       | 134                          | 1400     | Single county audit |
| Crowley     | Governor - Republican Party                                                        | 1        | 352           | 149           | 203            | 0.22937853107344633  | 0.03       | 31                           | 885      | Single county audit |
| Crowley     | Attorney General - Democratic Party                                                | 1        | 158           | 31            | 127            | 0.14350282485875707  | 0.03       | 50                           | 885      | Single county audit |
| Custer      | Attorney General - Democratic Party                                                | 1        | 415           | 95            | 320            | 0.1620253164556962   | 0.03       | 44                           | 1975     | Single county audit |
| Custer      | Regent of the University of Colorado - Congressional District 7 - Republican Party | 1        | 598           | 409           | 189            | 0.09569620253164557  | 0.03       | 76                           | 1973     | Single county audit |
| Delta       | Governor - Republican Party                                                        | 1        | 3347          | 1682          | 1665           | 0.17085684966649564  | 0.03       | 42                           | 9745     | Single county audit |
| Delta       | Secretary of State - Democratic Party                                              | 1        | 1850          | 1105          | 745            | 0.07644946126218574  | 0.03       | 95                           | 9745     | Single county audit |
| Denver      | Governor - Republican Party                                                        | 1        | 8132          | 4626          | 3506           | 0.023326990379113493 | 0.03       | 312                          | 150298   | Single county audit |
| Dolores     | Governor - Republican Party                                                        | 1        | 196           | 132           | 64             | 0.10015649452269171  | 0.03       | 72                           | 639      | Single county audit |
| Douglas     | Governor - Democratic Party                                                        | 1        | 26439         | 23569         | 2870           | 0.02820084504274344  | 0.03       | 258                          | 50645    | Single county audit |
| Eagle       | Attorney General - Republican Party                                                | 1        | 1344          | 1056          | 288            | 0.028059236165237724 | 0.03       | 259                          | 10264    | Single county audit |
| Elbert      | Attorney General - Democratic Party                                                | 1        | 1074          | 398           | 676            | 0.08048577211572806  | 0.03       | 90                           | 2067     | Single county audit |
| Elbert      | Attorney General - Republican Party                                                | 1        | 3149          | 2438          | 711            | 0.08465293487319919  | 0.03       | 86                           | 6306     | Single county audit |
| Fremont     | Secretary of State - Democratic Party                                              | 1        | 2441          | 1336          | 1105           | 0.08446720684910564  | 0.03       | 86                           | 13082    | Single county audit |
| Fremont     | Attorney General - Republican Party                                                | 1        | 4743          | 3229          | 1514           | 0.11573153951995108  | 0.03       | 62                           | 13082    | Single county audit |
| Garfield    | Secretary of State - Democratic Party                                              | 1        | 2651          | 1961          | 690            | 0.06079295154185022  | 0.03       | 119                          | 5200     | Single county audit |
| Garfield    | Attorney General - Republican Party                                                | 1        | 2746          | 2288          | 458            | 0.040352422907488986 | 0.03       | 180                          | 6150     | Single county audit |
| Gilpin      | Attorney General - Democratic Party                                                | 1        | 486           | 258           | 228            | 0.12271259418729817  | 0.03       | 59                           | 1174     | Single county audit |
| Gilpin      | Attorney General - Republican Party                                                | 1        | 372           | 229           | 143            | 0.07696447793326157  | 0.03       | 94                           | 680      | Single county audit |
| Grand       | Secretary of State - Democratic Party                                              | 1        | 1254          | 802           | 452            | 0.09936249725214333  | 0.03       | 73                           | 2374     | Single county audit |
| Grand       | Attorney General - Republican Party                                                | 1        | 1066          | 749           | 317            | 0.06968564519674654  | 0.03       | 104                          | 2165     | Single county audit |
| Gunnison    | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 801           | 442           | 359            | 0.08018762564217109  | 0.03       | 90                           | 4477     | Single county audit |
| Gunnison    | Secretary of State - Democratic Party                                              | 1        | 1580          | 1129          | 451            | 0.10073710073710074  | 0.03       | 72                           | 4477     | Single county audit |
| Hinsdale    | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 103           | 81            | 22             | 0.07457627118644068  | 0.03       | 97                           | 295      | Single county audit |
| Huerfano    | Governor - Democratic Party                                                        | 1        | 728           | 487           | 241            | 0.10460069444444445  | 0.03       | 69                           | 2304     | Single county audit |
| Jefferson   | Governor - Republican Party                                                        | 1        | 23874         | 18717         | 5157           | 0.030752441947833553 | 0.03       | 236                          | 167694   | Single county audit |
| Kit Carson  | Attorney General - Republican Party                                                | 1        | 729           | 535           | 194            | 0.10532030401737243  | 0.03       | 69                           | 1842     | Single county audit |
| La Plata    | Representative to the 120th United States Congress - District 3 - Democratic Party | 1        | 5322          | 4395          | 927            | 0.05775700934579439  | 0.03       | 126                          | 16050    | Single county audit |
| La Plata    | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 3548          | 1819          | 1729           | 0.10772585669781931  | 0.03       | 67                           | 16050    | Single county audit |
| Larimer     | Governor - Democratic Party                                                        | 1        | 27058         | 21427         | 5631           | 0.07397238679505537  | 0.03       | 98                           | 76123    | Single county audit |
| Las Animas  | Representative to the 120th United States Congress - District 3 - Democratic Party | 1        | 1183          | 791           | 392            | 0.09914011127971674  | 0.03       | 73                           | 3954     | Single county audit |
| Las Animas  | Attorney General - Republican Party                                                | 1        | 891           | 597           | 294            | 0.07435508345978756  | 0.03       | 98                           | 3954     | Single county audit |
| Lincoln     | Attorney General - Republican Party                                                | 1        | 554           | 312           | 242            | 0.18558282208588958  | 0.03       | 39                           | 1304     | Single county audit |
| Logan       | Attorney General - Democratic Party                                                | 1        | 610           | 229           | 381            | 0.07896373056994818  | 0.03       | 92                           | 4825     | Single county audit |
| Logan       | Attorney General - Republican Party                                                | 1        | 1954          | 1235          | 719            | 0.1490155440414508   | 0.03       | 48                           | 4825     | Single county audit |
| Mesa        | Governor - Democratic Party                                                        | 1        | 9865          | 6906          | 2959           | 0.06727446344125136  | 0.03       | 108                          | 43984    | Single county audit |
| Mesa        | Attorney General - Republican Party                                                | 1        | 13675         | 10010         | 3665           | 0.08332575481993452  | 0.03       | 87                           | 43984    | Single county audit |
| Mineral     | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 112           | 68            | 44             | 0.1111111111111111   | 0.03       | 65                           | 396      | Single county audit |
| Mineral     | Attorney General - Democratic Party                                                | 1        | 100           | 33            | 67             | 0.1691919191919192   | 0.03       | 43                           | 396      | Single county audit |
| Montezuma   | Attorney General - Democratic Party                                                | 1        | 1216          | 349           | 867            | 0.14697406340057637  | 0.03       | 49                           | 5899     | Single county audit |
| Montrose    | Governor - Republican Party                                                        | 1        | 3632          | 2070          | 1562           | 0.12751020408163266  | 0.03       | 57                           | 12250    | Single county audit |
| Montrose    | Secretary of State - Democratic Party                                              | 1        | 2554          | 1532          | 1022           | 0.08342857142857144  | 0.03       | 87                           | 12250    | Single county audit |
| Morgan      | Attorney General - Democratic Party                                                | 1        | 764           | 312           | 452            | 0.0867395893302629   | 0.03       | 84                           | 5211     | Single county audit |
| Morgan      | Attorney General - Republican Party                                                | 1        | 1935          | 1282          | 653            | 0.12531184033774706  | 0.03       | 58                           | 5211     | Single county audit |
| Otero       | Representative to the 120th United States Congress - District 3 - Democratic Party | 1        | 973           | 527           | 446            | 0.10958230958230958  | 0.03       | 66                           | 1601     | Single county audit |
| Otero       | Governor - Republican Party                                                        | 1        | 1226          | 630           | 596            | 0.14643734643734643  | 0.03       | 49                           | 2464     | Single county audit |
| Ouray       | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 421           | 276           | 145            | 0.0725               | 0.03       | 100                          | 2000     | Single county audit |
| Ouray       | Governor - Democratic Party                                                        | 1        | 699           | 575           | 124            | 0.062                | 0.03       | 117                          | 2000     | Single county audit |
| Park        | Secretary of State - Democratic Party                                              | 1        | 1325          | 787           | 538            | 0.09185589892436401  | 0.03       | 79                           | 5857     | Single county audit |
| Phillips    | Attorney General - Republican Party                                                | 1        | 494           | 334           | 160            | 0.12432012432012432  | 0.03       | 58                           | 1287     | Single county audit |
| Pitkin      | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 584           | 232           | 352            | 0.07177814029363784  | 0.03       | 101                          | 4904     | Single county audit |
| Pitkin      | Secretary of State - Democratic Party                                              | 1        | 1725          | 1413          | 312            | 0.0636215334420881   | 0.03       | 114                          | 4904     | Single county audit |
| Prowers     | Attorney General - Democratic Party                                                | 1        | 324           | 99            | 225            | 0.09911894273127753  | 0.03       | 73                           | 2270     | Single county audit |
| Prowers     | Attorney General - Republican Party                                                | 1        | 837           | 565           | 272            | 0.1198237885462555   | 0.03       | 60                           | 2270     | Single county audit |
| Pueblo      | Attorney General - Republican Party                                                | 1        | 8642          | 5651          | 2991           | 0.07692901234567902  | 0.03       | 94                           | 38880    | Single county audit |
| Rio Grande  | Attorney General - Democratic Party                                                | 1        | 623           | 267           | 356            | 0.1136653895274585   | 0.03       | 64                           | 3132     | Single county audit |
| Routt       | Attorney General - Republican Party                                                | 1        | 844           | 570           | 274            | 0.04407270387646775  | 0.03       | 165                          | 6217     | Single county audit |
| Routt       | Regent of the University of Colorado - Congressional District 2 - Democratic Party | 1        | 1244          | 927           | 317            | 0.05098922309795721  | 0.03       | 142                          | 6211     | Single county audit |
| Saguache    | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 352           | 206           | 146            | 0.08984615384615384  | 0.03       | 81                           | 1625     | Single county audit |
| Saguache    | Secretary of State - Democratic Party                                              | 1        | 608           | 323           | 285            | 0.1753846153846154   | 0.03       | 41                           | 1625     | Single county audit |
| San Miguel  | Representative to the 120th United States Congress - District 3 - Democratic Party | 1        | 720           | 569           | 151            | 0.07947368421052632  | 0.03       | 91                           | 1896     | Single county audit |
| San Miguel  | Representative to the 120th United States Congress - District 3 - Republican Party | 1        | 219           | 113           | 106            | 0.05578947368421053  | 0.03       | 130                          | 1896     | Single county audit |
| Sedgwick    | Attorney General - Republican Party                                                | 1        | 299           | 186           | 113            | 0.14160401002506265  | 0.03       | 51                           | 798      | Single county audit |
| Summit      | Secretary of State - Democratic Party                                              | 1        | 2463          | 1820          | 643            | 0.09569876469712754  | 0.03       | 76                           | 6719     | Single county audit |
| Summit      | Attorney General - Republican Party                                                | 1        | 791           | 457           | 334            | 0.049709778240809646 | 0.03       | 146                          | 6719     | Single county audit |
| Teller      | Governor - Republican Party                                                        | 1        | 2307          | 1488          | 819            | 0.10392082223068139  | 0.03       | 70                           | 7881     | Single county audit |
| Teller      | Secretary of State - Democratic Party                                              | 1        | 1573          | 924           | 649            | 0.0823499555893922   | 0.03       | 88                           | 7881     | Single county audit |
| Weld        | Attorney General - Republican Party                                                | 1        | 17179         | 12344         | 4835           | 0.08024230354327441  | 0.03       | 90                           | 60255    | Single county audit |
| Weld        | State Representative - District 19 - Democratic Party                              | 1        | 3691          | 2131          | 1560           | 0.025889967637540454 | 0.03       | 281                          | 13382    | Single county audit |
| Yuma        | Attorney General - Republican Party                                                | 1        | 1143          | 777           | 366            | 0.1301564722617354   | 0.03       | 55                           | 2812     | Single county audit |


In Merged:

````
    override fun contestNameCleanup(county: String, name: String): String {
        return contestNameMerge(name)
    }
    fun contestNameMerge(contestName: String): String {
        var name = merge(contestName, "Attorney General - DEM")
        name = merge(name, "Attorney General - REP")
        name = merge(name, "Governor - DEM")
        name = merge(name, "Governor - REP")
        name = merge(name, "Regent of the University of Colorado - Congressional District 2 - DEM")
        name = merge(name, "Regent of the University of Colorado - Congressional District 2 - REP")
        name = merge(name, "Regent of the University of Colorado - Congressional District 7 - REP")
        name = merge(name, "Representative to the 120th United States Congress - District 2 - REP")
        name = merge(name, "Representative to the 120th United States Congress - District 3 - DEM")
        name = merge(name, "Representative to the 120th United States Congress - District 3 - REP")
        name = merge(name, "Secretary of State - DEM")
        name = merge(name, "State Representative - District 19 - DEM")
        name = merge(name, "State Representative - District 33 - DEM")
        name = merge(name, "State Senator - District 21 - DEM")
        return name
    }

    fun merge(contest: String, root: String): String {
        return if (contest.startsWith(root)) root else contest
    }
````


## 2026 General

| County      | Contest                                                         | Vote For | Lowest Winner | Highest Loser | Contest Margin | Diluted Margin       | Risk Limit | Estimated # of CVRs to audit | # of CVRs | Remarks                                            |
|-------------|-----------------------------------------------------------------|----------|---------------|---------------|----------------|----------------------|------------|------------------------------|-----------|----------------------------------------------------|
| Colorado    | United States Senator                                           | 1        | 1425116       | 853660        | 571456         | 0.1426316530372024   | 0.03       | 51                           | 4006516   | Audited in all 64 counties                         |
| Colorado    | Governor / Lieutenant Governor                                  | 1        | 1434277       | 805213        | 629064         | 0.15701023033478465  | 0.03       | 46                           | 4006516   | Audited in all 64 counties                         |
| Colorado    | Secretary of State                                              | 1        | 1397818       | 884710        | 513108         | 0.128068376614495    | 0.03       | 56                           | 4006516   | Audited in all 64 counties                         |
| Colorado    | State Treasurer                                                 | 1        | 1357771       | 921018        | 436753         | 0.10901067161593764  | 0.03       | 66                           | 4006516   | Audited in all 64 counties                         |
| Colorado    | Attorney General                                                | 1        | 1362056       | 979727        | 382329         | 0.0954267997432183   | 0.03       | 76                           | 4006516   | Audited in all 64 counties                         |
| Adams       | State Senator - District 24                                     | 1        | 34420         | 21466         | 12954          | 0.03930886315636152  | 0.03       | 185                          | 115542    | 2 ballot cards per ballot*                         |
| Alamosa     | City of Alamosa Ballot Issue 2A                                 | 1        | 1676          | 1239          | 437            | 0.0705521472392638   | 0.03       | 103                          | 6171      |                                                    |
| Arapahoe    | Arapahoe County Commissioner - District 2                       | 1        | 31327         | 19435         | 11892          | 0.02780245574331591  | 0.03       | 262                          | 105986    | 2 ballot cards per ballot*                         |
| Archuleta   | Archuleta County Ballot Issue 1A                                | 1        | 4231          | 1928          | 2303           | 0.3606891151135474   | 0.03       | 20                           | 6385      |                                                    |
| Baca        | Colorado Supreme Court Justice - Hood                           | 1        | 675           | 533           | 142            | 0.09085092770313499  | 0.03       | 80                           | 1563      | Single county audit                                |
| Bent        | Proposition 137 (STATUTORY)                                     | 1        | 926           | 640           | 286            | 0.17218543046357615  | 0.03       | 42                           | 1661      | Single county audit                                |
| Boulder     | Boulder County Ballot Issue 1A                                  | 1        | 77272         | 55358         | 21914          | 0.07888465719695606  | 0.03       | 92                           | 277756    | 2 ballot cards per ballot*                         |
| Boulder     | Front Range Passenger Rail District Ballot Issue 7A             | 1        | 63106         | 38145         | 24961          | 0.08985305869732683  | 0.03       | 81                           | 277756    | "Single county audit 2 ballot cards per ballot*"   |
| Broomfield  | Proposition 132 (STATUTORY)                                     | 1        | 21422         | 18139         | 3283           | 0.08035736140007343  | 0.03       | 90                           | 40855     | Single county audit                                |
| Chaffee     | Chaffee County Commissioner - District 3                        | 1        | 7346          | 5259          | 2087           | 0.1612953087564727   | 0.03       | 45                           | 12932     |                                                    |
| Cheyenne    | Colorado Court of Appeals Judge - Harris                        | 1        | 397           | 302           | 95             | 0.1111111111111111   | 0.03       | 65                           | 855       | Single county audit                                |
| Clear Creek | Clear Creek County Ballot Issue 1A                              | 1        | 2302          | 2047          | 255            | 0.05024630541871921  | 0.03       | 145                          | 4497      |                                                    |
| Conejos     | Conejos County Sheriff                                          | 1        | 2112          | 1283          | 829            | 0.23544447600113605  | 0.03       | 30                           | 3521      |                                                    |
| Costilla    | Costilla County Sheriff                                         | 1        | 989           | 613           | 376            | 0.19789473684210526  | 0.03       | 36                           | 1900      |                                                    |
| Crowley     | County Court Judge - Crowley - Boyce                            | 1        | 669           | 506           | 163            | 0.1215510812826249   | 0.03       | 59                           | 1340      |                                                    |
| Custer      | Proposition 137 (STATUTORY)                                     | 1        | 1738          | 1358          | 380            | 0.11875              | 0.03       | 61                           | 3200      | Single county audit                                |
| Delta       | Colorado Court of Appeals Judge - Moultrie                      | 1        | 7946          | 5243          | 2703           | 0.10981109079829372  | 0.03       | 66                           | 24615     | "Single county audit 1.5 ballot cards per ballot*" |
| Denver      | City and County of Denver Councilmember - At-Large -Seat B      | 1        | 56536         | 31521         | 25015          | 0.04961600406210579  | 0.03       | 146                          | 503576    | 2 ballot cards per ballot*                         |
| Denver      | Front Range Passenger Rail District Ballot Issue 7A             | 1        | 155710        | 81332         | 74378          | 0.14752505097466737  | 0.03       | 49                           | 503576    | "Single county audit 2 ballot cards per ballot*"   |
| Dolores     | Dolores County Sheriff                                          | 1        | 702           | 469           | 233            | 0.19020408163265307  | 0.03       | 38                           | 1225      |                                                    |
| Douglas     | Douglas County Ballot Question 200                              | 1        | 99283         | 91282         | 8001           | 0.039496872732298974 | 0.03       | 184                          | 202336    |                                                    |
| Eagle       | Eagle County Sheriff                                            | 1        | 13308         | 9320          | 3988           | 0.0847212780421482   | 0.03       | 86                           | 47038     | 2 ballot cards per ballot*                         |
| El Paso     | Representative to the 120th United States Congress - District 5 | 1        | 140713        | 127946        | 12767          | 0.022232632007885122 | 0.03       | 327                          | 566056    | 2 ballot cards per ballot*                         |
| Elbert      | District Court Judge - 23rd Judicial District - Slade           | 1        | 7910          | 6038          | 1872           | 0.10789625360230548  | 0.03       | 67                           | 17350     | Single county audit                                |
| Fremont     | County Court Judge - Fremont - Robak                            | 1        | 9011          | 7622          | 1389           | 0.069087291718478    | 0.03       | 105                          | 20100     |                                                    |
| Garfield    | Colorado Court of Appeals Judge - Sullivan                      | 1        | 12289         | 6677          | 5612           | 0.1102510706848454   | 0.03       | 66                           | 50874     | "Single county audit 2 ballot cards per ballot*"   |
| Gilpin      | Gilpin County Clerk and Recorder                                | 1        | 1918          | 1379          | 539            | 0.07786766830395839  | 0.03       | 93                           | 6918      | 2 ballot cards per ballot*                         |
| Grand       | Grand County Clerk and Recorder                                 | 1        | 4544          | 3972          | 572            | 0.06956944782291413  | 0.03       | 104                          | 8814      |                                                    |
| Gunnison    | Proposition 135 (STATUTORY)                                     | 1        | 5079          | 4089          | 990            | 0.10322177040975915  | 0.03       | 70                           | 9558      | Single county audit                                |
| Hinsdale    | District Court Judge - 7th Judicial District - Jackson          | 1        | 197           | 112           | 85             | 0.16569200779727095  | 0.03       | 43                           | 513       | Single county audit                                |
| Huerfano    | Proposition 136 (STATUTORY)                                     | 1        | 2091          | 1536          | 555            | 0.14517394716191473  | 0.03       | 50                           | 3820      | Single county audit                                |
| Jackson     | Jackson County Assessor                                         | 1        | 389           | 234           | 155            | 0.23099850968703428  | 0.03       | 31                           | 671       |                                                    |
| Jefferson   | State Senator - District 20                                     | 1        | 50117         | 33081         | 17036          | 0.03006802192798218  | 0.03       | 242                          | 174578    | 2 ballot cards per ballot*                         |
| Kiowa       | Kiowa County Ballot Issue 1B                                    | 1        | 426           | 265           | 161            | 0.22268326417704012  | 0.03       | 32                           | 723       |                                                    |
| Kit Carson  | Kit Carson County Assessor                                      | 1        | 1656          | 1190          | 466            | 0.15764546684709066  | 0.03       | 46                           | 2954      |                                                    |
| La Plata    | La Plata County Treasurer                                       | 1        | 14379         | 11237         | 3142           | 0.10357672655348607  | 0.03       | 70                           | 30292     |                                                    |
| Lake        | Lake County Commissioner - District 1                           | 1        | 1735          | 1184          | 551            | 0.16073512252042008  | 0.03       | 45                           | 3425      |                                                    |
| Larimer     | State Representative - District 52                              | 1        | 27417         | 11389         | 16028          | 0.04727577337832417  | 0.03       | 154                          | 85270     | 2 ballot cards per ballot*                         |
| Larimer     | Front Range Passenger Rail District Ballot Issue 7A             | 1        | 60947         | 44394         | 16553          | 0.04882429977111305  | 0.03       | 149                          | 109129    | Single county audit                                |
| Las Animas  | Las Animas County Treasurer                                     | 1        | 3526          | 2894          | 632            | 0.0952094004218138   | 0.03       | 76                           | 6628      |                                                    |
| Lincoln     | Lincoln County Ballot Issue 1A                                  | 1        | 1070          | 815           | 255            | 0.13030148185998977  | 0.03       | 55                           | 1955      |                                                    |
| Logan       | City of Sterling Referred Ballot Issue 2A                       | 1        | 1960          | 1592          | 368            | 0.046635407426181726 | 0.03       | 156                          | 3635      |                                                    |
| Mesa        | City of Grand Junction Referred Measure 2B                      | 1        | 17444         | 13833         | 3611           | 0.04680431880338557  | 0.03       | 155                          | 32651     |                                                    |
| Mineral     | Mineral County Coroner                                          | 1        | 414           | 212           | 202            | 0.3098159509202454   | 0.03       | 23                           | 651       |                                                    |
| Moffat      | County Court Judge - Moffat - Hesson                            | 1        | 2396          | 1829          | 567            | 0.11214398734177215  | 0.03       | 64                           | 5053      |                                                    |
| Montezuma   | Montezuma County Commissioner - District 1                      | 1        | 6033          | 4560          | 1473           | 0.1363888888888889   | 0.03       | 53                           | 10797     |                                                    |
| Montrose    | City of Montrose Ballot Question 2B                             | 1        | 5692          | 3413          | 2279           | 0.11568527918781726  | 0.03       | 62                           | 9637      |                                                    |
| Morgan      | Proposition 137 (STATUTORY)                                     | 1        | 5725          | 4127          | 1598           | 0.07707891182712714  | 0.03       | 94                           | 20732     | "Single county audit 2 ballot cards per ballot*"   |
| Otero       | Otero County Sheriff                                            | 1        | 3851          | 2781          | 1070           | 0.16056422569027612  | 0.03       | 45                           | 7154      |                                                    |
| Ouray       | Ouray County Ballot Issue 1A                                    | 1        | 1900          | 1449          | 451            | 0.12615384615384614  | 0.03       | 57                           | 3575      |                                                    |
| Park        | Park County Commissioner - District 3                           | 1        | 5228          | 4076          | 1152           | 0.11986265737176152  | 0.03       | 60                           | 9605      |                                                    |
| Phillips    | City of Holyoke Ballot Measure 2B                               | 1        | 624           | 147           | 477            | 0.24562306900102987  | 0.03       | 29                           | 802       |                                                    |
| Pitkin      | Pitkin County Commissioner - District 1                         | 1        | 4951          | 3417          | 1534           | 0.08334238835162447  | 0.03       | 87                           | 18390     | 2 ballot cards per ballot*                         |
| Prowers     | Colorado Court of Appeals Judge - Lum                           | 1        | 1788          | 1435          | 353            | 0.08720355731225296  | 0.03       | 83                           | 4048      | Single county audit                                |
| Pueblo      | State Senator - District 3                                      | 1        | 35492         | 29754         | 5738           | 0.08602053819054044  | 0.03       | 84                           | 66697     |                                                    |
| Rio Blanco  | Rio Blanco County Ballot Question 1C                            | 1        | 1648          | 1206          | 442            | 0.14753004005340453  | 0.03       | 49                           | 2994      |                                                    |
| Rio Grande  | Rio Grande County Sheriff                                       | 1        | 2981          | 1724          | 1257           | 0.2560081466395112   | 0.03       | 28                           | 4910      |                                                    |
| Routt       | Proposition 135 (STATUTORY)                                     | 1        | 7088          | 6297          | 791            | 0.05692285549798503  | 0.03       | 128                          | 13896     | Single county audit                                |
| Saguache    | Saguache County Commissioner - District 3                       | 1        | 1594          | 1134          | 460            | 0.16428571428571428  | 0.03       | 44                           | 2797      |                                                    |
| San Miguel  | San Miguel County Sheriff                                       | 1        | 2135          | 1791          | 344            | 0.08289156626506024  | 0.03       | 87                           | 4146      |                                                    |
| Sedgwick    | Sedgwick County Ballot Issue 1A                                 | 1        | 626           | 439           | 187            | 0.16984559491371481  | 0.03       | 42                           | 1101      |                                                    |
| Summit      | Summit School District RE-1 Ballot Issue 4A                     | 1        | 7435          | 6670          | 765            | 0.051678713774234955 | 0.03       | 141                          | 14681     |                                                    |
| Teller      | City of Woodland Park Ballot Issue 2B                           | 1        | 2845          | 1408          | 1437           | 0.10356756756756756  | 0.03       | 70                           | 4389      |                                                    |
| Washington  | District Court Judge - 13th Judicial District - Christiansen    | 1        | 1046          | 774           | 272            | 0.1141418380193034   | 0.03       | 63                           | 2258      | Single county audit                                |
| Weld        | Weld County Council - At-Large                                  | 1        | 52020         | 44227         | 7793           | 0.05387114613576662  | 0.03       | 135                          | 144545    |                                                    |
| Yuma        | District Court Judge - 13th Judicial District - Christiansen    | 1        | 1790          | 1295          | 495            | 0.13050355918797785  | 0.03       | 55                           | 3787      | Single county audit                                |

    targetContestsFile = "$ac/2026/general/files/2026GeneralRLAContestList.xlsx",

| County     | Contest                                                      | Vote For | Lowest Winner | Highest Loser | Contest Margin | Diluted Margin      | Risk Limit | Estimated # of CVRs to audit | # of CVRs | Remarks                                            |
|------------|--------------------------------------------------------------|----------|---------------|---------------|----------------|---------------------|------------|------------------------------|-----------|----------------------------------------------------|
| Baca       | Colorado Supreme Court Justice - Hood                        | 1        | 675           | 533           | 142            | 0.09085092770313499 | 0.03       | 80                           | 1563      | Single county audit                                |
| Bent       | Proposition 137 (STATUTORY)                                  | 1        | 926           | 640           | 286            | 0.17218543046357615 | 0.03       | 42                           | 1661      | Single county audit                                |
| Broomfield | Proposition 132 (STATUTORY)                                  | 1        | 21422         | 18139         | 3283           | 0.08035736140007343 | 0.03       | 90                           | 40855     | Single county audit                                |
| Cheyenne   | Colorado Court of Appeals Judge - Harris                     | 1        | 397           | 302           | 95             | 0.1111111111111111  | 0.03       | 65                           | 855       | Single county audit                                |
| Custer     | Proposition 137 (STATUTORY)                                  | 1        | 1738          | 1358          | 380            | 0.11875             | 0.03       | 61                           | 3200      | Single county audit                                |
| Elbert     | District Court Judge - 23rd Judicial District - Slade        | 1        | 7910          | 6038          | 1872           | 0.10789625360230548 | 0.03       | 67                           | 17350     | Single county audit                                |
| Gunnison   | Proposition 135 (STATUTORY)                                  | 1        | 5079          | 4089          | 990            | 0.10322177040975915 | 0.03       | 70                           | 9558      | Single county audit                                |
| Hinsdale   | District Court Judge - 7th Judicial District - Jackson       | 1        | 197           | 112           | 85             | 0.16569200779727095 | 0.03       | 43                           | 513       | Single county audit                                |
| Huerfano   | Proposition 136 (STATUTORY)                                  | 1        | 2091          | 1536          | 555            | 0.14517394716191473 | 0.03       | 50                           | 3820      | Single county audit                                |
| Larimer    | Front Range Passenger Rail District Ballot Issue 7A          | 1        | 60947         | 44394         | 16553          | 0.04882429977111305 | 0.03       | 149                          | 109129    | Single county audit                                |
| Prowers    | Colorado Court of Appeals Judge - Lum                        | 1        | 1788          | 1435          | 353            | 0.08720355731225296 | 0.03       | 83                           | 4048      | Single county audit                                |
| Routt      | Proposition 135 (STATUTORY)                                  | 1        | 7088          | 6297          | 791            | 0.05692285549798503 | 0.03       | 128                          | 13896     | Single county audit                                |
| Washington | District Court Judge - 13th Judicial District - Christiansen | 1        | 1046          | 774           | 272            | 0.1141418380193034  | 0.03       | 63                           | 2258      | Single county audit                                |
| Yuma       | District Court Judge - 13th Judicial District - Christiansen | 1        | 1790          | 1295          | 495            | 0.13050355918797785 | 0.03       | 55                           | 3787      | Single county audit                                |
| Delta      | Colorado Court of Appeals Judge - Moultrie                   | 1        | 7946          | 5243          | 2703           | 0.10981109079829372 | 0.03       | 66                           | 24615     | "Single county audit 1.5 ballot cards per ballot*" |
| Boulder    | Front Range Passenger Rail District Ballot Issue 7A          | 1        | 63106         | 38145         | 24961          | 0.08985305869732683 | 0.03       | 81                           | 277756    | "Single county audit 2 ballot cards per ballot*"   |
| Denver     | Front Range Passenger Rail District Ballot Issue 7A          | 1        | 155710        | 81332         | 74378          | 0.14752505097466737 | 0.03       | 49                           | 503576    | "Single county audit 2 ballot cards per ballot*"   |
| Garfield   | Colorado Court of Appeals Judge - Sullivan                   | 1        | 12289         | 6677          | 5612           | 0.1102510706848454  | 0.03       | 66                           | 50874     | "Single county audit 2 ballot cards per ballot*"   |
| Morgan     | Proposition 137 (STATUTORY)                                  | 1        | 5725          | 4127          | 1598           | 0.07707891182712714 | 0.03       | 94                           | 20732     | "Single county audit 2 ballot cards per ballot*"   |

Smallest margin is .0222 Congress district 5; looks like its contained in El Paso county. Other than single county audits, no multi county audits AFAICS.

Note Ballot Isssue 7A in Boulder, Denver, Larimer