package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.Double

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

data class ResultsReportContest(
    val contestName: String,
    val targeted: Boolean,
    val winner: String,
    val risk: Double,
    val margin: Double,
    val mvrCount: Int,
    val ballotCount: Int,
    val voteMargin: Int, // test winner - loser
    val winnerVotes: Int,
    val loserVotes: Int,
    val totalVotes: Int, // just the sum of the winner and loser
)

fun readResultsReportContest(filename: String): List<ResultsReportContest> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    records.next() // throw first away
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableListOf<ResultsReportContest>()

    // 0 "Contest","targeted","Winner","Risk Limit met?","Risk measurement %","Audit Risk Limit %",
    // 6     "diluted margin %","disc +2","disc +1","disc -1","disc -2","gamma",
    // 12         "audited sample count","ballot count","min margin","votes for winner","votes for runner up"," total votes","disagreement count (included in +2 and +1)"

    // "Alamosa County Commissioner - District 3","No","Vern Heersink","No","100.0","3.0",
    // "0E-8","0","0","0","0","1.03905000",
    // "0","15216","0","5243","No data","5243","0"

    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            if (line.get(0) == "End of worksheet") break
            contests.add( ResultsReportContest(
                contestName = line.get(0),
                targeted = line.get(1) == "Yes",
                winner = line.get(2),
                risk = line.get(4).toDouble(),
                margin = line.get(6).toDouble(),
                mvrCount = line.get(12).toInt(),
                ballotCount = line.get(13).toInt(),
                voteMargin = line.get(14).toInt(),
                winnerVotes = parseNoData(line.get(15)),
                loserVotes = parseNoData(line.get(16)),
                totalVotes = parseNoData(line.get(17)), // may be "No Data"
            ))
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}

fun parseNoData(s: String): Int {
    return if (s == "No data") -1
    else s.toInt()
}
