package org.cryptobiotic.rlauxe.dominion

import kotlin.test.Test
import kotlin.test.assertEquals

class TestCvrExportRedaction {
    val show = false

    @Test
    fun testBoulder20() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv"
        val export: DominionCvrExport = readDominionCvrExportCsv(filename, "Boulder")
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
    }

    @Test
    fun testBoulder22Primary() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv"
        val export: DominionCvrExport = readDominionCvrExportCsv(filename, "Boulder")
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
    }
}