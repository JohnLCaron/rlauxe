package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest
import org.cryptobiotic.rlauxe.auditcenter.ContestIDsByCounty
import org.cryptobiotic.rlauxe.util.Locations.auditcenter

// mostly TODO
open class Statewide2026General(ac:String?=auditcenter): StatewideInput(
    targetContestsFile = "$ac/2026/general/files/2026GeneralRLAContestList.xlsx", // only one that exists so far
    generalCanonicalFile = "$ac/2026/general/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/general/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/general/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/general/finalReports/CVRtoAuditBoardInterpretationComparison.csv",

) {
    override val year = "2026"
    val manifestDir = "$ac/2026/general/files"
    val contestIDsByCountyFile = "$ac/2026/general/finalReports/ContestIDsByCounty.csv"
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
    }

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/$noblanks.csv"
    }

    //////////////////////////////////////////////////////
    override fun corlaCountyInput(countyName: String): CountyInput? = null
    override fun skipCounties(countyName: String) = false

    // in canonical manifest order
    override fun counties() = canonicalCounties

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result = initializeCanonicalContests()

        // TODO

        result.toSortedMap()
    }
}
