package org.cryptobiotic.rlauxe.reader

import kotlin.test.*

class TestDominion {
    val dataDir = "src/test/data/Dominion/json/"

    @Test
    fun testReadDominionJsonFromFile() {
        readDominionJsonFromFile(dataDir + "CvrExport_0.json")
        readDominionJsonFromFile(dataDir + "CvrExport_1.json")
        readDominionJsonFromFile(dataDir + "test_5.10.50.85.Dominion.json")
    }

    @Test
    fun test_read_cvrs_old_format() {
        readDominionJsonFromFileOld(dataDir + "test_5.2.18.2.Dominion.json")
    }
}