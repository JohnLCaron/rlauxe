package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest
import org.cryptobiotic.rlauxe.auditcenter.ContestIDsByCounty
import org.cryptobiotic.rlauxe.util.Locations.auditcenter


open class Statewide2026Primary(ac:String?=auditcenter): StatewideInput(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv",

) {
    override val year = "2026p"
    val manifestDir = "$ac/2026/primary/files"
    val contestIDsByCountyFile = "$ac/2026/primary/finalReports/ContestIDsByCounty.csv"
    val canonicalCounties: List<String>
    val countyContests: Map<String, List<String>>

    init {
        val cibc = ContestIDsByCounty.readFromFile(contestIDsByCountyFile)
        canonicalCounties = cibc.map { Pair(it.county_name, it.county_id) }.sortedBy { it.second }.toMap().map{ it.key }
        val work = mutableMapOf<String, MutableList<String>>()
        cibc.forEach {
            val cclist = work.getOrPut(it.county_name) { mutableListOf() }
            cclist.add(it.contest_name)
        }
        countyContests = work
        /*
        val ccSet = canonicalCounties.toSet()
        val cc: List<CanonicalContest> = readGeneralCanonicalList(generalCanonicalFile, this)
        val alphaSet = cc.map { it.counties }
            .flatten()
            .filter { !skipCounties(it) }
            .toSet()
            .sortedBy{ it.replace(" ", "").lowercase() } // canonical order has the blanks removed
            .toSet()
        println(alphaSet-ccSet)
        print("") */
    }

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/$noblanks.csv"
    }

    override fun statewideManifest() = statewideManifest
    val statewideManifest: StateManifest by lazy {
        val countyManifests = counties().map { county ->
            val mainfestList = readCountyManifestCsv(manifestFile(county))
            val correctedList = if (county != "Alamosa") mainfestList else
            // Our fresh recount of all 63 manifest CSVs 1,444,036
            // Official ballot_card_count column, live ContestsListRound1.csv	1,444,047

            // Alamosa: 3,464 ballots in the public Alamosa.csv download vs. 3,475 in CDOS’s own reconciliation. Every other county matches exactly.
            // Alamosa,102,138,30,Box 1
            // Tabulator 102, Batch 139, 11 ballots
                // data class ManifestBatch(
                //    val countyName: String,
                //    val tabulatorNum: Int,
                //    val batchId: String,
                //    val nballotCards: Int,
                //    val location: String,
                //)
                mainfestList + listOf(ManifestBatch(county, 102, "139", 11, "Box 1"))
            CountyManifest(county, correctedList)
        }
        StateManifest(countyManifests)
    }

    //////////////////////////////////////////////////////
    override fun skipCounties(countyName: String) = false

    // in canonical manifest order TODO perhaps read the file with county ids ContestIdsByCounty (2026 only)
    override fun counties() = canonicalCounties

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result = initializeCanonicalContests()

        // remove these contests
        result.remove("State Representative - District 7 (REP)")

        // add these missing candidates:
        addCandidates(result, "State Board of Education Member - Congressional District 7 - REP", listOf("Nick Morris"))

        result.toSortedMap()
    }

    override fun contestNameCleanup(county: String, name: String): String {
        return when (county) {
            "La Plata" -> when (name) {
                "Secretary of State" -> "Secretary of State - LBR"
                else -> name
            }
            else -> name
        }
    }

    override fun candidateNameCleanup(county: String, name: String): String {
        if (name.contains("Fiorino")) return "Paul Noel Fiorino"
        val changed = when (name) {
            "Keith Vieweg, Jr" -> "Keith Vieweg"
            "Paul Noël Fiorino" -> "Paul Noel Fiorino"
            else -> name
        }
        return changed
    }
}
