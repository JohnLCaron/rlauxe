package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.readPopulations
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO mo betta
class TestReadPopulations {

    @Test
    fun testReadOneAuditPools() {
        val auditdir = "$testdataDir/persist/testRunCli/oneaudit/audit"
        // val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"

        val publisher = Publisher(auditdir)
        val pools = readPopulations(publisher)!!
        println("read ${pools} pool (original)")
        val pool = pools.first()
        assertTrue(pool is OneAuditPool)
        assertEquals(42, pool.poolId) // bogus
        assertEquals("pool42", pool.poolName)

        val scratchFile = createTempFile().toFile()
        writePopulationsJsonFile(pools, scratchFile.toString())

        val roundtrip = readPopulationsJsonFileUnwrapped(scratchFile.toString())
        println("read ${roundtrip} pool (roundtrip)")
        assertEquals(pools.toSet(), roundtrip.toSet())
        val rpool = roundtrip.first()
        assertEquals(pool, rpool)
        assertEquals(pool.hashCode(), rpool.hashCode())

        scratchFile.delete()
    }

}