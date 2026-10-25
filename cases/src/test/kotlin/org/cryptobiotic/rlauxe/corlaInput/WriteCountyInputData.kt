package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteCountyInputData {
    val baseSummaryData = "src/main/resources/resources/data/cases/corla/countySummaryData"

    @Test
    fun testOneCounty() {
        val county = "Larimer"
        val stateInput = Statewide2020Redacted()
        val countyInput = stateInput.corlaCountyInput(county)
        if (countyInput != null) {
            val ccc = CheckCvrsAndManifest(stateInput, countyInput, showMatch = true, showMissingVotes = true, showRedactedCvrs = true)
            val corlaCvrs = ccc.corlaCvrs
            val manifestCounts = ccc.manifestCounts

            val data =
                CountyInputData(
                    county,
                    manifestCount = manifestCounts.totalEntries,
                    unredactedCvrs = corlaCvrs.unredactedCvrs(),
                    redactedCvrs = corlaCvrs.redactedCvrs(),
                    cvrInManifest = manifestCounts.countCvrsInManifest,
                    cvrNoManifest = manifestCounts.cvrNoManifest,
                    manifestNoCvr = manifestCounts.manifestNoCvr,
                    minCards = ccc.minCards,
                    countBlankPrecincts = ccc.countBlankPrecincts,
                    ballotStylesUnique = ccc.ballotStylesUnique,
                    ballotStyleMin = ccc.ballotStyleMin,
                    precinctStyleMin = ccc.precinctStyleMin,
                )
            println("wrote $county  ${data}")
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
    fun writeCountyData(stateInput: StatewideInputWithCvrs) { // }, ourRedactByPrecinct: Map<String, RedactionCount>) {
        // stateInput.counties().forEach { county -> println("'$county',")}
        val stopwatch = Stopwatch()
        val data = mutableListOf<CountyInputData>()
        stateInput.counties().forEach { county ->
            val countyInput = stateInput.corlaCountyInput(county)
            if (countyInput != null) {
                val ccc = CheckCvrsAndManifest(stateInput, countyInput, showMatch = true, showMissingVotes = true, showRedactedCvrs = true)
                val corlaCvrs = ccc.corlaCvrs
                val manifestCounts = ccc.manifestCounts

                // data class ManifestCounts(
                //    val totalEntries: Int,                    // total entries in the manifest
                //    val unmatched: Int,                       // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
                //    val countCvrsInManifest: Int,             // count of Cvrs that match entries in the Manifest
                //    val cvrNoManifest: Int,                  // cvrs without matching manifest entry
                //    val manifestNoCvr: Int,                  // manifest entries without matching cvr
                //    val match: Map<String, ManifestEntry>,    // imprintedId -> ManifestEntry
                //    val redactedIds: List<ManifestEntry>      // didnt match a cvr, assume to be in the redactions
                //)
                data.add(
                    CountyInputData(
                        county,
                        manifestCount = manifestCounts.totalEntries,
                        unredactedCvrs = corlaCvrs.unredactedCvrs(),
                        redactedCvrs = corlaCvrs.redactedCvrs(),
                        cvrInManifest = manifestCounts.countCvrsInManifest,
                        cvrNoManifest = manifestCounts.cvrNoManifest,
                        manifestNoCvr = manifestCounts.manifestNoCvr,
                        minCards = ccc.minCards,
                        countBlankPrecincts = ccc.countBlankPrecincts,
                        ballotStylesUnique = ccc.ballotStylesUnique,
                        ballotStyleMin = ccc.ballotStyleMin,
                        precinctStyleMin = ccc.precinctStyleMin,
                    )
                )
                println("wrote $county  ${data.last()}")
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
}

