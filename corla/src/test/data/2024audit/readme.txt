AuditCenter
Mar 24, 2025

Files downloaded from:

https://www.coloradosos.gov/pubs/elections/auditCenter.html

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

===================
targetedContests.xlsx

County	    Contest	       Vote For	    Winner	    Loser	    Margin	Margin%	EstimatedCVRs	# of CVRs	Remarks

Colorado	Presidential Electors	1	1,374,175	1,084,812	289,363 8.15%	89      2,554,611	Audited in all 64 counties
Boulder	    State Representative 10	1	23,460	    3,720	    19,740	6.99%	104	    29,261	    2 ballot cards per ballot

where do they get the Margin% = "Diluted Margin": dilutedMargin = 27538.0 / 396121.0 = 0.0695
   Colorado 289,363 / 2,554,611 = .113  not .0815
   Boulder 19,740 / 29,261 = .674  not .0699

spreadsheet doesnt appear to have any formulas

What is EstimatedCVRs?

spreadsheet has this note:
"The assumption has been made in the "Estimated # of CVRs to audit" value that all ballot cards were returned for each ballot.
Therefore, the number of CVRs is the ballots cast total multiplied by the number of cards.
As the average number of cards per ballot decreases the number of ballots to audit will also decrease."

"So number of CVRs" has to do with the cards vs the ballots ?

-------------------
round1/contest.csv

contest_name,audit_reason,random_audit_status,winners_allowed,          ballot_card_count, contest_ballot_card_count, winners, min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

State Representative - District 10,county_wide_contest,in_progress,1,   396121, 44675,"""Junie Joseph""",27538,0.03000000,0,0,0,0,0,0,0,1.03905000,0,105,105
State Representative - District 10	county_wide_contest	in_progress	1	396121	44675	"Junie Joseph"	27538	0.03	0	0	0	0	0	0	0	1.03905	0	105	105

ballot card count = 396121
contest_ballot_card_count = 44675
min_margin = 27538
all errors are 0
gamma = 1.03905
optimistic_samples_to_audit = 105
estimated_samples_to_audit = 105

estimated cvrs is optimistic function in corla (see OptimisticTest class):

// min_margin / contest_ballot_card_count
1. dilutedMargin = 27538.0 / 44675.0 = 0.6164073866815892 estSamples = 12.0

// min_margin / ballot_card_count
2. dilutedMargin = 27538.0 / 396121.0 = 0.0695191620742147 estSamples = 105.0

// from targetedContests.xlsx above:
3. dilutedMargin = 0.0699 estSamples = 105.0

==========================================




