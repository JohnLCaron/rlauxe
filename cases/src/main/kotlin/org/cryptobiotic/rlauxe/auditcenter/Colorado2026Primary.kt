package org.cryptobiotic.rlauxe.auditcenter

open class Colorado2026Primary(ac:String?=auditcenter): ColoradoInput(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv"
) {
    override fun skipCounties(countyName: String) = false

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
        return name
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
