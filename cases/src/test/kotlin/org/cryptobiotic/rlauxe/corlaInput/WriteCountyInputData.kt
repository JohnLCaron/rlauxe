package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteCountyInputData {
    val baseSummaryData = "src/main/resources/resources/data/cases/corla/countySummaryData"
    val base2026Precinct = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2026/precinct"
    val base2020Precinct = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/precinct"

    @Test
    fun write2026p() {
        //val ourRedactByPrecinct: Map<String, RedactionCount> = readRedactionCount("$base2026Precinct/redactedCountByPrecinct.csv")
        writeCountyData(Statewide2026PwithCvrs()) //, ourRedactByPrecinct)
    }

    @Test
    fun write2023() {
        //val ourRedactByPrecinct = readRedactionCount("$base2020Precinct/redactedCountByPrecinct.csv")
        writeCountyData(Statewide2023Coordinated()) // , ourRedactByPrecinct)
    }

    @Test
    fun write2020() {
        //val ourRedactByPrecinct = readRedactionCount("$base2020Precinct/redactedCountByPrecinct.csv")
        writeCountyData(Statewide2020General()) // , ourRedactByPrecinct)
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
                val ngroups = corlaCvrs.redaction().groups().size
                // TODO nredactedCvrs:  number of redacted cvrs given in CVR file
                // val nredactedCvrs = corlaCvrs.redaction().nredactedCvrs()

                // data class ManifestCounts(
                //    val totalEntries: Int,                    // total entries in the manifest
                //    val unmatched: Int,                       // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
                //    val countCvrsInManifest: Int,             // count of Cvrs that match entries in the Manifest
                //    val cvrNoManifest: Int,                  // cvrs without matching manifest entry
                //    val manifestNoCvr: Int,                  // manifest entries without matching cvr
                //    val match: Map<String, ManifestEntry>,    // imprintedId -> ManifestEntry
                //    val redactedIds: List<ManifestEntry>      // didnt match a cvr, assume to be in the redactions
                //)

                // data class CountyInputData(val county: String, val manifestCount: Int, val ncvrs, val cvrInManifest: Int, val cvrNoManifest:Int,
                //    val manifestNoCvr: Int, val ngroups: Int, val minCards: Int)
                data.add(
                    CountyInputData(
                        county,
                        manifestCount = manifestCounts.totalEntries,
                        unredactedCvrs = corlaCvrs.unredactedRows(),
                        redactedCvrs = corlaCvrs.redactedRows(), // TODO some of the redaction may be just in the redaction groups
                        cvrInManifest = manifestCounts.countCvrsInManifest,
                        cvrNoManifest = manifestCounts.cvrNoManifest,
                        manifestNoCvr = manifestCounts.manifestNoCvr,
                        // pythonNredact = ourRedactByPrecinct[county]?.pythonNredact ?: 0,
                        // rlauxeNredact = ourRedactByPrecinct[county]?.rlauxeNredact ?: 0,
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
        println("that took $stopwatch")

        val roundtrip = CountyInputData.readFromResource("/resources/data/cases/corla/countySummaryData/Statewide2026PwithCvrs.csv")
        assertEquals(data.sortedBy{it.county}, roundtrip)
    }
}
