package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.corla.CorlaStateContestInfoBuilder
import org.cryptobiotic.rlauxe.corla.ManifestCounts
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyElection
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlacvr.CardStyleId
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteCountyInputData {
    val baseSummaryData = "src/main/resources/resources/data/cases/corla/countySummaryData"

    @Test
    fun testOneCounty() {
        val county = "Otero"
        val stateInput = Statewide2020Redacted()
        val countyInput = stateInput.corlaCountyInput(county)
        if (countyInput != null) {
            val data = makeCountyInputData(county, stateInput, countyInput)
            println("made ${data}")
        }
    }

    @Test
    fun writeOneState() {
        writeCountyData(Statewide2020General())
    }

    @Test
    fun writeAllStateInput() {
        writeCountyData(Statewide2020General())
        writeCountyData(Statewide2020Redacted())
        //writeCountyData(Statewide2022Primary())
        writeCountyData(Statewide2023Coordinated())
        writeCountyData(Statewide2024General())
        writeCountyData(Statewide2025Coordinated())
        //writeCountyData(Statewide2026Primary())
        //writeCountyData(Statewide2026PMerged())
        writeCountyData(Statewide2026PwithCvrs())
    }

    // ourRedactByPrecinct written by TestAnonScenarios
    fun writeCountyData(stateInput: StatewideInput) { // }, ourRedactByPrecinct: Map<String, RedactionCount>) {
        // stateInput.counties().forEach { county -> println("'$county',")}
        val stopwatch = Stopwatch()
        val data = mutableListOf<CountyInputData>()
        stateInput.counties().forEach { county ->
            val countyInput = stateInput.corlaCountyInput(county)
            if (countyInput != null) {
                data.add(makeCountyInputData(county, stateInput, countyInput))
                println("made ${data.last()}")
            }
        }
        val filename = "$baseSummaryData/${stateInput.name()}.csv"
        writeCountyInputData(filename, data)
        println("wrote ${data.size} counties to $filename $stopwatch")
        println("that took $stopwatch")

        data.sortedBy {it.county}.forEach { println(it)}
        val roundtrip = readCountyInputData(filename)
        assertEquals(data.sortedBy {it.county} , roundtrip)
    }

    fun makeCountyInputData(county: String, stateInput: StatewideInput, countyInput: CountyInput): CountyInputData {
        val manifest = countyInput.readManifestEntries()

        return if (countyInput.hasCvrs()) {
            val contestBuilder = CorlaStateContestInfoBuilder(stateInput)
            val countyElection = CorlaCountyElection(countyInput, stateInput, contestBuilder, MvrSource.testPrivateMvrs,
                true, ElectionVariantEnum.OnePool)

            val corlaCvrs = countyElection.corlaCvrs
            val manifestCounts = manifest.manifestCounts(corlaCvrs)

            CountyInputData(
                county,
                manifestCount = manifestCounts.totalEntries,
                unredactedCvrs = corlaCvrs.unredactedCvrs(),
                redactedCvrs = corlaCvrs.redactedCvrs(),
                cvrInManifest = manifestCounts.countCvrsInManifest,
                cvrNoManifest = manifestCounts.cvrNoManifest,
                manifestNoCvr = manifestCounts.manifestNoCvr,
                minCards = -1,
                countBlankPrecincts = corlaCvrs.countBlankPrecincts(),
                ballotStylesUnique = corlaCvrs.ballotStyleUnique(),
                ballotStyleMin = corlaCvrs.cardStyles().minOf{ it.ncards },
                precinctStyleMin = findPrecinctStyleMin(corlaCvrs), // maybe "rare_cards" would be more useful
            )
        } else {
            CountyInputData(
                county,
                manifestCount = manifest.totalCards,
                unredactedCvrs = -1,
                redactedCvrs = -1,
                cvrInManifest = -1,
                cvrNoManifest = -1,
                manifestNoCvr = -1,
                minCards = -1,
                countBlankPrecincts = -1,
                ballotStylesUnique = false,
                ballotStyleMin = -1,
                precinctStyleMin = -1
            )
        }
    }
}

fun findPrecinctStyleMin(corlaCvrs: CorlaCountyCvrsIF): Int {

    class UniqueContests() {
        val contests = mutableMapOf<Set<Int>, Int>() // count unique contests within the precinct
        var ncards = 0
        var styleMap = emptyMap<String, Int>()

        fun convert(ballotType:String, cardStyleMap: Map<CardStyleId, CvrCardStyle>) {
            styleMap = contests.mapKeys { cardStyleMap[CardStyleId(ballotType, it.key)]?.cardStyle ?: "unknown" }
        }
    }

    val styleCounters = mutableMapOf<Pair<String, String>, UniqueContests>()
    corlaCvrs.cvrs().forEach { cvr ->
        val id = Pair(cvr.ballotType, cvr.precinctPortion ?: "none")
        val unique = styleCounters.getOrPut(id) { UniqueContests() }
        val count = unique.contests.getOrDefault(cvr.contests(), 0)
        unique.contests[cvr.contests()] = count + 1
        unique.ncards++
    }
    styleCounters.forEach { it.value.convert(it.key.first, corlaCvrs.cardStyleMap()) }

    return styleCounters.values.minOf { it.ncards }
}

