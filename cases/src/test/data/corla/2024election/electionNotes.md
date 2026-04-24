# Colorado 2024
03/24/2025
Election Results & Data

RLAs: https://www.coloradosos.gov/pubs/elections/auditCenter.html
targeted contests

https://www.coloradosos.gov/pubs/elections/resultsData.html
https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary

## Summary CSV - Comma separated file showing total votes received (not used)
see readColoradoElectionSummaryCsv()
https://results.enr.clarityelections.com//CO//122598/355977/reports/summary.zip
TODO:  Only 8 of 15 candidates show up for president: writeins??

line number,"contest name",            "choice name","party name","total votes","percent of votes",
                                                                                             "registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM", 1728159,54.16,         0,0,64,0,"607","2801"
2,"Presidential Electors (Vote For 1)","Donald J. Trump / JD Vance","REP",  1377441,43.17,         0,0,64,0,"607","2801"
3,"Presidential Electors (Vote For 1)","Blake Huber / Andrea Denault","APV",   2196,0.07,          0,0,64,0,"607","2801"
4,"Presidential Electors (Vote For 1)","Chase Russell Oliver / Mike ter Maat","LBR",21439,0.67,    0,0,64,0,"607","2801"
5,"Presidential Electors (Vote For 1)","Jill Stein / Rudolph Ware","GRN",     17344,0.54,          0,0,64,0,"607","2801"
6,"Presidential Electors (Vote For 1)","Randall Terry / Stephen E. Broden","ACN",3522,0.11,        0,0,64,0,"607","2801"
7,"Presidential Electors (Vote For 1)","Cornel West / Melina Abdullah","UNI", 5149,0.16,           0,0,64,0,"607","2801"
8,"Presidential Electors (Vote For 1)","Robert F. Kennedy Jr. / Nicole Shanahan","UAF",35623,1.12, 0,0,64,0,"607","2801"

the last 5 fields are contest, not candidate specific. under/overvotes doubtful


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
        // apparently detail.xml does not have all contests; not sure which it skips
        // eg "City of Lafayette Ballot Question 2A" and many more in round/contest 2A. that file lists 726 contests
        // 295 contests in src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip
        // 295 contests in src/test/data/corla/2024election/summary.csv
        // ~725 contests in src/test/data/corla/2024audit/round1/contestSelection.csv
        // "targeted contests.csv" has one for each county +2 statewide. many/most are not in detail.xml.

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


# round contestSelection.csv, targetedContests.csv, contest.csv

min_margin,contest_name,                  contest_cvr_ids
6780      ,Broomfield Ballot Question 2G,"[765172,847804,869211,847648,874352,822455,863519,819346,868712,838654,715509,756755,833630,810634,715853,748171,856760,775569,839234,700256,699491,872125,830771,700721,718688,828814,822671,840431,833321,867867,758540,851979,840483,841036,822457,705703,851947,852677,739769,837895,815011,803476,830518,691040,762717,698406,691505,787851,773650,757760,773481,815168,811751]"

"County",    "Contest",                               "Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
"Broomfield","City and County of Broomfield Ballot Question 2G",1,"23,893",       "17,219",       "6,674",         14.30%,        3%,            50,   "46,676",,,,,,,,,,,,,,,,,9

contest_name,                audit_reason, random_audit_status,winners_allowed,
                                                               ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
  audited_sample_count,two_vote_over_count, one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
                                                                                                                           gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
Broomfield Ballot Question 2G,county_wide_contest,in_progress,1,47543,47543,"""No/Against""",6780,0.03000000,0,0,0,0,0,0,0,1.03905000,0,52,52


# contestComparison.csv is the audited result, but only for the selected contest ...

county_name,contest_name,                imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
Broomfield,Broomfield Ballot Question 2G,104-100-11,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:55:14.442508,869211,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-19-48,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:17:19.859941,810634,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-23-20,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:30:09.072304,811751,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-36-37,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:32:07.561475,815011,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-37-41,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:34:04.549326,815168,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-61-31,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:42:38.015308,828814,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-74-36,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:46:41.728707,856760,COUNTY_WIDE_CONTEST