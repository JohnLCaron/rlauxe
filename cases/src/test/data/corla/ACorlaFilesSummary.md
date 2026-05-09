# Corla files

much more complete sets of files at https://github.com/nealmcb/auditcenter

also see https://github.com/nealmcb/auditcenter_analyze

## Files
AuditCenter
Apr 24, 2026
------------
Files downloaded from:

https://www.coloradosos.gov/pubs/elections/auditCenter.html
https://www.coloradosos.gov/pubs/elections/resultsData.html
https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary

https://www.coloradosos.gov/pubs/elections/RLA/2024/general/targetedContests.xlsx
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/batchCountComparison.csv
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/contestsByCounty.csv
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/tabulate.csv            (Candidate vote totals summary)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/tabulateCounty.csv      (Candidate vote totals by county)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/2024GeneralCanonicalList.csv
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/seed.csv
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/2024GEDiscrepancyReport.xlsx

By County:

https://www.coloradosos.gov/pubs/elections/RLA/2024/general/countyManifest.html
    https://www.coloradosos.gov/pubs/elections/RLA/2024/general/ballotManifests/AdamsBallotManifest.csv
    https://www.coloradosos.gov/pubs/elections/RLA/2024/general/ballotManifests/BoulderBallotManifest.csv

https://www.coloradosos.gov/pubs/elections/RLA/2024/general/finalReport/finalReports.html
    https://www.coloradosos.gov/pubs/elections/RLA/2024/general/finalReport/Adams.pdf
    https://www.coloradosos.gov/pubs/elections/RLA/2024/general/finalReport/Boulder.pdf

By Round:

https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contestSelection.csv     (Contest margin and contest CVR IDs)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contest.csv              (Contests list)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/20241120stateGeneral11052024report205PM.xlsx  (State audit report)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/ActivityReport.xlsx      (Audit activity report)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contestComparison.csv    (CVR to audit board interpretation comparison)
https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/ResultsReport.xlsx       (Audit results report)

## Summary (05/08/2026)

1. Contest Formation
   * contestTabsByCounty (readCountyTabulateCsv(tabulateCounty.csv)) to get all contests' tabulations by county.
   * resultsContests (readResultsReportContest(ResultsReportSummary.csv)) to get all contests' Corla's margin for the contest.
   * roundContests (readColoradoContestRoundCsv(round1/contest.csv)) to get all contests' ballotCardCount (Npop) contestBallotCardCount (Nc), estimated sample size, nwinners (voteForN).
   * detailXmlContests (readColoradoElectionDetail(detail.xml)) to get some contests' voteFor (voteForN).

2. Selected Contest to Audit
   * Use targetedContests.csv (readTargetedContestsCsv(targetedContests.csv)) to identify the county and selected contest name, but ignore everything else there
   * contestMvrs, countyMvrs and countyStyles (see readContestComparisonCsv(round3/contestComparison.csv)) from the last round to
     count the number of mvrs for each contest, and for each county, and create BallotStyles by County based on the mvrs.

The voteMargin from round1/contest.csv agrees with tabulateCounty.csv; the ballot_card_count (called Npop below) is the denominator of the diluted margin, so margin = voteMargin/npop. If I plug that margin into my formula for est samples needed, that agrees with the "Estimated # of CVRs to audit" in contest.csv. 

County Information from compareTabulateCountyAndRoundContest():

