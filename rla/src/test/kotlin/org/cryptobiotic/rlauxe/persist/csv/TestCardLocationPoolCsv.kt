package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardLocationPoolCsv {

    @Test
    fun testRoundtrip() {
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        val poolFile = "$topDir/ballotPools.csv"
        val poolFileOut = "/home/stormy/temp/tests/scratch/ballotPoolsTest.csv"

        val pools = readBallotPoolCsvFile(poolFile)
        println("read ${pools.size} pools (original")

        writeBallotPoolCsvFile(pools, poolFileOut)

        val roundtrip = readBallotPoolCsvFile(poolFileOut)
        println("read ${roundtrip.size} pools (roundtrip")
        assertEquals(pools.toSet(), roundtrip.toSet())
    }

}