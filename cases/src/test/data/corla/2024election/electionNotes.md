# Colorado 2024
03/24/2025
Election Results & Data

RLAs: https://www.coloradosos.gov/pubs/elections/auditCenter.html
targeted contests

https://www.coloradosos.gov/pubs/elections/resultsData.html
https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary

//////////////////////////////////////////////////////////////////
*** Contest Calhan School District RJ1 Question 5B has 2039 total votes, but contestBallotCardCount is 118 - using totalVotes

contestRound:
contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

    Calhan School District RJ1 Question 5B,opportunistic_benefits,in_progress,1,20652,118,"""No/Against""",61,0.03000000,0,0,0,0,0,0,0,1.03905000,0,2468,2468

La Plata,La Plata County Surveyor,There are no candidates for this office

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


## Detail XLS (295 contests, 92k zipped, 2.6M unzipped )
has a separate sheet for every contest with vote count, by county
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxls.zip

can also get it as an XML (56k zipped, 780k unzipped ):
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxml.zip

see readColoradoElectionDetail()

````
<ElectionResult>
   <Timestamp>12/6/2024 1:20:51 PM MST</Timestamp>
   <ElectionName>2024 General</ElectionName>
   <ElectionDate>11/5/2024</ElectionDate>
   <Region>CO</Region>
   <ElectionVoterTurnout totalVoters="4058938" ballotsCast="3241120" voterTurnout="79.85">
       <Counties>
           <County name="Adams" totalVoters="320225" ballotsCast="236899" voterTurnout="73.98" precinctsParticipating="283" precinctsReported="283" precinctsReportingPercent="100.00" />
           <County name="Alamosa" totalVoters="10321" ballotsCast="7671" voterTurnout="74.32" precinctsParticipating="8" precinctsReported="8" precinctsReportingPercent="100.00" />
...
   <Contest ...>
      <ParticipatingCounties ...>
          <County>
      <Choice test="candidate">
          <VoteType>
              <County name="countyName" votes = "voteCount">
...
````

Note we can get ballotsCast by county.
We can only use precinct results to get vote totals by contest, but we dont know what the card styles are.

## PrecinctLevelResults

https://www.sos.state.co.us/pubs/elections/resultsData.html
https://www.sos.state.co.us/pubs/elections/Results/2024/2024GeneralPrecinctLevelResults.xlsx
convert to cvs and zip to corla/src/test/data/2024election/2024GeneralPrecinctLevelResults.zip
note: no undervotes

````
County	Precinct	Contest	Choice	Party	Total Votes
ADAMS	4215601243	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	224
ADAMS	4215601244	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	237
ADAMS	4215601245	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	64
...
````

Create a pool for each precinct.
Assume single ballot style TODO dont assume, but how, without card styles ?
This will put all contests on a since card. But Boulder, eg, has 2 cards, so boulder24/oa (where we know the card styles) has 2x the cards than corla/county/Boulder.

corla/Boulder totalCardCount=196152 2x= 392304
boulder24 totalCardCount=396697

## contestRoundFile 

src/test/data/corla/2024audit/round1/contest.csv, see readColoradoContestRoundCsv()

````
contest_name,audit_reason,random_audit_status,winners_allowed,                           ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
     audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
                                                                                                                                                            gamma,overstatements,
                                                                                                                                                                           optimistic_samples_to_audit,estimated_samples_to_audit

17th Judicial District Ballot Question 7B,opportunistic_benefits,in_progress,1,           516401,279529,"""No/Against""",37549,0.03000000,   0,0,0,0,0,0,0, 1.03905000,0,  101,101
Adams 12 Five Star Schools Ballot Issue 5D,opportunistic_benefits,in_progress,1,          516401,117043,"""No/Against""",12622,0.03000000,   0,0,0,0,0,0,0, 1.03905000,0,  299,299
Adams 12 Five Star Schools Ballot Issue 5E,opportunistic_benefits,in_progress,1,          516401,117043,"""Yes/For""",10481,0.03000000,      0,0,0,0,0,0,0, 1.03905000,0,  360,360
Adams-Arapahoe School District 28J Ballot Issue 5A,opportunistic_benefits,in_progress,1,  799746,84894,"""Yes/For""",22669,0.03000000,       0,0,0,0,0,0,0, 1.03905000,0,  258,258
Adams-Arapahoe School District 28J Ballot Issue 5B,opportunistic_benefits,in_progress,1,  799746,84894,"""Yes/For""",39107,0.03000000,       0,0,0,0,0,0,0, 1.03905000,0,  150,150
State Senator - District 10,opportunistic_benefits,in_progress,1,                         387297,94146,"""Larry G. Liston""",15533,0.03000000,0,0,0,0,0,0,0,1.03905000,0,  182,182