---from contestSelection---                                              ---from tabulateCounty---     --------from contestRound-----------
contestName, countyName                                                 winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples
Adams, Adams County Commissioner District 5                             114772,   97011,  17761,           17761,  468858, 0.038,        193,    190
Alamosa, Alamosa County Commissioner District 1                           4530,    2773,   1757,            1757,   15216, 0.115,         64,    61
Arapahoe, District Attorney 18th Judicial District                      175078,  128368,  46710,           46710,  351540, 0.133,         55,    52
Archuleta, Archuleta County Commissioner District 1                       4657,    2779,   1878,            1878,    9489, 0.198,         37,    34
Baca, Town of Springfield Ballot Issue 2A                                  434,     259,    175,             175,    2165, 0.081,         91,    88
Bent, Bent County Commissioner District 1                                 1331,     794,    537,             537,    2221, 0.242,         31,    28
Boulder, State Representative District 10                                33818,    6280,  27538,           27538,  396121, 0.070,        105,    102
Broomfield, Broomfield Ballot Question 2G                                24207,   17427,   6780,            6780,   47543, 0.143,         52,    49
Chaffee, Chaffee County Commissioner District 2                           7540,    6320,   1220,            1220,   14627, 0.083,         88,    85
Cheyenne, Cheyenne County Court Eiring                                     704,     274,    430,             430,    1064, 0.404,         19,    16
Clear Creek, Clear Creek County Commissioner District 3                   2887,    2246,    641,             641,    6180, 0.104,         71,    68
Conejos, Conejos County Commissioner District 3                           2206,    1788,    418,             418,    4196, 0.100,         74,    71
Costilla, Costilla County Commissioner District 3                         1162,     822,    340,             340,    2149, 0.158,         47,    44
Crowley, Crowley County Commissioner District 3                           1317,     350,    967,             967,    1729, 0.559,         14,    11
Custer, Custer County Board of County Commissioners District 2            2068,    1217,    851,             851,    3932, 0.216,         34,    31
Delta, Delta County Court Zeerip                                          8839,    7019,   1820,            1820,   19786, 0.092,         80,    77
Denver, Denver Ballot Issue 2Q                                          187976,  149573,  38403,           38403, 1104271, 0.035,        210,    207
Dolores, Dove Creek Ambulance District Ballot Issue 6A                     668,     473,    195,             195,    1453, 0.134,         55,    52
Douglas, Douglas County Commissioner District 2                         129301,   99541,  29760,           29760,  248173, 0.120,         61,    58
Eagle, Eagle County Commissioner District 1                              15261,   10626,   4635,            4635,   28988, 0.160,         46,    43
El Paso, City of Colorado Springs Ballot Question 300                   130671,  108300,  22371,           22371,  387297, 0.058,        127,    124
Elbert, Elbert County Question 1A                                        14398,    4960,   9438,            9438,   20652, 0.457,         16,    13
Fremont, Fremont County Board of County Commissioners District 3         17173,    7364,   9809,            9809,   26051, 0.377,         20,    17
Garfield, County Commissioner District 2                                 15731,   13788,   1943,            1943,   30750, 0.063,        116,    113
Gilpin, Gilpin County Commissioner District 1                             1867,    1650,    217,             217,    4183, 0.052,        141,    138
Grand, Grand County Commissioner District 2                               5423,    4077,   1346,            1346,   10236, 0.131,         56,    53
Gunnison, Gunnison County Library District Ballot Issue 6A                6096,    4413,   1683,            1683,   11126, 0.151,         49,    46
Hinsdale, Hinsdale County Commissioner District 1                          351,     247,    104,             104,     618, 0.168,         44,    41
Huerfano, Huerfano County Commissioner District 1                         2217,    1589,    628,             628,    4511, 0.139,         53,    50
Jackson, NORTH PARK SCHOOL DISTRICT R 1 BALLOT ISSUE 4A                    458,     352,    106,             106,     845, 0.125,         59,    56
Jefferson, Jefferson County Commissioner District 2                     188211,  146801,  41410,           41410,  367779, 0.113,         65,    62
Kiowa, Kiowa County Hospital District Ballot Question 6A                   586,     450,    136,             136,    1060, 0.128,         57,    54
Kit Carson, Proposition 130 (STATUTORY) Kit Carson                        1888,    1632,    256,             256,    3807, 0.067,        109,    106
La Plata, La Plata County Commissioner District 3                        19130,   14947,   4183,            4183,   36150, 0.116,         63,    61
Lake, Lake County School District R 1 Ballot Issue 4A                     2011,    1760,    251,             251,    3973, 0.063,        116,    113
Larimer, Larimer County Clerk and Recorder                              122241,   89471,  32770,           32770,  458224, 0.072,        102,    99
Las Animas, Las Animas County Commissioner District 2                     4506,    3020,   1486,            1486,    7886, 0.188,         39,    36
Lincoln, Lincoln County Commissioner District 3                           1635,     837,    798,             798,    2595, 0.308,         24,    21
Logan, Logan County Court Brammer                                         5054,    3854,   1200,            1200,   10274, 0.117,         63,    60
Mesa, Mesa County Ballot Issue 1A                                        48999,   37516,  11483,           11483,   92921, 0.124,         59,    57
Mineral, Mineral County Commissioner District 3                            495,     161,    334,             334,     766, 0.436,         17,    14
Moffat, City of Craig Ballot Question 2A                                  2356,    1283,   1073,            1073,    6699, 0.160,         46,    43
Montezuma, Montezuma County Ballot Issue 1 A                              8237,    6184,   2053,            2053,   15436, 0.133,         55,    52
Montrose, Montrose County Commissioner District 3                        15008,    8886,   6122,            6122,   26005, 0.235,         31,    29
Morgan, City of Fort Morgan Ballot Question 2A                            3244,     552,   2692,            2692,   13669, 0.197,         38,    35
Otero, Otero County Ballot Question 1A                                    6163,    2504,   3659,            3659,    9039, 0.405,         19,    16
Ouray, Ouray County Commissioner District 1                               2208,    1797,    411,             411,    4157, 0.099,         74,    71
Park, Park County Commissioner District 1                                 6786,    4970,   1816,            1816,   13392, 0.136,         54,    51
Phillips, City of Holyoke Mayor                                            581,     320,    261,             261,    2384, 0.109,         67,    64
Pitkin, Pitkin County Ballot Issue 1A: Affordable and Workforce Housin    6266,    4175,   2091,            2091,   11348, 0.184,         40,    37
Prowers, Prowers County Ballot Issue 1A                                   3669,    1305,   2364,            2364,    5209, 0.454,         17,    14
Pueblo, Pueblo County Commissioner District 2                            42770,   39476,   3294,            3294,  170790, 0.019,        378,    375
Rio Blanco, Amendment 80 (CONSTITUTIONAL) Rio Blanco                      2013,    1539,    474,             474,    3728, 0.127,         58,    55
Rio Grande, Rio Grande County Court Stenger                               3336,    1954,   1382,            1382,    6286, 0.220,         34,    31
Routt, Routt County Commissioner District 1                               8719,    7319,   1400,            1400,   33261, 0.042,        174,    171
Saguache, Saguache County Commissioner District 1                         1730,    1522,    208,             208,    3446, 0.060,        121,    118
San Miguel, San Miguel County Ballot Question 1A                          2706,    1710,    996,             996,    5008, 0.199,         37,    34
Sedgwick, Sedgwick County Commissioner District 3                          703,     587,    116,             116,    1371, 0.085,         87,    84
Summit, Summit School District RE 1 Ballot Issue 4A                       9218,    6942,   2276,            2276,   17819, 0.128,         58,    55
Teller, Teller County Ballot Question 1A                                  8472,    6751,   1721,            1721,   17112, 0.101,         73,    70
Washington, Washington County Commissioner District 2                     1672,     972,    700,             700,    2838, 0.247,         30,    27
Weld, District Court 19th Judicial District Crowther                     75911,   60300,  15611,           15611,  182397, 0.086,         86,    83
Yuma, Yuma County Court Jones                                             3100,     969,   2131,            2131,    4719, 0.452,         17,    14



