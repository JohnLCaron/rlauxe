package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.readBatches
import org.cryptobiotic.rlauxe.workflow.readCardPools
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO mo betta
class TestReadPopulations {

    @Test
    fun testReadPollingBatches() {
        val auditdir = "$testdataDir/persist/testRunCli/polling/audit"

        val publisher = Publisher(auditdir)
        val pops = readBatches(publisher)!!
        println("read ${pops} batch (original)")
        val pool = pops.first()
        assertEquals(0, pool.id) // bogus
        assertEquals("style0", pool.name)

        val scratchFile = createTempFile().toFile()
        writeCardStylesJsonFile(pops, scratchFile.toString())

        val roundtrip = readCardStylesJsonFileUnwrapped(scratchFile.toString())
        println("read ${roundtrip} batch (roundtrip)")
        assertEquals(pops.toSet(), roundtrip.toSet())
        val rpool = roundtrip.first()
        assertEquals(pool, rpool)
        assertEquals(pool.hashCode(), rpool.hashCode())

        scratchFile.delete()
    }

    @Test
    fun testReadOneAuditPools() {
        val auditdir = "$testdataDir/persist/testRunCli/oneaudit/audit"
        // val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"

        val publisher = Publisher(auditdir)
        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val infos = contests.map { it.contest.info() }.associateBy { it.id }
        val cardPools = readCardPools(publisher, infos)!!

        println("read ${cardPools.size} cardPools (original)")
        val pool = cardPools.first()
        assertEquals(42, pool.poolId) // bogus
        assertEquals("pool42", pool.poolName)

        val scratchFile = createTempFile().toFile()
        writeCardPoolCsvFile(cardPools, scratchFile.toString())

        val roundtrip = readCardPoolCsvFile(scratchFile.toString(), infos)
        println("read ${roundtrip} pool (roundtrip)")
        assertEquals(cardPools.toSet(), roundtrip.toSet())
        val rpool = roundtrip.first()
        assertEquals(pool, rpool)
        assertEquals(pool.hashCode(), rpool.hashCode())

        scratchFile.delete()
    }

}