package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.CardComparisonResults
import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.auditcenter.ContestIDsByCounty
import org.cryptobiotic.rlauxe.auditcenter.ContestMvrCount
import org.cryptobiotic.rlauxe.auditcenter.CountyMvrCount
import org.cryptobiotic.rlauxe.auditcenter.CountyStylesFromMvrs
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.auditcenter.readGeneralCanonicalList
import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.Locations.home
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

// mostly TODO
open class Statewide2026General(ac:String?=auditcenter): StatewideInput(
    targetContestsFile = "$home/rla/cases/corla/2026/auditcenter/2026GeneralRLAContestList.csv",
    generalCanonicalFile = "$home/rla/cases/corla/2026/auditcenter/2026GeneralCanonicalList.csv",
    contestRoundFile = "$home/rla/cases/corla/2026/auditcenter/contest.csv",
    tabulateCountyFile = "$home/rla/cases/corla/2026/auditcenter/tabulateCountyPlurality.csv",
    mvrComparisonFile = "$home/rla/cases/corla/2026/auditcenter/notyet.csv",
) {
    override val seed = "38589646444703560910"
    override val year = "2026"

    val manifestDir = "$ac/2026/general/files"
    val cvrdir = "$auditcenter/2026/general/files"
    val manifestdir = "$auditcenter/2026/general/files"

    // val counties: List<String> // TODO canonical order
    val countyManifests = mutableMapOf<String, String>()
    val glob = "*Manifest.csv"

    // val contestIDsByCountyFile = "$ac/2026/general/finalReports/ContestIDsByCounty.csv"
    // val canonicalCounties: List<String>
    // val countyContests: Map<String, List<String>>

    init {
        val path = Path(manifestdir) // or does votedatabase include

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
    override fun corlaCountyInput(countyName: String): CountyInput? = CountyYearInput(cvrdir, manifestdir, year, countyName)
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

    override fun cardComparison() = CardComparisonResults(emptyList(), emptyList(), emptyList(), emptyList())

    override fun contestNameCleanup(county: String, exportContestName: String): String {
        // all from targeted contests
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

        return when {
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
    override val cvrsSource = "$cvrdir/$countyName.csv"
    val countyManifest: CountyManifest

    init {
        val mainfestList = readCountyManifestCsv(manifestSource)
        countyManifest = CountyManifest(countyName, mainfestList)
    }

    override fun readManifestEntries(): ManifestEntries {
        if (countyName == "Garfield") {
            return GarfieldManifestEntries(manifestSource)
        }

        var result = super.readManifestEntries()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase() // ??
        }
        return result
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}

class CountyInput2020(val county: String): CountyInput {
    override val electionName = "$county-2026p"
    override val countyName = county
    val countyZ = county.replace("\\s".toRegex(), "")
    override val manifestSource = "$auditcenter/2026/primary/files/${countyZ}.csv"
    override val cvrsSource: String
    override val year = "2026pc"

    init {
        val filename = when (county) {
            "La Plata" -> "LaPlata_RedactedTest_NoContestBalance_CVR_Export_20260709081424.csv"
            "Morgan" -> "Morgan_CVR_Export_20260709092315_Redacted.csv"
            "Weld" -> "Weld_CVR_Export_20260709140755.csv"
            else -> "none"
        }
        cvrsSource = "$auditcenter/2026/primary/observerfiles/$filename"
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}