## Audit round files

these have the margins and the estimated samples from

    round1/contest.csv and round2/contest.csv are identical,
    round3/contest.csv has random_audit_status changed to "ended" and "risk_limit_achieved", same number of lines.

contest_name,audit_reason,random_audit_status,winners_allowed,          ballot_card_count, contest_ballot_card_count, winners, min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

State Representative - District 10, county_wide_contest,in_progress,1,   396121, 44675,"""Junie Joseph""",27538,0.03000000,0,0,0,0,0,0,0,1.03905000,0,105,105
State Representative - District 10	county_wide_contest	in_progress	1	 396121	 44675	"Junie Joseph"	  27538	0.03	0	0	0	0	0	0	0	1.03905	0	105	105

ballot card count = 396121
contest_ballot_card_count = 44675
min_margin = 27538
all errors are 0
gamma = 1.03905
optimistic_samples_to_audit = 105
estimated_samples_to_audit = 105

estimated cvrs is optimistic function in corla (see OptimisticTest class).
TODO: For some reason they use ballot_card_count instead of contest_ballot_card_count. Something to do with cards vs ballots?

// min_margin / contest_ballot_card_count
1. dilutedMargin = 27538.0 / 44675.0 = 0.6164073866815892 estSamples = 12.0

// min_margin / ballot_card_count
2. dilutedMargin = 27538.0 / 396121.0 = 0.0695191620742147 estSamples = 105.0