````
ballot_card_count = number of ballots in the county
Nc = contest_ballot_card_count, unless this is less than nvotes, then use nvotes.
min_margin = min assertion vote margin (I think)
corla samples = optimistic_samples_to_audit

````
data class CorlaContestRoundCsv(
    val contestName: String,
    val nwinners: Int,
    val ballotCardCount: Int,
    val contestBallotCardCount: Int,
    val winners: String,
    val minMargin: Int,
    val riskLimit: Double,
    val gamma: Double,
    val optimisticSamplesToAudit: Int,
    val estimatedSamplesToAudit: Int,
)
````

Contest '17th Judicial District Ballot Question 7B' (7000) PLURALITY voteForN=1 votes={1=142032, 0=104478} undervotes=33019, voteForN=1
winners=[1] Nc=279529 Nphantoms=0 Nu=33019 sumVotes=246510
1/0 votes=142032/104478 diff=37554 (w-l)/w =0.2644 Npop=279529 dilutedMargin=13.4347% reportedMargin=13.4347% recountMargin=26.4405%

0 'Yes/For': votes=104478
1 'No/Against': votes=142032  (winner)
Total=246510

diff=37554, contestRound has 37549
margin= 37554/279529 13.4347%  calcNewMvrs=53
contestRound doesnt have, just has estimated_samples_to_audit = 101
which gives samples=101 margin=0.07061533649907603; 37554/531810=.070615445, so using population size of 531810, or
possible 37554/516401 = .072722555 using ballot_card_count as population size

summary.csv has
585,"17th Judicial District Ballot Question 7B (Vote For 1)","Yes/For","Y",104478,42.38,0,0,2,0,"12","26805"
586,"17th Judicial District Ballot Question 7B (Vote For 1)","No/Against","N",142032,57.62,0,0,2,0,"12","26805"

///////////////////////////////////
contests >= 5000 dont have precinct subtotals.... are these ever the "targeted contest" ? YES

City of Longmont Ballot Issue 3A,opportunistic_benefits,in_progress,1,578518,55848,"""Yes/For""",26166,0.03000000,0,0,0,0,0,0,0,1.03905000,0,162,162

CorlaContestBuilder(info='City of Longmont Ballot Issue 3A' (5002) candidates=[0, 1] choiceFunction=PLURALITY nwinners=1 voteForN=1, contestId=5002, Nc=55848, candidateVotes={0=39825, 1=13622}, poolTotalCards=0)

City of Longmont Ballot Issue 3A (5002) Nc=55848 Nphantoms=2401 votes={0=39825, 1=13622} undervotes=0, voteForN=1
   39825−13622 = 26203 (contestRoundFile has 26166)
   where do we get these vote counts if theres no precinct data ?? From ElectionResult
   where is Nphantoms coming from? Nc - Ncast = 39825+13622 = 53447; 55848 -  53447 = 2401, probably better to make those undervotes ....


# targetedContests.csv

// WRONG DONT USE
"County",    "Contest",                               "Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
"Broomfield","City and County of Broomfield Ballot Question 2G",1,"23,893",       "17,219",       "6,674",         14.30%,        3%,            50,   "46,676",,,,,,,,,,,,,,,,,9

                                                                                              from tabulateCounty
