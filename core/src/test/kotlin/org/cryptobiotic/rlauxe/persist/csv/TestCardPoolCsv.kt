package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.irv.RaireContestTestData
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.collections.forEach

class TestCardPoolCsv {

    @Test
    fun testRegVotes() {
        val infos = mutableMapOf<Int, ContestInfo>()
        val regPool = makeRegPool(infos)
        val irvPool= makeIrvPool(infos)

        val target = listOf(regPool, irvPool, regPool, irvPool, )

        val csvFile = "$testdataDir/tests/scratch/cardPoolCsvFile.csv"
        writeCardPoolCsvFile(target, csvFile)

        val roundtrip = readCardPoolCsvFile(csvFile,  infos)
        roundtrip.forEachIndexed { pidx, rpool ->
            val tpool = target[pidx]
            println(tpool)
            println(rpool)

            rpool.contestTabs.values.forEach{ rtab ->
                val ttab = tpool.contestTabs[rtab.contestId]
                assertEquals(ttab, rtab)
            }

            assertEquals(tpool, rpool)
            println("--------------------------------------------------")
        }

        assertEquals(target, roundtrip)
    }

    fun makeRegPool(infos: MutableMap<Int, ContestInfo>): CardPool {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 123, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            undervotes=undervotes, phantoms=phantoms, voteForNs = voteForNs)

        val myinfos = contests.associate { Pair(it.id, it.info) }
        val tabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), myinfos)
        infos.putAll(myinfos)

        // data class OneAuditPoolFromCvrs(
        //    override val poolName: String,
        //    override val poolId: Int,
        //    val hasSingleCardStyle: Boolean,
        //    val infos: Map<Int, ContestInfo>,
        //): OneAuditPoolIF {
        //
        //    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
        //    var totalCards = 0
        val pool = OneAuditPoolFromCvrs("testRegVotes", 1, true, infos)
        pool.totalCards = 42
        pool.contestTabs.putAll(tabs)
        return pool.toOneAuditPool()
    }

    fun makeIrvPool(infos: MutableMap<Int, ContestInfo>): CardPool {
        val N = 20000
        val minMargin = .05
        val undervotePct = 0.0
        val phantomPct = 0.0
        val testContest = RaireContestTestData(
            0,
            ncands = 4,
            ncards = N,
            minMargin = minMargin,
            undervotePct = undervotePct,
            phantomPct = phantomPct,
            excessVotes = 0,
        )
        val cvrs = testContest.makeCvrs()

        val myinfos = mapOf( 0 to testContest.info)
        infos.putAll(myinfos)

        val tabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        val pool = OneAuditPoolFromCvrs("testIrvVotes", 2, true, infos)
        pool.totalCards = 99
        pool.contestTabs.putAll(tabs)

        return pool.toOneAuditPool()
    }

}