package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.auditcenter.readGeneralCanonicalList
import org.cryptobiotic.rlauxe.boulder.Boulder23Input

open class Statewide2023Coordinated(ac:String?=auditcenter): StatewideInputWithCvrs(
    generalCanonicalFile = "$ac/2023/coordinated/canonicalListFinal.csv",
    contestRoundFile = "$ac/2023/coordinated/round_1/contest.csv",
    tabulateCountyFile = "$ac/2023/coordinated/tabulateCounty.csv",
    mvrComparisonFile = "$ac/2023/coordinated/round_2/CVRtoAuditBoardInterpretationComparison.csv"
) {

    override fun corlaCountyInput(countyName: String, votedatabase: Map<String, String>?): CountyInput? {
        if (countyName == "Boulder") return Boulder23Input()
        return null
    }

    private val skipCounties = emptyList<String>() // listOf("Baca", "Gunnison", "Las Animas", "San Juan")
    override fun skipCounties(countyName: String) = skipCounties.contains(countyName)

    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result = initializeCanonicalContests()

        val extras = listOf(
            CanonicalContest(this, "Academy School District 20 Ballot Issue 4A", listOf("No/Against","Yes/For",)).addCounties(listOf("El Paso",))
        )
        extras.forEach { result[it.contestName] = it }

        addCandidates(result, "City of Commerce City Councilmember - Ward 3", listOf("Rich Trujillo",))
        addCandidates(result, "Deer Trail School District 26J Director of the Board of Education - 4 Year", listOf("John N. Sampson",))
        addCandidates(result, "Consolidated School District C-1 Board of Directors - At Large", listOf("Linda Elaine Brown",))
        // addCandidates(result, "West Grand School District 1-Jt Board of Directors", listOf("Bryon Charles Colvin",))
        addCandidates(result, "RF Mayor", listOf("Kelley Yutterman","Michael \"Scott\" Kerr",))
        addCandidates(result, "Wray City Council", listOf("Brad Rockwell","Benjamin Gardner",))

        result.remove("Bethune School District R-5 Recall Successor Candidate")
        result.remove("Board of Education Director - District 1")
        result.remove("City Council Ward 2")
        result.remove("City of Boulder Mayoral Candidates")
        result.remove("City of Las Animas City Clerk & Treasurer")
        result.remove("Dolores County School District RE-2J Board of Directors - District E")
        result.remove("Silverton School District No. 1 School Board Director")
        result.remove("Weld RE-8 School District Director District A")

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