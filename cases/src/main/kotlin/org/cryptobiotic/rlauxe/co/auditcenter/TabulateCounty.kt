package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import java.io.File
import kotlin.collections.set

// one line for each county and contest ~10K

// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909

// canonical or print message
data class TabulateCounty(
    val contestName:String,
    val contest_name: String,
) {
    val choices = mutableMapOf<String, Int>() // choice name -> nvotes

    override fun toString(): String {
        return "TabulateCounty(county_name='$contestName', contest_name='$contest_name', choices=$choices)"
    }

    companion object {

        fun readFromFile(filename: String, input: StatewideInput): Map<String, TabulateCounty> { // county -> TabulateCounty
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

                    val cc = input.matchCanonicalContest(contest_name)
                    val ccname = if (cc != null) cc.contestName else {
                        println("TabulateCounty cant find canonical name for contest: $contest_name")
                        contest_name
                    }

                    val cchoice = if (cc != null) {
                        input.matchCanonicalCandidate(county_name, cc, choice) ?: choice
                    } else null

                    val useChoice = if (cchoice == null) {
                        println("TabulateCounty cant find canonical choice for contest: $contest_name and choice: $choice")
                        choice
                    } else cchoice

                    val votes=record.get("votes").toInt()
                    val tabulateCounty = tabulateCounties.getOrPut(county_name) { TabulateCounty(county_name, ccname) }
                    tabulateCounty.choices[useChoice] = votes
                }
            }
            return tabulateCounties.toSortedMap()
        }
    }
}

data class TabulateContest(
    val contestName: String,
) {
    val choices = mutableMapOf<String, Int>() // choice name -> votes
    val counties = mutableSetOf<String>()     // county names
    val countyVotes = mutableMapOf<String, Int>() // total votes in county and contest // TODO needed ?

    companion object {

        // canonical if TabulateCounty is
        fun fromTabulateCounty(countyTabs: List<TabulateCounty>):Map<String, TabulateContest> {
            val tabs = mutableMapOf<String, TabulateContest>()
            countyTabs.forEach { countyTab ->
                val tab = tabs.getOrPut(countyTab.contest_name) { TabulateContest(countyTab.contest_name) }
                countyTab.choices.forEach { (choiceName, votes) ->
                        val accum = tab.choices.getOrDefault(choiceName, 0)
                        tab.choices[choiceName] = accum + votes
                    }
                tab.counties.add(countyTab.contestName)
                tab.countyVotes[countyTab.contestName] = countyTab.choices.values.sum()
            }
            return tabs
        }
    }

}