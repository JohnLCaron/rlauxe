package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.readFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadBallotPoolCsv {

    @Test
    fun testRoundtrip() {
        val topDir = "/home/stormy/rla/cases/sf2024Poa"
        val poolFile = "$topDir/ballotPools.csv"
        val poolFileOut = "/home/stormy/rla/tests/scratch/ballotPoolsTest.csv"

        val pools = readBallotPoolCsvFile(poolFile)
        println("read ${pools.size} pools (original")

        writeBallotPoolCsvFile(pools, poolFileOut)

        val roundtrip = readBallotPoolCsvFile(poolFileOut)
        println("read ${roundtrip.size} pools (roundtrip")
        assertEquals(pools.toSet(), roundtrip.toSet())
    }

    @Test
    fun testMakeCardPoolsFromAuditRecord() {
        val topDir = "/home/stormy/rla/cases/boulder24oa/audit"
        val auditRecord = readFrom(topDir)

        val cardPools = makeCardPoolsFromAuditRecord(auditRecord)
        println(cardPools.showPoolVotes())
    }

}