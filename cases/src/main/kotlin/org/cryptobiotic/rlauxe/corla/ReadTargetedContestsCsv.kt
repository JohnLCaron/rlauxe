package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File
import java.nio.charset.Charset

// Read Colorado Audit Round Contest CSV files, eg data/corla/2024audit/targetedContests.csv
// example:
// "Targeted Contests",,,,,,,,,,,,,,,,,,,,,,,,,,
// "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
// "Colorado","Presidential Electors",1,"1,374,175","1,084,812","289,363",8.15%,3%,89,"2,554,611","Audited in all 64 counties",,,,,,,,,,,,,,,,1
// "Colorado","Regent of the University of Colorado - At Large",1,"1,178,343","1,090,844","87,499",2.47%,3%,295,"2,554,611","Audited in all 64 counties",,,,,,,,,,,,,,,,1
// "Adams","Adams County Commissioner - District 5",1,"88,099","74,153","13,946",3.94%,3%,184,"176,893","2 ballot cards per ballot*",,,,,,,,,,,,,,,,2
// "Alamosa","Alamosa County Commissioner - District 1",1,"4,475","2,751","1,724",11.37%,3%,64,"7,579","2 ballot cards per ballot*",,,,,,,,,,,,,,,,3
// "Arapahoe","District Attorney - 18th Judicial District",1,"120,697","87,750","32,947",14.83%,3%,49,"222,213",,,,,,,,,,,,,,,,,4
// ..
// "The assumption has been made in the ""Estimated # of CVRs to audit"" value that all ballot cards were returned for each ballot. Therefore, the number of CVRs is the ballots cast total multiplied by the number of cards. As the average number of cards per ballot decreases the number of ballots to audit will also decrease.",,,,,,,,,,,,,,,,,,,,,,,,,,
// "End of worksheet",,,,,,,,,,,,,,,,,,,,,,,,,,

// "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin",
//      "Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
// "Adams","Adams County Commissioner - District 5",1,"88,099","74,153","13,946",
//      3.94%,3%,184,"176,893"  ,"2 ballot cards per ballot*",,,,,,,,,,,,,,,,2


data class TargetedContestsCsv(
    val countyName: String,
    val contestName: String,
    val nwinners: Int,
    val lowestWinner: Int,
    val highestLoser: Int,
    val voteMargin: Int,
    val dilutedMargin: Double,
    val riskLimit: Double,
    val estimatedSamplesToAudit: Int,
    val numberOfCvrs: Int,
) {
    override fun toString() = buildString {
        val ss = "$countyName, $contestName"
        append("${trunc(ss, -60)}, $nwinners, ${nfn(lowestWinner, 7)}, ${nfn(highestLoser, 7)}, ${nfn(voteMargin, 6)}, ")
        append("  ${trunc(dfn(.01*dilutedMargin, 4), 6)},   ${dfn(riskLimit, 1)}, ")
        append("${nfn(estimatedSamplesToAudit,7)}, ${nfn(numberOfCvrs, 7)}")
    }

    fun short() = buildString {
        val ss = "$countyName, $contestName"
        append("${trunc(ss, -60)}, $nwinners, ${nfn(lowestWinner, 7)}, ${nfn(highestLoser, 7)}, ${nfn(voteMargin, 6)}, ")
}

    companion object {
        val header = "${trunc("contestName, countyName", -53)}, nwinners,  winner, loser, voteMargin, margin, risk%, nsamples, cvrs"
    }
}

fun readTargetedContestsCsv(filename: String): List<TargetedContestsCsv> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the two lines to be the headers
    records.next()
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableListOf<TargetedContestsCsv>()

    // "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin",
//      "Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            if (line.get(0).startsWith("The assumption")) break
            val tcc = TargetedContestsCsv(
                countyName = line.get(0),         // contest_name,
                contestName = line.get(1),         // contest_name,
                nwinners = clean(line.get(2)).toInt(),   // winners_allowed,
                lowestWinner = clean(line.get(3)).toInt(), // ballot_card_count,contest_ballot_card_count,winners,min_margin,
                highestLoser = clean(line.get(4)).toInt(), // contest_ballot_card_count
                voteMargin = clean(line.get(5)).toInt(), // winners
                dilutedMargin = clean(line.get(6)).toDouble(),    // minMargin
                riskLimit = clean(line.get(7)).toDouble(), // riskLimit
                estimatedSamplesToAudit = clean(line.get(8)).toInt(), // estimated_samples_to_audit
                numberOfCvrs = clean(line.get(9)).toInt(), // estimated_samples_to_audit
            )
            contests.add(tcc)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}

fun clean(s: String): String {
    return s.trim().replace("\"", "").replace(",", "").replace("%", "")
}
