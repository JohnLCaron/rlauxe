package org.cryptobiotic.rlauxe.auditcenter

class Colorado2026Primary(ac:String?=auditcenter): ColoradoInput(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv"
) {
    override val skipCounties = listOf<String>()

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        /* add these missing contests:
        val extras = listOf(
            CanonicalContest("Adams County Assessor - DEM", choices=listOf("Ken Musso",)).addCounties(listOf("Adams",))
        )
        extras.forEach { result[it.contestName] = it } */

        // add these missing candidates:
        addCandidates(result, "State Board of Education Member - Congressional District 7 - REP", listOf("Nick Morris"))

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

    // same here - Garfield ??
    // Adams,Adams County - United States Senator - REP,"Ron Hanks, Joe O'Dea, Daniel Hendricks"
    // Gilpin,United States Senator - REP,"Ron Hanks, Joe O'Dea, Daniel Hendricks"
    // Garfield,Garfield County - United States Senator - REP,"Ron Hanks, Joe O'Dea, Daniel Hendricks"

    // ------------------------- checkContestTabulateHasCanonical
    //    missing choice  'Daniel Hendricks' in contestTab 'Garfield County - United States Senator - REP'


    override fun contestNameCleanup(county: String, name: String): String {

        val transform = when (county) {
            "Garfield" -> when (name) {
                else -> null
            }
            else -> null
        }
        if (transform != null) return transform

        // let counties have first pass as transform, then the general case
        return name
    }

    override fun candidateNameCleanup(county: String, name: String): String {
        return name
    }
}
