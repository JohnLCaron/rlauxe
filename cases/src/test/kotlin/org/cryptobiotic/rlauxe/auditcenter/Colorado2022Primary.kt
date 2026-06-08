package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList

class Colorado2022Primary(): ColoradoInput(
    generalCanonicalFile = "$auditcenter/2022PrimaryRLACounty-CandidateList.csv",
    contestRoundFile = "$auditcenter/round_1/contest.csv",
    tabulateCountyFile = "$auditcenter/tabulate_county.csv",
    mvrComparisonFile = "$auditcenter/round_2/contest_comparison.csv"
) {
    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        // add these missing contests:
        val extras = listOf(
            CanonicalContest("Adams County Assessor - DEM", choices=listOf("Ken Musso",)).addCounties(listOf("Adams",))
        )
        extras.forEach { result[it.contestName] = it }

        result.toSortedMap()
    }

    // county round
    // just auditing in Arapahoe County ??
    // Arapahoe County - State Senator - District 27 - REP,county_wide_contest,in_progress,1,114629,16590,"""Tom Kim""",6325,0.03000000,0,0,0,0,0,0,0,1.03905000,0,133,133
    // State Senator - District 27 - REP,opportunistic_benefits,in_progress,1,88443,788,"""Tom Kim""",118,0.03000000,0,0,0,0,0,0,0,1.03905000,0,5462,5462

    // canon
    // Arapahoe,State Senator - District 27 - DEM,Tom Sullivan
    // Arapahoe,Arapahoe County - State Senator - District 27 - REP,"Tom Kim, JulieMarie A. Shepherd Macklin"
    // Douglas,State Senator - District 27 - DEM,Tom Sullivan
    // Douglas,State Senator - District 27 - REP,"Tom Kim, JulieMarie A. Shepherd Macklin"

    override fun contestNameCleanup(name: String) = name

    override fun candidateNameCleanup(candName: String) = candName

    companion object {
        private val auditcenter = "/home/stormy/dev/github/rla/nealmcb/auditcenter/2022/primary"
    }
}
