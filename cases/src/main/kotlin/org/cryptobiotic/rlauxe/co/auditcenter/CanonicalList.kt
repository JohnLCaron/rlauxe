package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File

// CanonicalListOfContestsAndChoices
// CountyName,ContestName,ContestChoices
// El Paso,City of Colorado Springs Ballot Question 300,"Yes/For,No/Against"
data class CanonicalList(
    val contestName:String,
    val contestChoices: List<String>,
) {
    val counties = mutableListOf<String>()

    override fun toString(): String {
        return "CanonicalList(contestName='$contestName', contestChoices=$contestChoices, counties=$counties)"
    }

    companion object {

        fun readFromFile(filename: String): Map<String, CanonicalList> {
            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            val contests = mutableMapOf<String, CanonicalList>()
            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val countyName = record.get("CountyName")
                    val contestName = record.get("ContestName")
                    val contestChoices=record.get("ContestChoices")
                    val choices = contestChoices.split(",").map { it.trim() }
                    val cc = contests.getOrPut(contestName) { CanonicalList(contestName, choices) }
                    cc.counties.add(countyName)
                }
            }
            return contests.toSortedMap()
        }
    }

}