package org.cryptobiotic.rlauxe.co.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
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

private val logger = KotlinLogging.logger("TabulateCounty")

// canonical or log warning message
data class TabulateCounty(
    val countyName:String,
    val contestName: String,
) {
    val choices = mutableMapOf<String, Int>() // choice name -> nvotes

    override fun toString(): String {
        return "TabulateCounty(county_name='$countyName', contest_name='$contestName', choices=$choices)"
    }

    companion object {

        fun readFromFile(filename: String, input: StatewideInput): Map<String, Map<String, TabulateCounty>> { // county -> contest -> TabulateCounty
            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            val countyMap = mutableMapOf<String, MutableMap<String, TabulateCounty>>()
            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val county_name = record.get("county_name")
                    val contest_name = record.get("contest_name")
                    val choice=record.get("choice")

                    val cc = input.matchCanonicalContest(county_name, contest_name)
                    val ccname = if (cc != null) cc.contestName else {
                        logger.warn{ "TabulateCounty cant find canonical name for contest: $contest_name" }
                        contest_name
                    }

                    val cchoice = if (cc != null) {
                        input.matchCanonicalCandidate(county_name, cc, choice) ?: choice
                    } else null

                    val useChoice = if (cchoice == null) {
                        logger.warn{ "TabulateCounty cant find canonical choice for contest: $contest_name and choice: $choice" }
                        choice
                    } else cchoice

                    val votes=record.get("votes").toInt()

                    val contestMap = countyMap.getOrPut(county_name) { mutableMapOf() }
                    val contestTab = contestMap.getOrPut(ccname) { TabulateCounty(county_name, ccname) }
                    contestTab.choices[useChoice] = votes
                }
            }
            return countyMap.toSortedMap()
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
        fun fromTabulateCounty(countyMap: Map<String, Map<String, TabulateCounty>>): Map<String, TabulateContest> {
            val tabs = mutableMapOf<String, TabulateContest>()
            countyMap.forEach { (county, contestMap) ->
                contestMap.forEach { (contestName, countyTab) ->
                    val tab = tabs.getOrPut(countyTab.contestName) { TabulateContest(countyTab.contestName) }
                    countyTab.choices.forEach { (choiceName, votes) ->
                        val accum = tab.choices.getOrDefault(choiceName, 0)
                        tab.choices[choiceName] = accum + votes
                    }
                    tab.counties.add(countyTab.countyName)
                    tab.countyVotes[countyTab.countyName] = countyTab.choices.values.sum()
                }
            }
            return tabs
        }
    }

}