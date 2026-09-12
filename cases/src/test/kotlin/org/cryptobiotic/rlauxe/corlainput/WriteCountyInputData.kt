package org.cryptobiotic.rlauxe.corlainput

import org.cryptobiotic.rlauxe.corlaCounty.CheckCvrsAndManifest
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.CountyInputData
import org.cryptobiotic.rlauxe.corlaInput.readCountyInputData
import org.cryptobiotic.rlauxe.corlaInput.writeCountyInputData

import kotlin.test.Test
import kotlin.test.assertEquals

class WriteCountyInputData {

    @Test
    fun writeCountyData() {
        val filename = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/corla20/contestData.csv"
        val data = mutableListOf<CountyInputData>()
        val stateInput = Colorado2020General()
        stateInput.counties().forEach { county ->
            val countyInput = stateInput.corlaCountyInput(county)!!
            val ccc = CheckCvrsAndManifest(stateInput, countyInput, showMatch = true, showMissingVotes = true)
            val corlaCvrs = ccc.corlaCvrs
            val manifestCount = ccc.manifestCount
            val ncvrs = ccc.ncvrsInManifest
            val ngroups = corlaCvrs.ngroups()
            val nredactedCvrs = corlaCvrs.redactedCvrs().size + corlaCvrs.redactedGroups().sumOf{ it.ncards()}
            // data class CountyInputData(val county: String, val cvrRows: Int, val ncvrs: Int, val nredactedCvrs: Int, val ngroups: Int,)
            data.add(CountyInputData(county, manifestCount, ncvrs, nredactedCvrs, ngroups, ccc.minCards))
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