package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestRoundAccum
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestRoundCsv
import org.cryptobiotic.rlauxe.boulder.BoulderInput
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.Locations.cases

import kotlin.collections.forEach

// we have Boulder, La Plata, Morgan, and Weld CVR_export files
class Statewide2026PwithCvrs(ac:String?=auditcenter): Statewide2026PMerged (ac) {
    override val year = "2026pc"

    val useCounties = setOf("Boulder", "La Plata", "Morgan", "Weld") // the counties we have cvrs for
    override fun skipCounties(countyName: String) = !useCounties.contains(countyName)
    override fun counties(): List<String> = useCounties.toList().sorted()

    // should not be needed
    val countyPopulations = mapOf( "Boulder" to 100423, "La Plata" to 16146, "Morgan" to 5220,"Weld" to 69640)

    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val ccmap = mutableMapOf<String, CanonicalContest>()
        super.canonicalContests().values.forEach {
            val included = it.counties.any { useCounties.contains(it) }
            if (included) {
                val key = contestNameMerge(it.contestName)
                val cc = ccmap.getOrPut(key) { it }
                cc.counties.addAll(it.counties)
            }
        }
        ccmap.toSortedMap()
    }

    // why override ??
    override fun roundContests(): Map<String, CorlaContestRoundCsv> = roundContests
    private val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        val crmap = mutableMapOf<String, CorlaContestRoundAccum>()
        super.roundContests().values.forEach {
            if (canonicalContests.contains(it.contestName)) {
                val key = contestNameMerge(it.contestName)
                val cr = crmap.getOrPut(key) { CorlaContestRoundAccum(it) }
                cr.add( it )
            }
        }
        crmap.mapValues { it.value.build() }.toSortedMap()
    }

    // data class MergedInfo(
    //    val mergedContestInfo: List<MergedContestInfo>,
    //    val strataInfo: List<StrataInfo>,
    //    val statewideContests: List<CorlaContestRoundCsv>,
    //)

    /* override fun mergedInfo() = mergedInfo
    private val mergedInfo: MergedInfo by lazy {
        val contestTabs = contestTabsAllCounties()
        val orgInfo = mergeContestInfo(this)
        val fixContestInfo = orgInfo.mergedContestInfo.map { info ->
            val contestTab = contestTabs[info.contestName]!!
            var sumCards = 0
            contestTab.counties.forEach {
                sumCards += (countyPopulations[it] ?: 0)
            }
            info.copy(corlaNc = sumCards) // replace Nc
        }
        orgInfo.copy(mergedContestInfo = fixContestInfo) // replace MergedInfo
    }

    override fun strataPopulation() = strataPopulation
    private val strataPopulation: Map<String, Int> by lazy { mergedInfo().strataInfo.associate { it.strataName to it.population } } // county name to population
    */

    override fun contestNameCleanup(county: String, name: String): String {
        if (county == "La Plata" && name == "Secretary of State") return "Secretary of State - LBR"
        return super.contestNameMerge(name)
    }

}

class CountyInput2026p(val county: String): CountyInput {
    override val electionName = "$county-2026p"
    override val countyName = county
    val countyZ = county.replace("\\s".toRegex(), "")
    override val manifestSource = "$auditcenter/2026/primary/files/${countyZ}.csv"
    override val cvrsSource: String
    override val year = "2026pc"

    init {
        val filename = when (county) {
            "La Plata" -> "LaPlata_RedactedTest_NoContestBalance_CVR_Export_20260709081424.csv"
            "Morgan" -> "Morgan_CVR_Export_20260709092315_Redacted.csv"
            "Weld" -> "Weld_CVR_Export_20260709140755.csv"
            else -> "none"
        }
        cvrsSource = "$auditcenter/2026/primary/observerfiles/$filename"
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}

class Boulder26PInput(): BoulderInput  {
    val cvrdir = "$cases/corla/2026p/cvrs"
    override val electionName= "Boulder2026p"
    override val cvrsSource = "$cvrdir/Boulder2026P-Redacted-CVR-Public.csv"
    override val manifestSource = "$auditcenter/2026/primary/files/Boulder.csv"
    override val sovoSource = "/resources/data/cases/boulder2026p/2026P-Boulder-County-Official-Statement-of-Votes.csv"
    override val year = "2026pc"

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }

    override fun readCorlaCvrs() = readCountyCvrs(cvrsSource, redaction = RedactionBoulder())
}