contestName, countyName                              , nwinners,  winner, loser, voteMargin,   winner, loser, voteMargin, diff%
Adams, Adams County Commissioner - District 5               , 1,   88099,   74153,  13946,     114772,   97011,  17761     27.4 ***
Alamosa, Alamosa County Commissioner - District 1           , 1,    4475,    2751,   1724,       4530,    2773,   1757     1.9
Arapahoe, District Attorney - 18th Judicial District        , 1,  120697,   87750,  32947,     175078,  128368,  46710     41.8 ***
Archuleta, Archuleta County Commissioner - District 1       , 1,    4605,    2760,   1845,       4657,    2779,   1878     1.8
Baca, Town of Springfield Ballot Issue 2A                   , 1,     428,     256,    172,        434,     259,    175     1.7
Bent, Bent County Commissioner - District 1                 , 1,    1314,     778,    536,       1331,     794,    537     0.2
Boulder, State Representative - District 10                 , 1,   23460,    3720,  19740,      33818,    6280,  27538     39.5 ***
Broomfield, City and County of Broomfield Ballot Question 2G, 1,   23893,   17219,   6674,      24207,   17427,   6780     1.6
Chaffee, Chaffee County Commissioner - District 2           , 1,    7482,    6270,   1212,       7540,    6320,   1220     0.7
Cheyenne, Cheyenne County Court Judge - Eiring              , 1,     686,     266,    420,        704,     274,    430     2.4
Clear Creek, Clear Creek County Commissioner - District 3   , 1,    2841,    2200,    641,       2887,    2246,    641     0.0
Conejos, Conejos County Commissioner - District 3           , 1,    2184,    1767,    417,       2206,    1788,    418     0.2
Costilla, Costilla County Commissioner - District 3         , 1,    1137,     796,    341,       1162,     822,    340     0.3
Crowley, Crowley County Commissioner - District 3           , 1,    1305,     345,    960,       1317,     350,    967     0.7
Custer, Custer County Commissioner - District 2             , 1,    2053,    1205,    848,       2068,    1217,    851     0.4
Delta, Delta County Court Judge - Zeerip                    , 1,    8713,    6874,   1839,       8839,    7019,   1820     1.0
Denver, City and County of Denver Ballot Issue 2Q           , 1,  187470,  149202,  38268,     187976,  149573,  38403     0.4
Dolores, Dove Creek Ambulance District Ballot Issue 6A      , 1,     660,     458,    202,        668,     473,    195     3.5
Douglas, Douglas County Commissioner - District 2           , 1,  120771,   94494,  26277,     129301,   99541,  29760     13.3 ***
Eagle, Eagle County Commissioner - District 1               , 1,   14949,   10432,   4517,      15261,   10626,   4635     2.6
El Paso, City of Colorado Springs Ballot Question 300       , 1,  130671,  108300,   2735,     130671,  108300,  22371     718.0 ***
Elbert, Elbert County Question 1A                           , 1,   11395,    4117,   7278,      14398,    4960,   9438     29.7 ***
Fremont, Fremont County Commissioner - District 3           , 1,   16911,    7223,   9688,      17173,    7364,   9809     1.2
Garfield, Garfield County Commissioner - District 2         , 1,   14595,   12842,   1753,      15731,   13788,   1943     10.8 ***
Gilpin, Gilpin County Commissioner - District 1             , 1,    1834,    1619,    215,       1867,    1650,    217     0.9
Grand, Grand County Commissioner - District 2               , 1,    5378,    4023,   1355,       5423,    4077,   1346     0.7
Gunnison, Gunnison County Library District Ballot Issue 6A  , 1,    6066,    4375,   1691,       6096,    4413,   1683     0.5
Hinsdale, Hinsdale County Commissioner - District 1         , 1,     343,     245,     98,        351,     247,    104     6.1 ***
Huerfano, Huerfano County Commissioner - District 1         , 1,    2207,    1582,    625,       2217,    1589,    628     0.5
Jackson, North Park School District R-1 Ballot Issue 4A     , 1,     454,     345,    109,        458,     352,    106     2.8
Jefferson, Jefferson County Commissioner - District 2       , 1,  183798,  142879,  40919,     188211,  146801,  41410     1.2
Kiowa, Kiowa County Hospital District Ballot Question 6A    , 1,     579,     445,    134,        586,     450,    136     1.5
Kit Carson, Proposition 130 (STATUTORY)                     , 1,    1870,    1599,    271,       1888,    1632,    256     5.5 ***
La Plata, La Plata County Commissioner - District 3         , 1,   18987,   14832,   4155,      19130,   14947,   4183     0.7
Lake, Lake County School District R-1 Ballot Issue 4A       , 1,    1994,    1749,    245,       2011,    1760,    251     2.4
Larimer, Larimer County Clerk and Recorder                  , 1,   99324,   74197,  25127,     122241,   89471,  32770     30.4 ***
Las Animas, Las Animas County Commissioner - District 2     , 1,    3027,    2199,    828,       4506,    3020,   1486     79.5 ***
Lincoln, Lincoln County Commissioner - District 3           , 1,    1623,     826,    797,       1635,     837,    798     0.1
Logan, Logan County Court Judge - Brammer                   , 1,    4991,    3833,   1158,       5054,    3854,   1200     3.6
Mesa, Mesa County Ballot Issue 1A                           , 1,   44985,   34382,  10603,      48999,   37516,  11483     8.3 ***
Mineral, Mineral County Commissioner - District 3           , 1,     493,     161,    332,        495,     161,    334     0.6
Moffat, City of Craig Ballot Question 2A                    , 1,    1360,     716,    644,       2356,    1283,   1073     66.6 ***
Montezuma, Montezuma County Ballot Issue 1A                 , 1,    6933,    5472,   1461,       8237,    6184,   2053     40.5 ***
Montrose, Montrose County Commissioner - District 3         , 1,   12335,    7713,   4622,      15008,    8886,   6122     32.5 ***
Morgan, City of Fort Morgan Ballot Question 2A              , 1,    3217,     546,   2671,       3244,     552,   2692     0.8
Otero, Otero County Ballot Question 1A                      , 1,    5557,    2263,   3294,       6163,    2504,   3659     11.1 ***
Ouray, Ouray County Commissioner - District 1               , 1,    2171,    1762,    409,       2208,    1797,    411     0.5
Park, Park County Commissioner - District 1                 , 1,    6622,    4845,   1777,       6786,    4970,   1816     2.2
Phillips, City of Holyoke Mayor                             , 1,     575,     315,    260,        581,     320,    261     0.4
Pitkin, Pitkin County Ballot Issue 1A                       , 1,    4741,    3249,   1492,       6266,    4175,   2091     40.1 ***
Prowers, Prowers County Ballot Issue 1A                     , 1,    3087,    1140,   1947,       3669,    1305,   2364     21.4 ***
Pueblo, Pueblo County Commissioner - District 2             , 1,   42770,   39476,   3294,      42770,   39476,   3294     0.0
Rio Blanco, Amendment 80 (CONSTITUTIONAL)                   , 1,    1859,    1437,    422,       2013,    1539,    474     12.3 ***
Rio Grande, Rio Grande County Court Judge - Stenger         , 1,    3317,    1944,   1373,       3336,    1954,   1382     0.7
Routt, Routt County Commissioner - District 1               , 1,    8298,    6664,   1634,       8719,    7319,   1400     14.3 ***
Saguache, Saguache County Commissioner - District 1         , 1,    1708,    1507,    201,       1730,    1522,    208     3.5
San Miguel, San Miguel County Ballot Measure 1A             , 1,    2653,    1692,    961,       2706,    1710,    996     3.6
Sedgwick, Sedgwick County Commissioner - District 3         , 1,     702,     586,    116,        703,     587,    116     0.0
Summit, Summit School District RE-1 Ballot Issue 4A         , 1,    9163,    6893,   2270,       9218,    6942,   2276     0.3
Teller, Teller County Ballot Question 1A                    , 1,    6901,    5593,   1308,       8472,    6751,   1721     31.6 ***
Washington, Washington County Commissioner - District 2     , 1,    1657,     964,    693,       1672,     972,    700     1.0
Weld, District Court Judge - 19th Judicial District - Crowth, 1,   51150,   40167,  10983,      75911,   60300,  15611     42.1 ***
Yuma, Yuma County Court Judge - Jones                       , 1,    2973,     924,   2049,       3100,     969,   2131     4.0


