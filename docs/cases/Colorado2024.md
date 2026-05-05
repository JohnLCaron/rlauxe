# Colorado Statewide Election 2024
03/13/2026

* 3,241,120 ballot cast (Colorado 2024 General Election) in 3199 precincts.
* 146 contests, no IRV.
* CO doesnt publically publish the CVRs, just precinct totals, see _2024GeneralPrecinctLevelResults.csv/zip/xlsx_.
* CORLA does an RLA, so they do have access to the CVRs. A "publically verifiable" RLA requires the CVRs to be publically verifiable. But we can still do the RLA as long as they are "privately available".

We use the precinct totals to simulate CVRs, and use those to estimate how Rlauxe would preform in a real audit.

# Comparing CORLA and Rlauxe

The Colorado RLA software uses a "Conservative approximation of the Kaplan-Markov P-value" for its risk measuring function
from the ["Gentle Introduction" and "Super Simple" papers](../notes/notes.txt). It makes use of measured error rates as they are sampled.

We have a Kotlin port of the CORLA Java code in order to compare performance with our CLCA algorithm. Its possible
that our port does not accurately reflect the actual CORLA code.

The following compares our Corla implementation against the Rlauxe algorithm [see BettingRiskFunctions](docs/BettingRiskFunctions.md).
These are "ballot-at-a-time" plots, so we dont limit the number of samples, or use the estimation rounds.

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/cases/compareCorlaAndRlauxeLogLinear.html" rel="compareCorlaAndRlauxeLogLinear">![compareCorlaAndRlauxeLogLinear](../plots2/cases/compareCorlaAndRlauxeLogLinear.png)</a>

* CORLA is impressively good in the absence of errors.
* It does progressively worse as the error rate increases and the margin decreases.

