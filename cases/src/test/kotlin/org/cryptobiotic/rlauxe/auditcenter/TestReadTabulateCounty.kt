package org.cryptobiotic.rlauxe.auditcenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReadTabulateCounty {
    val input: ColoradoInput = Colorado2020General()

    @Test
    fun readTabulateCounties() {
        val countyTabs: Map<String, CountyTabAllContests> = readCountyTabulateCsv(input.tabulateCountyFile)
        val countyTabs2: Map<String, CountyTabAllContests> = input.countyTabsAllContests
        countyTabs.forEach {
            val tab2 = countyTabs2[it.key]
            assertEquals(it.value, tab2)
        }
    }

    @Test
    fun contestTabAllCounties() {
        val contestTabAllCounties: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties
        contestTabAllCounties.forEach {
            assertTrue(it.key !in input.skipCounties)
        }
    }
}