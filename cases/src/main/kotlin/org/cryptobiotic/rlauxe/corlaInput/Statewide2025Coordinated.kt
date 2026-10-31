package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.boulder.BoulderInput
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs
import org.cryptobiotic.rlauxe.util.Locations.auditcenter

open class Statewide2025Coordinated(ac:String?=auditcenter): StatewideInput (
    generalCanonicalFile = "$ac/2025/canonicalList.csv", // Needs ISO-8859-1
    contestRoundFile = "$ac/2025/round1/contest.csv",
    tabulateCountyFile = "$ac/2025/tabulateCountyPlurality.csv",
    mvrComparisonFile = "$ac/2025/finalReports/contestComparison.csv",
) {
    override val year = "2025"
    val manifestDir = "$ac/2025/files"

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/${noblanks}Manifest.csv"
    }

    override fun corlaCountyInput(countyName: String): CountyInput? {
        return when (countyName) {
            "Boulder" -> Boulder25Input()
            "Broomfield", "Larimer" -> CountyInput2025(countyName)
            else -> null
        }
    }

    private val skipCounties = emptyList<String>() // listOf("Baca", "Gunnison", "Las Animas", "San Juan")
    override fun skipCounties(countyName: String) = skipCounties.contains(countyName)

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result = initializeCanonicalContests()

        val extras = listOf(
            CanonicalContest(this, "Academy School District 20 Board of Directors - 4 Year Term", listOf("Holly Tripp","Susan Payne","Eddie Waldrep","Brandon Clark","RenÃ©e Malloy Ludlam","Jennafer Stites","Cynthia Halverson",)).addCounties(listOf("El Paso",))
        )
        extras.forEach { result[it.contestName] = it }

        addCandidates(result, "City of Canon City - City Council - At Large", listOf("Dominic Condoluci",))
        addCandidates(result, "Glade Park Fire Protection District 2 year term", listOf("Jeremy Edward Carns","Andy Jack Hanks",))
        addCandidates(result, "Glade Park Fire Protection District 4 year term", listOf("Charles R. Novinski","David F. Brach","Lance Kramer",))
        addCandidates(result, "East Otero School District R-1 School Board Member At Large", listOf("Amanda Gingrich",))

        // these are the IRVs
        result.remove("City of Fort Collins Councilmember - District 1")
        result.remove("City of Fort Collins Councilmember - District 3")
        result.remove("City of Fort Collins Mayor")

        /*
        these are flipped in auditcenter
        addCandidates(result, "Glade Park Fire Protection District 2 year term", listOf("Jeremy Edward Carns","Andy Jack Hanks",))
        addCandidates(result, "Glade Park Fire Protection District 4 year term", listOf("Charles R. Novinski","David F. Brach","Lance Kramer",))
         */

        result.toSortedMap()
    }

    override fun contestTabsAllCounties(): Map<String, ContestTabAllCounties>  = contestTabsAllCounties
    private val contestTabsAllCounties: Map<String, ContestTabAllCounties> by lazy {
        val tabs = mutableMapOf<String, ContestTabAllCounties>()
        countyTabsAllContests().values.forEach { countyTabAllContests ->
            countyTabAllContests.contests.forEach { (contestName, countyContestVotes) ->
                val tab = tabs.getOrPut(contestName) { ContestTabAllCounties(contestName) }
                tab.add(countyTabAllContests.countyName, countyContestVotes)
            }
        }
        tabs.toMap()
    }

    override fun contestNameCleanup(county: String, name: String): String {
        val countyInput = corlaCountyInput(county)
        return countyInput?.contestNameCleanup(name) ?: name
    }

    override fun candidateNameCleanup(county: String, name: String): String {
        return when (name) {
            "Bryon Charles Colvin" -> "Byron Charles Colvin"
            else -> {
                val countyInput = corlaCountyInput(county)
                countyInput?.candidateNameCleanup(name) ?: name
            }
        }
    }
}

class CountyInput2025(val county: String): CountyInput {
    override val electionName = "$county-2025"
    override val countyName = county
    override val manifestSource = "$auditcenter/2025/files/${county}Manifest.csv"
    override val cvrsSource: String
    override val year = "2025"

    init {
        val filename = when (county) {
            "Broomfield" -> "broomfield-Redacted_CVR_Export_20251119093616.csv"
            "Larimer" -> "larimer-county-cast-vote-record-2025-coordinated-electionredacted.csv"
            else -> "none"
        }
        cvrsSource = "$auditcenter/2025/cvrs/$filename"
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}

class Boulder25Input(): BoulderInput  {
    override val electionName= "Boulder2025"
    override val manifestSource = "$auditcenter/2025/files/BoulderManifest.csv"
    override val cvrsSource = "$auditcenter/2025/cvrs/2025-boulder-Redacted-CVR-PUBLIC.csv"
    override val sovoSource = "/resources/data/cases/boulder2025/2025C-Boulder-County-Official-Statement-of-Votes.csv"
    override val year = "2025"

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }

    override fun readCorlaCvrs() = readCountyCvrs(cvrsSource, redaction = RedactionBoulder())
}