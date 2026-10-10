package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.auditcenter.readGeneralCanonicalList
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest

open class Colorado2026Primary(ac:String?=auditcenter): ColoradoInputWithManifests(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv",
    manifestDir = "$ac/2026/primary/files"
) {

    // Our fresh recount of all 63 manifest CSVs	1,444,036
    // Official ballot_card_count column, live ContestsListRound1.csv	1,444,047

    // Alamosa: 3,464 ballots in the public Alamosa.csv download vs. 3,475 in CDOS’s own reconciliation. Every other county matches exactly.
    // Alamosa,102,138,30,Box 1
    // Tabulator 102, Batch 139, 11 ballots
    val statewideManifest: StateManifest by lazy {
        val countyManifests = counties().map { county ->
            val noblanks = county.replace("\\s".toRegex(), "")
            val mainfestList = readCountyManifestCsv("$manifestDir/$noblanks.csv")
            val correctedList = if (county != "Alamosa") mainfestList else
                // data class ManifestBatch(
                //    val countyName: String,
                //    val tabulatorNum: Int,
                //    val batchId: String,
                //    val nballotCards: Int,
                //    val location: String,
                //)
                mainfestList + listOf(ManifestBatch(county, 102, "139", 11,"Box 1"))
            CountyManifest(county, correctedList)
        }
        StateManifest(countyManifests)
    }

    override fun corlaCountyManifest(countyName: String): CountyManifest? {
        return statewideManifest.manifests.find { it.county == countyName }
    }

    override fun corlaStateManifest() = statewideManifest

    //////////////////////////////////////////////////////
    override fun skipCounties(countyName: String) = false

    // in canonical manifest order
    override fun counties(): List<String> {
        val alphaList = canonicalContests().values.map { it.counties }
            .flatten()
            .toSet()
            .sorted()
            .toMutableList()
        alphaList.remove("Broomfield")
        alphaList.add("Broomfield")      // add at end
        return alphaList.toList()
    }

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        // remove these contests
        result.remove("State Representative - District 7 (REP)")

        // add these missing candidates:
        addCandidates(result, "State Board of Education Member - Congressional District 7 - REP", listOf("Nick Morris"))

        result.toSortedMap()
    }

    fun addCandidates(result: MutableMap<String, CanonicalContest>, contestName: String, addCandidates: List<String>) {
        val current = result[contestName]!!
        val achoices = current.choices + addCandidates
        result[contestName] = current.copy(choices = achoices).addCounties(current.counties.toList())
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