// from targetedContests.xlsx below:
3. dilutedMargin = 0.0699 estSamples = 105.0

The estimations agree closely with rlauxe simulations using AdaptiveComparision and assume no errors.

Note 12 * 396121/44675 = 106, so "find 12 cards in entire pool"

-----------------------------
round2/contestComparison.csv has lines added reletive to round1/contestComparison.csv,

-----------------------------
not sure what roundX/contestSelection.csv are; some kind of working documents.


-----------------------------------
CreateCountyAudits 4/24/26

*** Cant find contest with name 'Colorado Court of Appeals Roman' key=Colorado Court of Appeals Judge Roman - will ignore
precincts = 3199
ncontests with info = 48
2026-04-24 13:10:54.747 INFO  CreateColoradoElection unsortedMvrsFile to /home/stormy/rla/cases/corla/county/Boulder/audit/private/unsortedMvrs.csv
ncontests with info = 54
2026-04-24 13:11:01.943 INFO  CreateColoradoElection unsortedMvrsFile to /home/stormy/rla/cases/corla/county/El Paso/audit/private/unsortedMvrs.csv
ncontests with info = 30
2026-04-24 13:11:08.837 INFO  CreateColoradoElection unsortedMvrsFile to /home/stormy/rla/cases/corla/county/La Plata/audit/private/unsortedMvrs.csv
ncontests with info = 59

contests = 295
County, Precinct, Contest, Choice, Party, Total Votes
precincts = 3199
   total cvrs = 3191197
   writeContestsJsonFile /home/stormy/rla/corla/election2/contests.json
took = 32.32 s

 cvrs has 3191197 cvrs
wrote 3191197 cvrs
mergeSortedChunk took 60.91 s

Each precinct has exactly one "ballot style", namely the one with all precinct.contestChoices on it.
We create number of cvrs for each precinct equal to maximum vote across contests.
So underestimating the undervotes. Not using phantoms.

Compare to official "Ballots Cast" = 3,241,120

///////////////////////////////////////////////////////////////

apparently detail.xml does not have all contests; not sure which it skips
eg "City of Lafayette Ballot Question 2A" and many more in round/contest 2A. that file lists 726 contests
295 contests in src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip
295 contests in src/test/data/corla/2024election/summary.csv
~725 contests in src/test/data/corla/2024audit/round1/contestSelection.csv
read 725 contests from src/test/data/corla/2024audit/round1/contest.csv

targetedContests.csv has one for each county +2 statewide. many/most are not in detail.xml.
However this appears to be wrong, and is perhaps not used.



=> we could use round1/contest.csv to create contests

but where do we get candidates and votes for ones not in detail.xml ?

//////////////////////////////////////////////////////////////////
*** Contest Calhan School District RJ1 Question 5B has 2039 total votes, but contestBallotCardCount is 118 - using totalVotes

contestRound:
contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

    Calhan School District RJ1 Question 5B,opportunistic_benefits,in_progress,1,20652,118,"""No/Against""",61,0.03000000,0,0,0,0,0,0,0,1.03905000,0,2468,2468

La Plata,La Plata County Surveyor,There are no candidates for this office


