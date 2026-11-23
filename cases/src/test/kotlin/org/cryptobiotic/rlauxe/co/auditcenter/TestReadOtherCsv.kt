package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.test.Test

class TestReadOtherCsv {

    @Test
    fun testReadCanonicalList() {
        val filename = "$auditcenter/2026/general/files/2026GeneralCanonicalList.csv"
        val canons = CanonicalList.readFromFile(filename)
        println()
        println(filename)
        canons.forEach { println(it) }
    }

    @Test
    fun testReadTabulateCounty() {
        val filename = "$auditcenter/2026/general/files/tabulateCountyPlurality.csv"
        val tabs = TabulateCounty.readFromFile(filename, Statewide2026General())
        println()
        println(filename)
        tabs.forEach { println(it) }
    }

    @Test
    fun testReadCountyManifest() {
        val filename = "$auditcenter/2026/general/files/EagleManifest.csv"
        val batches = ManifestBatch.readFromFile(filename)
        println()
        println(filename)
        batches.forEach { println(it) }
    }

}