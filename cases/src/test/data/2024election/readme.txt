03/24/2025
Election Results & Data

https://www.coloradosos.gov/pubs/elections/resultsData.html
https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary

Summary CSV - Comma separated file showing total votes received.
see readColoradoElectionSummaryCsv()
https://results.enr.clarityelections.com//CO//122598/355977/reports/summary.zip
TODO:  Only 8 of 15 candidates show up for president: writeins??

line number","contest name","choice name","party name","total votes","percent of votes","registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM",1728159,54.16,0,0,64,0,"607","2801"
2,"Presidential Electors (Vote For 1)","Donald J. Trump / JD Vance","REP",1377441,43.17,0,0,64,0,"607","2801"
3,"Presidential Electors (Vote For 1)","Blake Huber / Andrea Denault","APV",2196,0.07,0,0,64,0,"607","2801"
4,"Presidential Electors (Vote For 1)","Chase Russell Oliver / Mike ter Maat","LBR",21439,0.67,0,0,64,0,"607","2801"
5,"Presidential Electors (Vote For 1)","Jill Stein / Rudolph Ware","GRN",17344,0.54,0,0,64,0,"607","2801"
6,"Presidential Electors (Vote For 1)","Randall Terry / Stephen E. Broden","ACN",3522,0.11,0,0,64,0,"607","2801"
7,"Presidential Electors (Vote For 1)","Cornel West / Melina Abdullah","UNI",5149,0.16,0,0,64,0,"607","2801"
8,"Presidential Electors (Vote For 1)","Robert F. Kennedy Jr. / Nicole Shanahan","UAF",35623,1.12,0,0,64,0,"607","2801"

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

Detail XLS (295 contests, 92k zipped, 2.6M unzipped )
has a separate sheet for every contest with vote count, by county
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxls.zip
can also get it as an XML (56k zipped, 780k unzipped ):
https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxml.zip

TODO: Put together a simulation using the info from detailxml.zip. We dont have cvrs (except boulder) so would have
      to manufacture those, perhaps like we do for Boulder redacted summaries.
      Create seperate County entities?
      Is this worth the trouble?


PrecinctLevelResults
https://www.sos.state.co.us/pubs/elections/resultsData.html
https://www.sos.state.co.us/pubs/elections/Results/2024/2024GeneralPrecinctLevelResults.xlsx
convert to cvs and zip to corla/src/test/data/2024election/2024GeneralPrecinctLevelResults.zip

County	Precinct	Contest	Choice	Party	Total Votes
ADAMS	4215601243	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	224
ADAMS	4215601244	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	237
ADAMS	4215601245	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	64
...