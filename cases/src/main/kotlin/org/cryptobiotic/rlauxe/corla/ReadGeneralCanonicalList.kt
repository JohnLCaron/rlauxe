package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset

// 2024GeneralCanonicalList.csv seems to have all the contests and candidates
//
//      CountyName,ContestName,ContestChoices
//      El Paso,City of Colorado Springs Ballot Question 300,"Yes/For,No/Against"

data class CanonicalContest(
    val contestName: String,
    val choices: List<String>
) {
    val counties =  mutableSetOf<String>()

    fun addCounty(county: String, choices: List<String>) {
        if (counties.contains(county)) {
            println("already have this county")
        } else if (this.choices != choices) {
            println("choices do not match")
        } else {
            counties.add(county)
        }
    }

    override fun toString(): String {
        return "CanonicalContest('$contestName', choices=$choices, counties=$counties)"
    }
}

fun readGeneralCanonicalList(filename: String): List<CanonicalContest> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    records.next()
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableMapOf<String, CanonicalContest>()

    // contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
    //   audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
    //   gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            val countyName = line.get(0)
            val contestName = line.get(1)
            val choices = line.get(2).split(",")
            val cc = contests.getOrPut(contestName) { CanonicalContest(contestName, choices) }
            cc.addCounty(countyName, choices)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests.values.toList()
}
