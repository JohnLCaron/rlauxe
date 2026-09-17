package org.cryptobiotic.rlauxe.corlainput

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.CheckCvrsAndManifest
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithCvrs
import org.cryptobiotic.rlauxe.corlaInput.CountyInputData
import org.cryptobiotic.rlauxe.corlaInput.readCountyInputData
import org.cryptobiotic.rlauxe.corlaInput.writeCountyInputData

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

        val data = mutableListOf<CountyInputData>()
        stateInput.counties().forEach { county ->
            val countyInput = stateInput.corlaCountyInput(county)!!
            val ccc = CheckCvrsAndManifest(stateInput, countyInput, showMatch = true, showMissingVotes = true, showRedactedCvrs = true)
            val corlaCvrs = ccc.corlaCvrs
            val manifestCounts = ccc.manifestCounts
            val ngroups = corlaCvrs.redaction().groups().size
            // TODO nredactedCvrs:  number of redacted cvrs given in CVR file
            val nredactedCvrs = corlaCvrs.redaction().nredactedCvrs()

            // data class CountyInputData(val county: String, val manifestCount: Int, val ncvrs: Int, val cvrNoManifest: Int, val nredactedCvrs: Int, val ngroups: Int, val minCards: Int)
            data.add(CountyInputData(county,
                manifestCounts.totalEntries,
                manifestCounts.countCvrsInManifest,
                manifestCounts.cvrNoManifest,
                nredactedCvrs,
                ngroups,
                ccc.minCards))
            println("wrote $county  ${data.last()}")
        }
        writeCountyInputData(filename, data)

        println("totalManifest ${data.sumOf { it.manifestCount }}")
        println("totalNcvrs ${data.sumOf { it.ncvrs }}")
        println("totalMissing ${data.sumOf { it.manifestCount - it.ncvrs }}")
        println("totalRedactedCvrs ${data.sumOf { it.nredactedCvrs }}")
        println("totalNgroups ${data.sumOf { it.ngroups }}")

        val roundtrip = readCountyInputData(filename)
        assertEquals(data.sortedBy{it.county}, roundtrip)
    }
}
