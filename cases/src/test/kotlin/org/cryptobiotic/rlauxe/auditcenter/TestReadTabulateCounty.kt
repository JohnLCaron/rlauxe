package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corlaInput.Statewide2026Primary
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReadTabulateCounty {
    val input: StatewideInput = Statewide2026Primary()

    @Test
    fun readTabulateCounties() {
        val countyTabs: Map<String, CountyTabAllContests> = readCountyTabulateCsv(input.tabulateCountyFile)
        val countyTabs2: Map<String, CountyTabAllContests> = input.countyTabsAllContests()
        countyTabs.forEach {
            val tab2 = countyTabs2[it.key]
            assertEquals(it.value, tab2)
            println(it.value)
        }
    }

    @Test
    fun contestTabAllCounties() {
        val contestTabAllCounties: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        contestTabAllCounties.forEach {
            assertTrue(!input.skipCounties(it.key))
            println(it)
        }
    }
}