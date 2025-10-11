package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadBallotPoolCsv {

    @Test
    fun testRoundtrip() {
        val poolFile = "src/test/data/ballotPools.csv"
        val scratchFile = kotlin.io.path.createTempFile().toFile()

        val pools = readBallotPoolCsvFile(poolFile)
        println("read ${pools.size} pools (original")

        writeBallotPoolCsvFile(pools, scratchFile.toString())

        val roundtrip = readBallotPoolCsvFile(scratchFile.toString())
        println("read ${roundtrip.size} pools (roundtrip")
        assertEquals(pools.toSet(), roundtrip.toSet())

        scratchFile.delete()
    }


}