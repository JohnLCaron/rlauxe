
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