# round contestSelection.csv 

min_margin,contest_name,                  contest_cvr_ids
6780      ,Broomfield Ballot Question 2G,"[765172,847804,869211,847648,874352,822455,863519,819346,868712,838654,715509,756755,833630,810634,715853,748171,856760,775569,839234,700256,699491,872125,830771,700721,718688,828814,822671,840431,833321,867867,758540,851979,840483,841036,822457,705703,851947,852677,739769,837895,815011,803476,830518,691040,762717,698406,691505,787851,773650,757760,773481,815168,811751]"

# round  contest.csv

contest_name,                audit_reason, random_audit_status,winners_allowed,
                                                               ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
  audited_sample_count,two_vote_over_count, one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
                                                                                                                           gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
Broomfield Ballot Question 2G,county_wide_contest,in_progress,1,47543,47543,"""No/Against""",6780,0.03000000,0,0,0,0,0,0,0,1.03905000,0,52,52

ballot_card_count = Npop?
contest_ballot_card_count = Nc ?
min_margin = 6780
optimistic_samples_to_audit = 52


Boulder selected contest:

State Representative - District 10,county_wide_contest,in_progress,1,
    396121,44675,"""Junie Joseph""",27538, 0.03000000,0,0,0,0,0,0,0,1.03905000,0,105,105

ballot_card_count = Npop = 396121
contest_ballot_card_count = Nc = 44675
min_margin = 27538
optimistic_samples_to_audit = 105

                                                                           ---from tabulateCounty---     --------from contestRound-----
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

# contestComparison.csv is the audited result, for all contests on the selected ballots ...

county_name,contest_name,                imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
Broomfield,Broomfield Ballot Question 2G,104-100-11,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:55:14.442508,869211,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-19-48,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:17:19.859941,810634,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-23-20,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:30:09.072304,811751,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-36-37,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:32:07.561475,815011,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-37-41,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:34:04.549326,815168,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-61-31,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:42:38.015308,828814,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-74-36,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:46:41.728707,856760,COUNTY_WIDE_CONTEST



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

