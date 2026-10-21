package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaCounty.CountyManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.GarfieldManifestEntries

open class Statewide2020Redacted(
        ac:String?=auditcenter,
        votedatabaseDir: String? = null,
    ): StatewideInputWithCvrs(
    generalCanonicalFile = "$ac/2020/general/canonicalTitleCase.csv",
    contestRoundFile = "$ac/2020/general/round_1/contest.csv",
    tabulateCountyFile = "$ac/2020/general/tabulate_county.csv",
    mvrComparisonFile = "$ac/2020/general/round_3/contestComparison.csv"
) {
    override val year = "2020r"
    val parent = Statewide2020General(ac, votedatabaseDir)
    val manifestDir = "$ac/2020/general/round_1"

    override fun manifestFile(county: String): String {
        val noblanks = county.replace("\\s".toRegex(), "")
        return "$manifestDir/manifest-${noblanks}.csv"
    }

    override fun corlaCountyInput(countyName: String): CountyInput? {
        if (countyName == "Baca") return null
        return if (counties().contains(countyName))
            County2020Redacted(countyName, strataPopulation()[countyName]!!) else null
    }

    // if you skip counties, you have to adjust Nc
    private val skipCounties = listOf("San Juan")
    override fun skipCounties(countyName: String) = skipCounties.contains(countyName)

    // add Gunnison cvr totals to countyTabsAllContests, since those are missing from the tabulateCountyFile file
    override fun countyTabsAllContests() = parent.countyTabsAllContests()
    override fun canonicalContests() = parent.canonicalContests()
    override fun contestNameCleanup(county: String, name: String) = parent.contestNameCleanup(county, name)
    override fun candidateNameCleanup(county: String, name: String) = parent.candidateNameCleanup(county, name)
}

class County2020Redacted(
    override val countyName: String,
    val countyPopulation: Int
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    val cvrdir = "/home/stormy/rla/temp/Anonomice/2020"

    override val electionName = "${countyName}2020"
    // TODO Gunnison has Manifest-Gunnison.csv; but Gunnison is an excluded county in 2020
    override val manifestSource = if (countyName == "Gunnison") "$manifestDir/Manifest-${countyNameZ}.csv"
    else "$manifestDir/manifest-${countyNameZ}.csv"

    override val cvrsSource = "$cvrdir/$countyName-Redacted.csv"
    override val year = "2020r"

    override fun readCountyManifest(): CountyManifestEntries {
        if (countyName == "Garfield") {
            return GarfieldManifestEntries(manifestSource)
        }

        var result = super.readCountyManifest()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase()
        }
        return result
    }

    override fun countyPopulation() = countyPopulation

    companion object {
        val manifestDir = "$auditcenter/2020/general/round_1"
    }
}
