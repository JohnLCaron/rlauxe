package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.boulder.BoulderInput
import org.cryptobiotic.rlauxe.boulder.BoulderStatementOfVotes
import org.cryptobiotic.rlauxe.boulder.readBoulderSOVfromResourcePath
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs

open class Statewide2023Coordinated(ac:String?=auditcenter): StatewideInputWithCvrs(
    generalCanonicalFile = "$ac/2023/coordinated/canonicalListFinal.csv",
    contestRoundFile = "$ac/2023/coordinated/round_1/contest.csv",
    tabulateCountyFile = "$ac/2023/coordinated/tabulateCounty.csv",
    mvrComparisonFile = "$ac/2023/coordinated/round_2/CVRtoAuditBoardInterpretationComparison.csv"
) {
    override val year = "2023"
    val manifestDir = "$ac/2023/coordinated/round_1"

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/${noblanks}.csv"
    }

    override fun corlaCountyInput(countyName: String): CountyInput? {
        if (countyName == "Boulder") return Boulder23Input()
        return null
    }
    // missing manifests
    private val skipCounties = listOf("Delta", "Douglas","Moffat","Phillips","Sedgwick","Summit")
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

class Boulder23Input(): BoulderInput {
    override val electionName = "Boulder2023"
    override val manifestSource = "$auditcenter/2023/coordinated/round_1/Boulder.csv"
    override val cvrsSource = "/resources/data/cases/boulder2023/Redacted-2023Coordinated-CVR.csv"
    override val sovoSource = "/resources/data/cases/boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv"
    val sovoRcvSource = "/resources/data/cases/boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv"
    override val year = "2023"

    override fun readCorlaCvrs() = readCountyCvrs(cvrsSource, redaction = RedactionBoulder())

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }

    override fun sovo(): BoulderStatementOfVotes {
        val sovo = readBoulderSOVfromResourcePath(sovoSource, "Boulder2023")
        val sovoRcv = readBoulderSOVfromResourcePath(sovoRcvSource, "Boulder2023Rcv")
        return BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))
    }

    override fun contestNameCleanup(name: String): String {
        if (name == "City of Longmont - Mayor") return "City of Longmont Mayor"
        if (name.startsWith("Estes Park School")) return removeParen(name)
        if (name.startsWith("Thompson R2-J"))
            return removeParen(name.replace("R2-J", "R-2J"))
        return name
    }

    override fun candidateNameCleanup(name: String): String {
        if (name == "Jorge Chávez")
            return "Jorge Chavez"
        return name
    }
}

