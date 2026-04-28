# Risk-limiting audit - 2025 Coordinated Election
files downloaded from https://www.coloradosos.gov/pubs/elections/auditCenter.html
4/22/2026

// voting
contestsByCounty.csv                county id, county name, contest name, contest id
canonicalList.csv                   county, contestName, contestChoices  <--- maybe use this for creating contestInfo? doesnt have nwinners
tabulatePlurality.csv               summary of candidate votes           <--- has the cadidate vote totals, can make the contest
tabulateCountyPlurality.csv         summary of candidate votes by county <--- votes totals by county

no undervotes, Nc, total cards anywhere Ive found....

# 2025CoordinatedRLASelectedContests.xlsx .csv 

county, contest, voteFor, lowest winner, highest loser, margin in votes, diluted margin, risk limit, estimated # cvrs to audit, #cvrs, remarks
````
County	Contest	       Vote For	Lowest Winner	Highest Loser	Contest Margin	Diluted Margin	  Risk Limit	Estimated # of CVRs to audit	# of CVRs	Remarks
Colorado	Proposition MM (STATUTORY)	1	828,425	598,293	230,132	15.9%	3%	45	1,448,499	Audited in all 63 counties
Multi-county	Telluride School District R-1 Board of Directors	1	1,608	1,051	557	15.2%	3%	48	3,399	Audited in Dolores and San Miguel
Adams	City of Thornton Ballot Question 2A	1	14,017	11,862	2,155	2.6%	3%	284	27,597	No Data
Alamosa	City of Alamosa Councilor - At-Large	1	1,323	475	848	21.1%	3%	34	2,005	No Data
Arapahoe	City of Centennial Mayor	1	16,559	12,009	4,550	3.4%	3%	211	34,273	No Data
Archuleta	Town of Pagosa Springs Ballot Issue 2A	1	389	120	269	5.8%	3%	125	513	No Data
Baca	Town of Springfield Ballot Question 2B	1	331	190	141	10.5%	3%	69	542	
Baca	Town of Springfield Ballot Question 2C	1	346	188	158	11.7%	3%	62	542	No Data
Bent	Proposition LL (STATUTORY)	1	606	520	86	7.6%	3%	95	1,128	Single county audit
Boulder	City of Boulder Ballot Issue 2B	1	15,600	8,944	6,656	7.4%	3%	98	26,826	No Data
````

this has voteForN, margin, dilutedMargin 

# canonicalList.csv

CountyName,  ContestName,                                                   ContestChoices
El Paso,     Academy School District 20 Board of Directors - 4 Year Term,   "Holly Tripp,Ren�e Malloy Ludlam,Brandon Clark,Susan Payne,Eddie Waldrep,Jennafer Stites,Cynthia Halverson"
Adams,       Adams 12 Five Star School District Director - District No 3,   "Juan Evans,Ike Anyanwu-Ebo"
Broomfield,  Adams 12 Five Star School District Director - District No 3,   "Juan Evans,Ike Anyanwu-Ebo"
...

// audit
# stateCoordinatedAuditReport.xlsx    summary of audit by county, audited contests, votes, margin

# contestComparison.csv   compare cvr/mvr, just for the targeted contests
````
county_name	contest_name	imprinted_id	ballot_type	choice_per_voting_computer	audit_board_selection	consensus	record_type	audit_board_comment	timestamp	cvr_id	audit_reason
Adams	Adams 12 Five Star School District Director - District No 3	101-1-65	28	"Juan Evans"	"Juan Evans"	YES	uploaded		2025-11-18 09:33:59.346136	844420
Adams	Adams 12 Five Star School District Director - District No 3	101-16-85	35			YES	uploaded		2025-11-18 09:59:19.523036	845943
Adams	Adams 12 Five Star School District Director - District No 3	101-21-73	33	"Juan Evans"	"Juan Evans"	YES	uploaded		2025-11-18 10:03:02.987321	846391
Adams	Adams 12 Five Star School District Director - District No 3	101-21-92	30	"Juan Evans"	"Juan Evans"	YES	uploaded		2025-11-18 10:04:01.692525	846381
````

there are county pdf files with total ballots = total CVRs, W/L margin, diluted margin  for min assertion

