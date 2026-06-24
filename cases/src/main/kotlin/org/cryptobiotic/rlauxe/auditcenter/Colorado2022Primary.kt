package org.cryptobiotic.rlauxe.auditcenter

class Colorado2022Primary(): ColoradoInput(
    generalCanonicalFile = "$primary2022/2022PrimaryRLACounty-CandidateList.csv",
    contestRoundFile = "$primary2022/round_1/contest.csv",
    tabulateCountyFile = "$primary2022/tabulate_county.csv",
    mvrComparisonFile = "$primary2022/round_2/contest_comparison.csv"
) {
    override val skipCounties = listOf<String>()

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

        // add these missing candidates:
        addCandidates(result, "Gilpin County Commissioner - District 2 - REP", listOf("Rick Wenzel"))

        result.toSortedMap()
    }

    fun addCandidates(result: MutableMap<String, CanonicalContest>, contestName: String, addCandidates: List<String>) {
        val current = result[contestName]!!
        val achoices = current.choices + addCandidates
        result[contestName] = current.copy(choices = achoices).addCounties(current.counties.toList())
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

    companion object {
        private val primary2022 = "$auditcenter/2022/primary"
    }
}
