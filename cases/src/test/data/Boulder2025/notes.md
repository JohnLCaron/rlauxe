2025 November Coordinated Election
4/22/2026

downloaded from https://bouldercounty.gov/elections/results/

Redacted-CVR-PUBLIC.xlsx    cvrs in Boulder Redacted-Cast-Vote-Record format

2025C-Boulder-County-Official-Statement-of-Votes.csv   precinct subtotals aka Boulder SOVO
````
"Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters*","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
"100","2181207100","Boulder County Ballot Issue 1B","Yes/For","1,596",708,401,16,0
"100","2181207100","Boulder County Ballot Issue 1B","No/Against","1,596",708,291,16,0
"102","2181207102","Proposition LL (Statutory)","Yes/For","1,279",293,263,3,0
"102","2181207102","Proposition LL (Statutory)","No/Against","1,279",293,27,3,0
````

what is "total ballots"
what is "total votes"
why is "total votes" + undervotes != total votes ??

2025C-Boulder-County-Official-Summary-of-Votes.csv      presumambly summary across precincts?
````
"Contest Title","Choice Name","Precinct Count","Active Voters*","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
"City of Boulder Council Candidates","Nicole Speer",60,"71,340","34,109","16,165","22,288",65
"City of Boulder Council Candidates","Rob Kaplan",60,"71,340","34,109","15,867","22,288",65
"City of Boulder Council Candidates","Montserrat Palacios",60,"71,340","34,109","2,957","22,288",65
"City of Boulder Council Candidates","Rob Smoke",60,"71,340","34,109","1,499","22,288",65
"City of Boulder Council Candidates","Maxwell Lord",60,"71,340","34,109","2,853","22,288",65
"City of Boulder Council Candidates","Jennifer Robins",60,"71,340","34,109","14,781","22,288",65
"City of Boulder Council Candidates","Aaron Stone",60,"71,340","34,109","2,707","22,288",65
"City of Boulder Council Candidates","Lauren Folkerts",60,"71,340","34,109","14,222","22,288",65
"City of Boulder Council Candidates","Mark Wallach",60,"71,340","34,109","17,476","22,288",65
"City of Boulder Council Candidates","Matt Benjamin",60,"71,340","34,109","20,276","22,288",65
"City of Boulder Council Candidates","Rachel Rose Isaacson",60,"71,340","34,109","5,085","22,288",65
````

sum (total votes) + undervotes + overvotes = total ballots * voteForN

Nc=Ncast = 34109
nvotes = 113888
undervotes = info.voteForN * Ncast - nvotes = 4 * 34109 - 113888 = 22548

this has Total Ballots = Nc = 34109
this has      info.voteForN * (Nc - overvotes) - nvotes = undervotes = 22288

so, what do we doing with overvotes ?? 

// SHANGRLA section 2, both undervotes and overvotes are given assort values of 0.5.
// So in setting up the contest we can add the undervotes and overvotes together like this:
// Boulder for example has 
//           voteForN * (Ncast - boulderOvervotes) - nvotes = boulderUndervotes
//           voteForN * Ncast - nvotes = boulderUndervotes + voteForN * boulderOvervotes
// so
//       undervotes = voteForN * Ncast - nvotes = boulderUndervotes + voteForN * boulderOvervotes
//
// TODO well that should confuse everyone. maybe we want to store them in Contest anyway?

https://electionresults.bouldercounty.gov/