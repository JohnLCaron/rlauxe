package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.persist.Publisher
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO mo betta
class TestReadCardPoolFromCvrsJson {

    @Test
    fun testCardPoolFromCvrs() {
        // val auditDir = "/home/stormy/rla/persist/testCliRoundOneAudit/audit"
        val auditDir = "src/test/data/workflow/testCliRoundOneAudit/audit"

        val publisher = Publisher(auditDir)
        val poolFile = publisher.cardPoolsFile()
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(poolFile, infos).unwrap()
        println("read ${pools} pool (original)")
        val pool = pools.first()
        assertTrue(pool is CardPoolFromCvrs)
        assertEquals(1, pool.poolId)
        assertEquals("noCvr", pool.poolName)

        val tab = pool.contestTabs.values.first()
        val votes = tab.votes
        assertEquals(mapOf(0 to 250, 1 to 245), votes)
        assertEquals(500, tab.ncards)
        assertEquals(5, tab.undervotes)
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