package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

// Parse the 2024GeneralPrecinctLevelResults.csv file

data class ContestChoice(val choice: String, val totalVotes: Int)

data class ColoradoPrecinctLevelResults(
    val county: String,
    val precinct: String,
) {
    val contestChoices = mutableMapOf<String, MutableList<ContestChoice>>() // contestName -> choices

    override fun toString() = buildString {
        appendLine("ColoradoPrecinctLevelResults(county='$county', precinct='$precinct')")
        contestChoices.forEach {
            appendLine("   '${it.key}':")
            it.value.forEach {
                appendLine("      '${it.choice}': ${it.totalVotes}")
            }
            appendLine()
        }
    }

    fun summ() = buildString {
        appendLine("ColoradoPrecinctLevelResults(county='$county', precinct='$precinct')")
        contestChoices.forEach {
            val summ = it.value.map { it.totalVotes } .sum()
            appendLine("   '${it.key}': $summ")
        }
    }

}

// one line from the precinct csv file (2024GeneralPrecinctLevelResults.csv)
// The total vote for this contest/candidate from the precinct
data class ColoradoPrecinctLevelLine(
    val county: String,
    val precinct: String,
    val contest: String,
    val choice: String,
    val party: String,
    val totalVotes: Int,
)

// "County","Precinct","Contest","Choice","Party","Total Votes"
//"ADAMS","4215601243","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","224"
//"ADAMS","4215601244","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","237"
//"ADAMS","4215601245","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","64"
//"ADAMS","4215601246","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","543"
//"ADAMS","4215601247","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","179"
//"ADAMS","4215601248","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","193"
//"ADAMS","4215601249","Presidential Electors","Kamala D. Harris / Tim Walz","DEM","43"
//"ADAMS","4215601251","Presidential Electors","Kamala D. Harris / Tim Walz","DEM",319
//"ADAMS","4283601267","Presidential Electors","Kamala D. Harris / Tim Walz","DEM",0
//"ADAMS","4285601241","Presidential Electors","Kamala D. Harris / Tim Walz","DEM",6

fun readColoradoPrecinctLevelResults(inputStream: InputStream): List<ColoradoPrecinctLevelResults> {
    val reader: Reader = InputStreamReader(inputStream, "ISO-8859-1")
    val parser = CSVParser(reader, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println(header)

    // val precintResult = mutableListOf<ColoradoPrecinctLevelResult>()

    val precincts = mutableMapOf<String, ColoradoPrecinctLevelResults>()
    var count = 0;
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            if (line.get(0).startsWith("End of worksheet")) break
            val bmi = ColoradoPrecinctLevelLine(
                county = line.get(idx++),
                precinct = line.get(idx++),
                contest = line.get(idx++),
                choice = line.get(idx++),
                party = line.get(idx++),
                totalVotes = line.get(idx++).toInt(),
            )
            val precinctID = "${bmi.county}#${bmi.precinct}"
            val precinct = precincts.getOrPut(precinctID) { ColoradoPrecinctLevelResults(bmi.county, bmi.precinct) }
            val contests = precinct.contestChoices.getOrPut(bmi.contest) { mutableListOf() }
            contests.add(ContestChoice(bmi.choice, bmi.totalVotes))
            count++
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }
    return precincts.values.toList()
}
