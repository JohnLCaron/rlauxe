package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestReadCardPoolJson {

    @Test
    fun testCardPoolWithBallotStyle() {
        val auditdir = "/home/stormy/rla/cases/boulder24/oa/audit"

        val publisher = Publisher(auditdir)
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
        println("read ${pools.size} pools (original) from $auditdir")
        val pool = pools.first()
        assertTrue(pool is CardPoolWithBallotStyle)
        assertTrue(pool.toString().startsWith("CardPoolWithBallotStyle"))
        val tab = pool.voteTotals.values.first()
        assertEquals(1, tab.info.voteForN)
        assertEquals(false, tab.isIrv)

        val scratchFile = createTempFile().toFile()
        writeCardPoolsJsonFile(pools, scratchFile.toString())

        val rpools = readCardPoolsJsonFile(scratchFile.toString(), infos).unwrap()
        println("read ${rpools.size} pools (roundtrip)")
        assertEquals(pools.toSet(), rpools.toSet())
        val rpoolsMap = rpools.associateBy { it.poolName }
        pools.forEach { pool ->
            val rpool = rpoolsMap[pool.poolName]
            assertNotNull(rpool)
            assertTrue(rpool is CardPoolWithBallotStyle)
            assertEquals(pool, rpool)
            assertEquals(pool.hashCode(), rpool.hashCode())
        }

        scratchFile.delete()
    }

    @Test
    fun testCardPoolFromCvrs() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"

        val publisher = Publisher(auditdir)
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
        println("read ${pools.size} pools (original) from $auditdir")
        val pool = pools.first()
        assertTrue(pool is CardPoolFromCvrs)
        assertTrue(pool.toString().startsWith("CardPoolFromCvrs"))
        val tab = pool.contestTabs.values.first()
        assertEquals(1, tab.info.voteForN)
        assertEquals(false, tab.isIrv)

        val scratchFile = createTempFile().toFile()
        writeCardPoolsJsonFile(pools, scratchFile.toString())

        val rpools = readCardPoolsJsonFile(scratchFile.toString(), infos).unwrap()
        println("read ${rpools.size} pools (roundtrip)")
        assertEquals(pools.toSet(), rpools.toSet())
        val rpoolsMap = rpools.associateBy { it.poolName }
        pools.forEach { pool ->
            val rpool = rpoolsMap[pool.poolName]
            assertNotNull(rpool)
            assertTrue(rpool is CardPoolFromCvrs)
            assertEquals(pool, rpool)
            assertEquals(pool.hashCode(), rpool.hashCode())
        }

        scratchFile.delete()
    }

    @Test
    fun testCardPoolFromCvrsNS() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oans/audit"

        val publisher = Publisher(auditdir)
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
        println("read ${pools.size} pools (original) from $auditdir")
        val pool = pools.first()
        assertTrue(pool is CardPoolFromCvrs)
        val tab = pool.contestTabs.values.first()
        assertEquals(1, tab.info.voteForN)
        assertEquals(false, tab.isIrv)

        val scratchFile = createTempFile().toFile()
        writeCardPoolsJsonFile(pools, scratchFile.toString())

        val rpools = readCardPoolsJsonFile(scratchFile.toString(), infos).unwrap()
        println("read ${rpools.size} pools (roundtrip)")
        assertEquals(pools.toSet(), rpools.toSet())
        val rpoolsMap = rpools.associateBy { it.poolName }
        pools.forEach { pool ->
            val rpool = rpoolsMap[pool.poolName]
            assertNotNull(rpool)
            assertTrue(rpool is CardPoolFromCvrs)
            assertEquals(pool, rpool)
            assertEquals(pool.hashCode(), rpool.hashCode())
        }

        scratchFile.delete()
    }

    @Test
    fun compareContestTabs() {
        val oa = readContestTabs("/home/stormy/rla/cases/sf2024/oa/audit")
        val clca = readContestTabs("/home/stormy/rla/cases/sf2024/clca/audit")
        println("  oa[18] = ${oa[18]}")
        println("clca[18] = ${clca[18]}")
        println("clca[18] == oa18: ${clca[18] == oa[18]}")
    }

    fun readContestTabs(auditDir: String): Map<Int, ContestTabulation> {
        val publisher = Publisher(auditDir)
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info() }.associateBy { it.id }

        val scardIter = readCardsCsvIterator(publisher.sortedCardsFile())
        return tabulateAuditableCards(scardIter, infos)
    }

}