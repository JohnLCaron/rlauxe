"Summit",   "Summit School District RE-1 Ballot Issue 4A",              1,"9,163","6,893","2,270",      12.87%,3%,  56,"17,640",,,,,,,,,,,,,,,,,60
Summit School District RE-1 Ballot Issue 4A,county_wide_contest,in_progress,1,17819,17701,"""No/Against""",2276,0.03000000,0,0,0,0,0,0,0,1.03905000,0,58,58

/////////////////////////////
From targetedContests.csv, the target contest for the 4 counties:

"County",   "Contest", "Vote For",                                          "Lowest Winner","Highest Loser", "Contest Margin",
                                                                                                        "Diluted Margin","Risk Limit",  
                                                                                                                    "Estimated # of CVRs to audit","# of CVRs",
                                                                                                                                    "Remarks",,,,,,,,,,,,,,,,

"Boulder",  "State Representative - District 10",1,                         "23,460","3,720","19,740",    6.99%,3%,   104, "29,261",  "2 ballot cards per ballot*",,,,,,,,,,,,,,,,8
"El Paso",  "City of Colorado Springs Ballot Question 300",1,               "130,671","108,300","2,735",  5.78%,3%,   126, "387,297"  ,,,,,,,,,,,,,,,,,22
"La Plata", "La Plata County Commissioner - District 3", 1,                 "18,987","14,832","4,155",    11.61%,3%,  62, "35,800"    ,,,,,,,,,,,,,,,,,35
"Summit",   "Summit School District RE-1 Ballot Issue 4A", 1,               "9,163","6,893","2,270",      12.87%,3%,  56,"17,640",,,,,,,,,,,,,,,,,60
"Weld",     "District Court Judge - 19th Judicial District - Crowther", 1,  "51,150","40,167","10,983",   9.24%,3%,   78, "118,853"   ,,,,,,,,,,,,,,,,,63

But these dont match the precinct sums:

    Contest 'State Representative District 10' (2100) PLURALITY voteForN=1 votes={24=33889, 23=6290} undervotes=4052, voteForN=1
    winners=[24] Nc=44231 Nphantoms=0 Nu=4052 sumVotes=40179
    24/23 votes=33889/6290 diff=27599 (w-l)/w =0.8144 Npop=44231 dilutedMargin=62.3974% reportedMargin=62.3974% recountMargin=81.4394%
    
    23 'William B. DeOreo': votes=6290
    24 'Junie Joseph': votes=33889  (winner)
    Total=40179

or detail.xml:

````
<Contest key="2100" text="State Representative - District 10" voteFor="1" isQuestion="false" countiesParticipating="1" countiesReported="1" precinctsParticipating="44" precinctsReported="44" precinctsReportingPercent="100.00">
    <ParticipatingCounties>
        <County name="Boulder" precinctsParticipating="44" precinctsReported="44" precinctsReportingPercent="100.00" />
    </ParticipatingCounties>
    <Choice key="23" text="William B. DeOreo" party="REP" totalVotes="6290">
        <VoteType name="Total Votes" votes="6290">
            <County name="Boulder" votes="6290" />
        </VoteType>
    </Choice>
    <Choice key="24" text="Junie Joseph" party="DEM" totalVotes="33889">
        <VoteType name="Total Votes" votes="33889">
            <County name="Boulder" votes="33889" />
        </VoteType>
    </Choice>
</Contest>
````

same with Weld:

"Weld",     "District Court Judge - 19th Judicial District - Crowther", 1,"51,150","40,167","10,983",   9.24%,3%,   78, "118,853"   ,,,,,,,,,,,,,,,,,63

    Contest 'District Court 19th Judicial District Crowther' (3753) PLURALITY voteForN=1 votes={132=75911, 133=60300} undervotes=43656, voteForN=1
    winners=[132] Nc=179867 Nphantoms=0 Nu=43656 sumVotes=136211
    132/133 votes=75911/60300 diff=15611 (w-l)/w =0.2056 Npop=179867 dilutedMargin=8.6792% reportedMargin=8.6792% recountMargin=20.5649%

    132 'Yes': votes=75911  (winner)
    133 'No': votes=60300
    Total=136211

Did I copy the wrong 2024Audit/targetedContests.csv file into the repo? So dont trust that...

This one is ok; from 2024Audit/round1/contests.csv:
                                                                    ballot_card_count,contest_ballot_card_count,winners,min_margin
State Senator - District 10,opportunistic_benefits,in_progress,1,   387297,94146,"""Larry G. Liston""",15533,           0.03000000,0,0,0,0,0,0,0,1.03905000,0,182,182

Boulder pdf has total cards = 396121, margin=27538, diluted margin= 6.95191621 %

## Another problem is:
Corla creates a pool for each precinct, assumes a single ballot style, this will put all contests on a since card. 
But Boulder, eg, has 2 cards, so boulder24/oa (where we know the card styles) has 2x the cards than corla/county/Boulder.

corla/Boulder totalCardCount=196152 2x= 392304
boulder24 totalCardCount=396697


4. How does Corla do their county-level sampling?

Im guessing that it does uniform sampling across all ballots in the county. 
So the population is all ballots in the county, and the sampling is simply a random draw from that population.
The number of samples is based on the margin of the "target" contest. 


Doesnt do "style based sampling" so presumably the margin is fully diluted, ie they didnt try to only sample the pilot contest,
which i think means the sampling is uniform.

We can assume that there are CVRs for all ballots. So we have the county-level subtotals for each contest,
as well as the reported number of ballots for each contest in the county,
which give us the "reported margin" within the county.

If there is an accurate, independent knowledge of the maximum number of ballots for each contest in the county, we can use
that for the trusted bound for each contest (aka Nc). Otherwise we have to use the ballot counts from the CVRs.

Without control over the sampling, we can only do "risk measuring" audits.
So, each county runs an independent audit for all contests, and we measure the risk for each contest in that county.
If the contest is contained within the county, then theres nothing else to be done.

If Corla is using the entire population size for the reported margin, then using the CVRs to determine the contests' actual
ballot count will be a big improvement.
