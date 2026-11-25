package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

// mostly TODO
open class Statewide2026General(ac:String?=auditcenter): StatewideInput() {
    override val seed = "38589646444703560910"
    override val year = "2026"

    // encoded in ISO-8859-1 for "CITY OF CÃNON CITY ISSUE 2A"; UTF-8 has C�NON
    override val generalCanonicalFile = "$ac/2026/general/files//2026GeneralCanonicalList.csv"
    override val tabulateCountyFile = "$ac/2026/general/files/tabulateCountyPlurality.csv"
    override val targetContestsFile = "$ac/2026/general/files/2026GeneralRLAContestList.xlsx"

    // round specific
    override val contestRoundFile = "$ac/2026/general/files/round2/contest.csv"
    override val mvrComparisonFile = "$ac/2026/general/files/round2/contestComparison.csv"
    override val contestSelectionFile = "$ac/2026/general/files/round2/contestSelection.csv"
    override val resultsReportFile = "$ac/2026/general/files/round2/resultsReport.xlsx"

    override fun activityReportFile(): String? = "$auditcenter/2026/general/files/round2/activityReport.xlsx"

    val cvrsPresent = listOf("La Plata", "Morgan", )

    val manifestDir = "$ac/2026/general/files"
    val cvrdir = "$auditcenter/2026/general/observerfiles/uploads"

    // val counties: List<String> // TODO canonical order
    val countyManifests = mutableMapOf<String, String>()
    val glob = "*Manifest.csv"

    // val contestIDsByCountyFile = "$ac/2026/general/finalReports/ContestIDsByCounty.csv"
    // val canonicalCounties: List<String>
    // val countyContests: Map<String, List<String>>

    init {
        val path = Path(manifestDir) // or does votedatabase include

        path.listDirectoryEntries(glob=glob).forEach { filepath ->
            val filename = filepath.getFileName().toString()
            val strlen = filename.length
            val county = filename.slice(0 until strlen-12) // 12 is a kludge
            // println(county)
            countyManifests[county] = filepath.toString()
        }
        // counties = countyManifests.keys.toList().sorted()
    }

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/${noblanks}Manifest.csv"
    }

    //////////////////////////////////////////////////////
    // override fun counties() = counties
    override fun corlaCountyInput(countyName: String): CountyInput? {
        return if (countyName in cvrsPresent) CountyYearInput(cvrdir, manifestDir, year, countyName)
          else CountyYearInputNoCvrs(manifestDir, year, countyName)
    }
    override fun skipCounties(countyName: String) = false

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result = initializeCanonicalContests()

        // remove these contests
        // result.remove("State Representative - District 7 (REP)")

        // add these missing candidates:
        addCandidates(result, "Representative to the 120th United States Congress - District 4", listOf("Luis Galven",))
        addCandidates(result, "Home Rule Charter Commission", listOf("Carrie Wise","Rodney Archer","Mary Plese","Bruce (Biff) Warren","D. Scott Tennant","Sue Ellen Peck","Charles Hodges","Jason Seely",))
        addCandidates(result, "Representative to the 120th United States Congress - District 5", listOf("Steven Fuller",))
        addCandidates(result, "State Representative - District 16", listOf("Isaac Contreras",))
        addCandidates(result, "State Representative - District 17", listOf("Sherrea Sterling",))
        addCandidates(result, "El Paso County Commissioner - District 1", listOf("Matt \"MAGS\" Grubesic",))

        result.toSortedMap()
    }

    // all from targeted contests
    // no canon for target contest City of Alamosa Ballot Issue 2A
    // no canon for target contest County Court Judge - Crowley - Boyce
    // no canon for target contest City and County of Denver Councilmember - At-Large -Seat B
    // no canon for target contest County Court Judge - Fremont - Robak
    //no canon for target contest Lincoln County Ballot Issue 1A
    //no canon for target contest County Court Judge - Moffat - Hesson
    //no canon for target contest City of Montrose Ballot Question 2B
    //no canon for target contest City of Holyoke Ballot Measure 2B



    override fun contestNameCleanup(county: String, exportContestName: String): String {
        when (county) {
            "Denver" -> {
                if (exportContestName.contains("Seat B")) return "Councilmember At-Large - Seat B"
            }
            "Lincoln" -> {
                if (exportContestName.contains("Ballot Issue 1A")) return "LINCOLN COUNTY BALLOT ISSUE 1A: LODGING TAX"
            }
            "Moffat" -> {
                if (exportContestName.contains("Hesson")) return "Moffat County Court Judge - Hesson"
            }
            "Montrose" -> {
                if (exportContestName.contains("2B")) return "City of Montrose Term Limits Ballot Question 2B"
            }
            "Phillips" -> {
                if (exportContestName.contains("2B")) return "City of Holyoke Ballot Measure 2B Tax Reallocation"
            }
            else -> {}
        }

        when (exportContestName) {
            "Justice of the Colorado Supreme Court - Hood" -> return "Colorado Supreme Court Justice - Hood"
            else -> {}
        }

        return when {
            // Round Contest probably UTF-8, reading in defailt gives CITY OF CÃNON CITY ISSUE 2A
            exportContestName.contains("ON CITY ISSUE 2A") -> "CITY OF CÃNON CITY ISSUE 2A"
            exportContestName.contains("City of Alamosa Ballot Issue 2A") -> "City of Alamosa Ballot Issue 2A - Extension of Streets Trust Fund Sales Tax"
            exportContestName.contains("Crowley - Boyce") -> "Crowley County Court Judge - Boyce"
            exportContestName.contains("Robak") -> "Fremont County Court Judge - Robak"
            else -> exportContestName
        }
    }
}

class CountyYearInput(
    val cvrdir: String,
    val manifestdir: String,
    override val year: String,
    override val countyName: String,
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    override val electionName = "${countyName}${year}"
    override val manifestSource = "$manifestdir/${countyNameZ}Manifest.csv"
    override val cvrsSource: String

    val countyManifest: CountyManifest // why are we reading this in?

    init {
        val mainfestList = readCountyManifestCsv(manifestSource)
        countyManifest = CountyManifest(countyName, mainfestList)
        cvrsSource = when (countyName) {
            "Boulder" -> "$cvrdir/Boulder/cvr/2026P-Redacted-CVR-Public.csv"
            "La Plata" -> "$cvrdir/La_Plata/cvr/Modified_CVR_Export_20261113122247.csv"
            "Morgan" -> "$cvrdir/Morgan/cvr/Morgan-CVR_Export_20261113104631-redacted.csv"
            else -> throw RuntimeException("There are no cvrs for $countyName")
        }
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}

class CountyYearInputNoCvrs(
    val manifestdir: String,
    override val year: String,
    override val countyName: String,
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    override val manifestSource = "$manifestdir/${countyNameZ}Manifest.csv"
    override val electionName = "${countyName}$year"
    override val cvrsSource = "NONE"
    val countyManifest: CountyManifest

    init {
        val mainfestList = readCountyManifestCsv(manifestSource)
        countyManifest = CountyManifest(countyName, mainfestList)
    }

    override fun hasCvrs() = false
    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }

}
