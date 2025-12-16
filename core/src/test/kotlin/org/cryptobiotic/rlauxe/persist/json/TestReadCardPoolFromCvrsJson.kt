package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.workflow.readPopulations
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO mo betta
class TestReadCardPoolFromCvrsJson {

    @Test
    fun testPoolsFromCvrs() {
        // val auditDir = "$testdataDir/persist/testCliRoundOneAudit/audit"
        val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"

        val publisher = Publisher(auditdir)
        val poolFile = publisher.cardPoolsFile()
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readPopulations(publisher)!!
        println("read ${pools} pool (original)")
        val pool = pools.first()
        assertTrue(pool is CardPoolFromCvrs)
        assertEquals(42, pool.poolId) // bogus
        assertEquals("pool42", pool.poolName)

        val tab = pool.contestTabs.values.first()
        val votes = tab.votes
        assertEquals(mapOf(0 to 1288, 1 to 1187), votes)
        assertEquals(2500, tab.ncards)
        assertEquals(25, tab.undervotes)
        assertEquals(1, tab.info.voteForN)
        assertEquals(false, tab.isIrv)

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

    // remove
    @Test
    fun testCardPoolFromCvrs() {
        // val auditDir = "$testdataDir/persist/testCliRoundOneAudit/audit"
        val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"

        val publisher = Publisher(auditdir)
        val poolFile = publisher.cardPoolsFile()
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(poolFile, infos).unwrap()
        println("read ${pools} pool (original)")
        val pool = pools.first()
        assertTrue(pool is CardPoolFromCvrs)
        assertEquals(42, pool.poolId) // bogus
        assertEquals("pool42", pool.poolName)

        val tab = pool.contestTabs.values.first()
        val votes = tab.votes
        assertEquals(mapOf(0 to 1288, 1 to 1187), votes)
        assertEquals(2500, tab.ncards)
        assertEquals(25, tab.undervotes)
        assertEquals(1, tab.info.voteForN)
        assertEquals(false, tab.isIrv)

        val scratchFile = createTempFile().toFile()
        writeCardPoolsJsonFile(pools, scratchFile.toString())

        val roundtrip = readCardPoolsJsonFile(scratchFile.toString(), infos).unwrap()
        println("read ${roundtrip} pool (roundtrip)")
        assertEquals(pools.toSet(), roundtrip.toSet())
        val rpool = roundtrip.first()
        assertEquals(pool, rpool)
        assertEquals(pool.hashCode(), rpool.hashCode())

        scratchFile.delete()
    }

}