If you arbitrarily set the maximum sample size to 10,000, Rlauxe can audit down to .5% margin and 1% fuzz rates. 
CORLA can audit down to .5% margin at .25% fuzz rates. If you are confident in keeping errors low, CORLA is quite good.
Note that phantom ballots contribute to error rates (see [effects of phantoms on samples needed](../ClcaErrors.md#phantom-ballots))
and also must be kept low for Corla to be effective. Also see [Corla Notes](../notes/CorlaNotes.md).

## Downloaded files

1. Detail XLS (295 contests, 92k zipped, 2.6M unzipped )
has a separate sheet for every contest with vote count, by county
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxls.zip
can also get it as an XML (56k zipped, 780k unzipped ):
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxml.zip

        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"


2. PrecinctLevelResults are apparently no longer available, or were moved.

We got them from https://www.sos.state.co.us/pubs/elections/resultsData.html
https://www.sos.state.co.us/pubs/elections/Results/2024/2024GeneralPrecinctLevelResults.xlsx

        corla/src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.xlsx

convert to cvs and zip to 

        corla/src/test/data/2024election/2024GeneralPrecinctLevelResults.zip

looks like:

County	Precinct	Contest	Choice	Party	Total Votes
ADAMS	4215601243	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	224
ADAMS	4215601244	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	237
ADAMS	4215601245	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	64
...

3. https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contest.csv
This file is still available at https://github.com/nealmcb/auditcenter

        corla/src/test/data/corla/2024audit/round1/contest.csv

We use it to make the contests.

## Generating the election

Run createColoradoElection() to create a CLCA or Polling election in  _$testdataDir/cases/corla/clca/audit_
or _$testdataDir/cases/corla/polling/audit_. OneAudit elections are not supported.

1. detailxml and contest.csv are used to define a CorlaContestBuilder for each contest.
   Set Nc from contest.csv field name contestBallotCardCount.
2. 2024GeneralPrecinctLevelResults are used to create an OneAuditPoolFromBallotStyle for each precinct.
3. Assumes that each precinct has one BallotStyle; we need total ballots per precinct per contest to fix that.
   The precinct ballot style is used for the AuditableCards from that precinct.
4. Adjust pool ncards so contest 0 has 0 undervotes; needed since we dont know number of cards in precincts or missing.
   Adjust Nc up if needed. This is rather arbitrary; lots of other ways to guess at it. 
5. The sum over precincts gives us Ncast, the difference from Nc gives us nphantoms.

### Corla election notes:

* The _detail.xml_ file has summary by contest broken out by county.
* The _round1/contest.csv_ file has a summary of each round; we use these fields from it to make the contest:
````
  contest_name
  winners_allowed
  ballot_card_count
  contest_ballot_card_count
  winners
  min_margin
  risk_limit
  optimistic_samples_to_audit
  estimated_samples_to_audit
````

Note that this gives us the number of samples estimated for each audit round, from the CORLA "super simple" algorithm. We can compare these estimates with the CORLA software's estimates (estimates can be seen in Rlauxe Viewer _AuditRoundsTable_).

There are 725 contests listed on round1/contest.csv. There are 295 listed in detail.xml. I was told they dont have precinct data (or CVRs?) for contests \>= 260. So we ignore contests with id > 260.

The file corla/2024audit/targetedContests.xlsx shows contests selected for audit, eg:

````
  "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
  "Colorado","Presidential Electors",1,"1,374,175","1,084,812","289,363",8.15%,3%,89,"2,554,611","Audited in all 64 counties",,,,,,,,,,,,,,,,1
````

However this doesnt agree with detail.xml, eg:
````
      Choice(key=1, text='Kamala D. Harris / Tim Walz', party='DEM', totalVotes=1728159, voteTypes=[VoteType(name='Total Votes', votes=1728159
      Choice(key=2, text='Donald J. Trump / JD Vance', party='REP', totalVotes=1377441, voteTypes=[VoteType(name='Total Votes', votes=1377441
````
Not sure why its different, but it looks like targetedContests.xlsx is wrong.

detail.xml does not have the total number of ballots for a contest, so we get that from ContestRound.contest_ballot_card_count eg:

````
contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
Presidential Electors,state_wide_contest,in_progress,1,4746866,3239722,"""Kamala D. Harris / Tim Walz""",350348,0.03000000,0,0,0,0,0,0,0,1.03905000,0,99,99
````
Not exactly consistent, eg 1728159 - 1377441 = 350718 != 350348, but close enough for now (we can only do a simulation since we dont have the real CVRs).

## how many cards per ballot?

corla/2024audit/targetedContests.xlsx/csv has at the bottom:

""The assumption has been made in the ""Estimated # of CVRs to audit"" value that all ballot cards were returned for each ballot. 
  Therefore, the number of CVRs is the ballots cast total multiplied by the number of cards. As the average number of cards per ballot 
  decreases the number of ballots to audit will also decrease.",,,,,,,,,,,,,,,,,,,,,,,,,,
"

So presumably round1/contestComparison.csv has all cards. This complicates extracting the ballot style, unless the cvrs include the entire ballot.
OTOH, it helps in getting more of the 


## what are the contests and candidates?

read 295 contests in src/test/data/corla/2024election/summary.csv (602 candidates)

read 725 contests from src/test/data/corla/2024audit/round1/contest.csv, only has the winning name

All in /home/stormy/dev/github/rla/nealmcb/auditcenter/2024/general:

In targetedContests.xlsl we have

      "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
      "El Paso","City of Colorado Springs Ballot Question 300",1,"130,671","108,300","2,735",5.78%,3%,126,"387,297",,,,,,,,,,,,,,,,,22

      "Lowest Winner" = "130,671"
      "Highest Loser" = "108,300" (vote diff = 22371)
      "Contest Margin"= "2,735"   WTF? should be 22371
      "Diluted Margin"=5.78%
      "Estimated # of CVRs to audit", = 126
      "# of CVRs" = "387297"  // ballot_card_count

2024GeneralCanonicalList.csv seems to have all the contests and candidates

      CountyName,ContestName,ContestChoices
      El Paso,City of Colorado Springs Ballot Question 300,"Yes/For,No/Against"


tabulateCounty.csv, matches targetedContests: (can use this to divide votes by county)

      El Paso,City of Colorado Springs Ballot Question 300,Yes/For,130671
      El Paso,City of Colorado Springs Ballot Question 300,No/Against,108300


contestsByCounty.csv (the last field is "contest id"):

      21,El Paso,City of Colorado Springs Ballot Question 300,1764152

round1/contest.csv:

      contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
         audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,
            optimistic_samples_to_audit,estimated_samples_to_audit
      City of Colorado Springs Ballot Question 300,county_wide_contest,in_progress,1,387297,254918,"""Yes/For""",22371,0.03000000,
         0,0,0,0,0,0,0,1.03905000,0,
            127,127

      winners_allowed=1
      ballot_card_count = 387297
      contest_ballot_card_count = 254918
      min_margin = 22371
      optimistic_samples_to_audit,estimated_samples_to_audit = 127

round1/contestComparison.csv has card-by-card comparision for all contests

round1/contestSelection.csv has the seleced ballot ids:

      22371,City of Colorado Springs Ballot Question 300,"[2406219,2209447,3122832,2075630,3072721,2617763,2909085,1954203,3019522,1840462,3138969,2077653,2526171,2636654,2661316,2673922,2716434,3037407,2165721,1845133,3109451,1803532,2611783,3150469,3009936,2373999,3195765,2269487,2507120,2564348,3189713,2005307,2188524,2741635,2623494,1789845,2139286,2007116,2312917,2225654,2457420,2026641,2300901,3207729,2592312,1769076,2681848,2253717,2289775,2755156,2130473,2245670,2026129,2700217,2790139,3130435,2761016,2378617,2917954,2740690,2651059,2243460,2054726,2733549,2193134,2287263,2709854,2768682,2082298,2292076,2339516,2529299,1854081,2670975,3286093,2317938,1990270,2850081,2911507,2842209,2048750,2912934,3226406,1852755,2056323,2882370,1951002,2836985,3069691,2049669,3153547,2899248,2998229,2863908,2680907,2729879,2285539,2293322,2714455,2513603,1985198,2068145,3104879,3290795,2987808,2454039,2389422,3017981,2707801,1818157,2574210,2378437,3019690,3128045,1923074,2412173,1891707,1930519,2849408,2753919,2594032,3200124,2394762,3087205,2045796,2861113,2827661,2581873]"

round1/ResultsReport.xlsx has seperate sheets for all(?) contests with the card-to-card comparision; and a summary with

      "Contest","targeted","Winner","Risk Limit met?","Risk measurement %","Audit Risk Limit %","diluted margin %","disc +2","disc +1","disc -1","disc -2","gamma","audited sample count","ballot count","min margin","votes for winner","votes for runner up","total votes","disagreement count (included in +2 and +1)"

      "City of Colorado Springs Ballot Question 300","Yes","Yes/For","Yes","2.7","3.0","5.77618700","0","0","0","0","1.03905000","128","387297","22371","130671","108300","238971","0"

      "Contest", = "City of Colorado Springs Ballot Question 300"
      "targeted", = "Yes"
      "Winner",   =   "Yes/For"
      "Risk Limit met?", = "Yes"
      "Risk measurement %", = "2.7"
      "Audit Risk Limit %", = "3.0"
      "diluted margin %", = "5.77618700"
      "disc +2","disc +1","disc -1","disc -2","gamma",
      "audited sample count", = "128"
      "ballot count", = "387297"
      "min margin", = "22371"
      "votes for winner", = "130671"
      "votes for runner up", = "108300"
      "total votes", = "238971"
      "disagreement count (included in +2 and +1)" = 0

this has all previous info except for contest_ballot_card_count, which we use as Nc
there are 725 contests, marked "targeted" both yes and no. this could be where we get our canonical contest list?
but we dont have the candidate list, just the winner name

### simulated CVRs for CLCA audit

We use the published precinct level results to create simulated CVRs and run simulated RLAs. Note that we would need CVRs to do IRV contests, so we cant handle IRV contests.

**createColoradoClca()** assumes we can match the CVRs to physical ballots and does a regular CLCA.
This allows us to compare the cost of OneAudit vs CLCA.

### simulated OneAudit

Not currently done. 

TODO: choose a few rural counties that might be doing hand counts; put them into OneAudit pools, probably by county. 
See how the sample sizes compare to CLCA. Maybe Mesa (107,447	74,421), and El Paso (469,368 voters; 288,059 cast)? This would be
11% of the total vote.

"Ballots are counted using voting systems in every county except San Juan County, which hand-counts ballots".
San Juan (746, 566). With a single pool that small, the effect would be negligible (566/3241120) = .0001746.

### Next Steps

From "Next Steps for the Colorado Risk-Limiting Audit (CORLA) Program" (Mark Lindeman, Neal McBurnett, Kellie Ottoboni, Philip B. Stark. March 5, 2018):

    It is estimated that by June, 2018, 98.2% of active Colorado voters will be in CVR counties.

    First, the current version (1.1.0) of RLATool needs to be modified to recognize and group together contests that 
    cross jurisdictional boundaries; currently, it treats every contest as if it were entirely contained in a single county. 
    Margins and risk limits apply to entire contests, not to the portion of a contest included in a county. 
    RLATool also does not allow the user to select the sample size, nor does it directly allow an unstratified 
    random sample to be drawn across counties. 

    Second, to audit a contest that includes voters in “legacy” counties (counties with voting systems that cannot 
    export cast vote records) and voters in counties with newer systems, new statistical methods are needed to keep the 
    efficiency of ballot-level comparison audits that the newer systems afford. 

    Third, auditing contests that appear only on a subset of ballots can be made much more efficient if the sample can 
    be drawn from just those ballots that contain the contest. While allowing samples to be restricted to ballots 
    reported to contain a particular contest is not essential in the short run, it will be necessary eventually to 
    make it feasible to audit smaller contests.

Also see [Corla Notes](../notes/CorlaNotes.md).
