package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlacvr.readRedactionCount

import kotlin.test.Test
import kotlin.test.assertEquals

class WriteCountyInputData {

    @Test
    fun write2026p() {
        writeCountyData("$cases/corlaState/2026p", Colorado2026PwithCvrs())
    }

    @Test
    fun write2020() {
        writeCountyData("$cases/corlaState/2020", Colorado2020General())
    }

    fun writeCountyData(topdir: String, stateInput: ColoradoInputWithCvrs) {
        val filename = "$topdir/countyInputData.csv"

        val countNredacted: Map<String, Int> = readRedactionCount("/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/redacted.csv")

        val data = mutableListOf<CountyInputData>()
        stateInput.counties().forEach { county ->
            val countyInput = stateInput.corlaCountyInput(county)!!
            val ccc: CheckCvrsAndManifest = CheckCvrsAndManifest(stateInput, countyInput, showMatch = true, showMissingVotes = true, showRedactedCvrs = true)
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
            data.add(CountyInputData(county,
                manifestCount=manifestCounts.totalEntries,
                unredactedCvrs=corlaCvrs.cvrs().size,                            // number of unredacted cvrs
                redactedCvrs=corlaCvrs.redaction().redactedRows().size, // number of redacted cvrs
                cvrInManifest =  manifestCounts.countCvrsInManifest,
                cvrNoManifest = manifestCounts.cvrNoManifest,
                manifestNoCvr = manifestCounts.manifestNoCvr,
                countNredacted = countNredacted[county] ?: 0,
                minCards = ccc.minCards))
            println("wrote $county  ${data.last()}")
        }
        writeCountyInputData(filename, data)

        val roundtrip = readCountyInputData(filename)
        assertEquals(data.sortedBy{it.county}, roundtrip)
    }
}
