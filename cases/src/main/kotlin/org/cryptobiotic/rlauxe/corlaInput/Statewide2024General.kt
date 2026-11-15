package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.boulder.BoulderInput
import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.Locations.cases

class Statewide2024General(ac:String?=auditcenter): StatewideInput (
    targetContestsFile = "$ac/2024/general/targetedContests.xlsx", // convert to csv
    generalCanonicalFile = "$ac/2024/general/2024GeneralCanonicalList.csv",
    contestRoundFile = "$ac/2024/general/round1/contest.csv",
    tabulateCountyFile = "$ac/2024/general/tabulateCounty.csv",
    mvrComparisonFile = "$ac/2024/general/round3/contestComparison.csv"
) {
    override val seed = ""
    override val year = "2024"
    val manifestDir = "$ac/2024/general"

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/${noblanks}BallotManifest.csv"
    }

    override fun skipCounties(countyName: String) = false

    override fun corlaCountyInput(countyName: String): CountyInput? {
        return when (countyName) {
            "Boulder" -> Boulder24Input()
            else -> CountyInputNoCvrs(countyName, manifestFile(countyName), year)
        }
    }

    // canonical contests and choices
    override fun canonicalContests(): Map<String, CanonicalContest> = canonicalContests
    private val canonicalContests by lazy {
        val result = initializeCanonicalContests()

        // add these missing contests:
        val extras = listOf(
            CanonicalContest(this, "Bannock Ballot Issue 6A", listOf("Yes", "No")).addCounties(listOf("Douglas")),
            CanonicalContest(this, "Spring Canyon Ballot Issue 6B", listOf("Yes", "No")).addCounties(listOf("Douglas")),
        )
        extras.forEach { result[it.contestName] = it }

        // remove these contests
        result.remove("La Plata County Surveyor")

        result.toSortedMap()
    }

    override fun contestNameCleanup(county: String, name: String): String {
        return when (name) {
            "Justice of the Colorado Supreme Court - Berkenkotter" -> "Colorado Supreme Court Justice - Berkenkotter"
            "Justice of the Colorado Supreme Court - Boatright" -> "Colorado Supreme Court Justice - Boatright"
            "Justice of the Colorado Supreme Court - Marquez" -> "Colorado Supreme Court Justice - Marquez"
            "Colorado Court of Appeals Judge Román" -> "Colorado Court of Appeals Judge Roman"
            else -> name
        }
    }

    override fun candidateNameCleanup(county: String, name: String): String {
        return when (name) {
            "Randall Terry / Stephen E. Broden" -> "Randall Terry / Stephen E Broden"
            "Claudia De la Cruz / Karina García" -> "Claudia De la Cruz / Karina Garcia"
            "Colorado Supreme Court Justice Márquez" -> "Colorado Supreme Court Justice Marquez"
            "Colorado Court of Appeals Judge Román" -> "Colorado Court of Appeals Judge Roman"
            "Daniel Campaña" -> "Daniel Campana"
            else -> name
        }
    }

}

class Boulder24Input(): BoulderInput  {
    override val electionName= "Boulder-2024"
    override val manifestSource = "$auditcenter/2024/general/BoulderBallotManifest.csv"
    val cvrdir = "$cases/corla/2024/cvrs"
    override val cvrsSource = "$cvrdir/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    override val sovoSource = "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"
    override val year = "2024"

    override fun readCorlaCvrs() = readCountyCvrs(cvrsSource, redaction = RedactionBoulder())

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}