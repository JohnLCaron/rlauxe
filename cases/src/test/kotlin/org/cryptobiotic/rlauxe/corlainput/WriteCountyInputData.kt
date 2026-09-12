package org.cryptobiotic.rlauxe.corlainput

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
            val corlaCvrs = countyInput.readCorlaCvrs()
            val ncvrs = corlaCvrs.cvrs().size
            val ngroups = corlaCvrs.ngroups()
            val nrows = corlaCvrs.nrows()
            val nredactedCvrs = corlaCvrs.redactedCvrs().size
            // data class CountyInputData(val county: String, val cvrRows: Int, val ncvrs: Int, val nredactedCvrs: Int, val ngroups: Int,)
            data.add(CountyInputData(county, nrows, ncvrs, nredactedCvrs, ngroups))
            println("did $county == ${data.last()}")
        }
        writeCountyInputData(filename, data)

        val roundtrip = readCountyInputData(filename)
        roundtrip.forEach {
            println(it)
        }
        assertEquals(data.sortedBy{it.county}, roundtrip)
    }
}