## NOT USED Summary CSV - Comma separated file showing total votes received

see readColoradoElectionSummaryCsv()
https://results.enr.clarityelections.com//CO//122598/355977/reports/summary.zip

line number,"contest name",            "choice name","party name","total votes","percent of votes",
"registered voters","ballots cast","num Area total","num Area rptg",
"over votes","under votes"
1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM", 1728159,54.16,         0,0,64,0,      "607","2801"
2,"Presidential Electors (Vote For 1)","Donald J. Trump / JD Vance","REP",  1377441,43.17,         0,0,64,0,      "607","2801"
3,"Presidential Electors (Vote For 1)","Blake Huber / Andrea Denault","APV",   2196,0.07,          0,0,64,0,      "607","2801"
4,"Presidential Electors (Vote For 1)","Chase Russell Oliver / Mike ter Maat","LBR",21439,0.67,    0,0,64,0,      "607","2801"
5,"Presidential Electors (Vote For 1)","Jill Stein / Rudolph Ware","GRN",     17344,0.54,          0,0,64,0,      "607","2801"
6,"Presidential Electors (Vote For 1)","Randall Terry / Stephen E. Broden","ACN",3522,0.11,        0,0,64,0,      "607","2801"
7,"Presidential Electors (Vote For 1)","Cornel West / Melina Abdullah","UNI", 5149,0.16,           0,0,64,0,      "607","2801"
8,"Presidential Electors (Vote For 1)","Robert F. Kennedy Jr. / Nicole Shanahan","UAF",35623,1.12, 0,0,64,0,      "607","2801"

the last 5 fields are contest, not candidate specific. under/overvotes doubtful

295 contests in src/test/data/corla/2024election/summary.csv


/////////////////////////////////////////////////////////////////////
TODO:  Only 8 of 15 candidates show up for president: writeins??


Contest 'Presidential Electors' (0) PLURALITY voteForN=1 votes={0=1728159, 1=1377441, 7=35623, 3=21439, 4=17344, 6=5149, 5=3522, 2=2196} undervotes=48849, voteForN=1
winners=[0] Nc=3239722 Nphantoms=0 Nu=48849 sumVotes=3190873
0/1 votes=1728159/1377441 diff=350718 (w-l)/w =0.2029 Npop=3239722 dilutedMargin=10.8256% reportedMargin=10.8256% recountMargin=20.2943%

0 'Kamala D. Harris / Tim Walz': votes=1728159  (winner)
1 'Donald J. Trump / JD Vance': votes=1377441
2 'Blake Huber / Andrea Denault': votes=2196
3 'Chase Russell Oliver / Mike ter Maat': votes=21439
4 'Jill Stein / Rudolph Ware': votes=17344
5 'Randall Terry / Stephen E. Broden': votes=3522
6 'Cornel West / Melina Abdullah': votes=5149
7 'Robert F. Kennedy Jr. / Nicole Shanahan': votes=35623
Total=3190873


should be:

choiceFunction=PLURALITY nwinners=1, winners=[0])
0 'Kamala D. Harris / Tim Walz (DEM)': votes=150149
1 'Donald J. Trump / JD Vance (REP)': votes=40758
2 'Blake Huber / Andrea Denault (APV)': votes=123
3 'Chase Russell Oliver / Mike ter Maat (LBR)': votes=1263
4 'Jill Stein / Rudolph Ware (GRN)': votes=1499
5 'Randall Terry / Stephen E Broden (ACN)': votes=147
6 'Cornel West / Melina Abdullah (UNI)': votes=457
7 'Robert F. Kennedy Jr. / Nicole Shanahan (UNA)': votes=1754
8 'Write-in': votes=2
9 'Chris Garrity / Cody Ballard': votes=4
10 'Claudia De la Cruz / Karina GarcÃ­a': votes=82
11 'Shiva Ayyadurai / Crystal Ellis': votes=2
12 'Peter Sonski / Lauren Onak': votes=65
13 'Bill Frankel / Steve Jenkins': votes=1
14 'Brian Anthony Perry / Mark Sbani': votes=0



