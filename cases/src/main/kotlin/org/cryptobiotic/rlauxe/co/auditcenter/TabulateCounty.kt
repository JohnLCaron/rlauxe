package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import java.io.File

// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909
data class TabulateCounty(
    val county_name:String,
    val contest_name: String,
) {
    val choices = mutableMapOf<String, Int>()

    override fun toString(): String {
        return "TabulateCounty(county_name='$county_name', contest_name='$contest_name', choices=$choices)"
    }

    companion object {

        fun readFromFile(filename: String): Map<String, TabulateCounty> {
            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            val tabulateCounties = mutableMapOf<String, TabulateCounty>()
            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val county_name = record.get("county_name")
                    val contest_name = record.get("contest_name")
                    val choice=record.get("choice")
                    val votes=record.get("votes").toInt()
                    val tabulateCounty = tabulateCounties.getOrPut(county_name) { TabulateCounty(county_name, contest_name) }
                    tabulateCounty.choices[choice] = votes
                }
            }
            return tabulateCounties.toSortedMap()
        }
    }

}