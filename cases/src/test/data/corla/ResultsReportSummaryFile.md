# ResultsReportSummary

summarize audited ballots for each round

````
data class ResultsReportContest(
    val contestName: String,
    val targeted: Boolean,
    val winner: String,
    val risk: Double,
    val margin: Double,
    val mvrCount: Int,
    val ballotCount: Int,
    val voteMargin: Int,    // winner - loser
    val winnerVotes: Int,
    val loserVotes: Int,
    val totalVotes: Int,    // just the sum of the winner and loser
}
````

// ResultsReportSummary.csv is the summary tab from ResultsReport.xlsx, exported to csv
//
// "Summary","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data","No data"
// "Contest","targeted","Winner","Risk Limit met?","Risk measurement %","Audit Risk Limit %",
//      "diluted margin %","disc +2","disc +1","disc -1","disc -2","gamma",
//          "audited sample count","ballot count","min margin","votes for winner","votes for runner up"," total votes","disagreement count (included in +2 and +1)"
// "Adams County Commissioner - District 5","Yes","Lynn Baca","Yes","2.8","3.0","3.78814100","0","0","0","0","1.03905000","194","468858","17761","114772","97011","211783","0"
// "Alamosa County Commissioner - District 1","Yes","Lori Laske","Yes","2.4","3.0","11.54705600","0","0","0","0","1.03905000","65","15216","1757","4530","2773","7303","0"
// "Amendment 80 (CONSTITUTIONAL) - Rio Blanco","Yes","Yes/For","Yes","2.4","3.0",
//      "12.71459200","0","0","0","0","1.03905000",
//          "59","3728","474","2013","1539","3552","0"



"Contest","targeted","Winner","Risk Limit met?","Risk measurement %","Audit Risk Limit %","diluted margin %","disc +2","disc +1","disc -1","disc -2","gamma","audited sample count","ballot count","min margin","votes for winner","votes for runner up","total votes","disagreement count (included in +2 and +1)"

"Adams County Commissioner - District 5","Yes","Lynn Baca","Yes","2.8","3.0","3.78814100","0","0","0","0","1.03905000","194","468858","17761","114772","97011","211783","0"
"Alamosa County Commissioner - District 1","Yes","Lori Laske","Yes","2.4","3.0","11.54705600","0","0","0","0","1.03905000","65","15216","1757","4530","2773","7303","0"
"Amendment 80 (CONSTITUTIONAL) - Rio Blanco","Yes","Yes/For","Yes","2.4","3.0","12.71459200","0","0","0","0","1.03905000","59","3728","474","2013","1539","3552","0"
"Archuleta County Commissioner - District 1","Yes","Warren M. Brown","Yes","2.2","3.0","19.79133700","0","0","0","0","1.03905000","38","9489","1878","4657","2779","8437","0"
"Bent County Commissioner-District 1","Yes","Jennifer Scofield","Yes","1.9","3.0","24.17829800","0","0","0","0","1.03905000","32","2221","537","1331","794","2125","0"
