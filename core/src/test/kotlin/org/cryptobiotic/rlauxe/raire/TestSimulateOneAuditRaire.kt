package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSimulateOneAuditRaire {
    val N = 50000
    val ncontests = 40
    val marginRange = 0.01..0.20
    val underVotePct = 0.04
    val phantomPct = 0.0

    val rcontestUA: RaireContestWithAssertions
    val rcontest: RaireContest
    val cvrs: List<Cvr>
    val infos: Map<Int, ContestInfo>
    val pools: List<OneAuditPoolFromCvrs>

    init {
        val minMargin = marginRange.start + Random.nextDouble(marginRange.endInclusive - marginRange.start)
        val makeRaireContestResult = simulateOneAuditRaire(
            N = N, contestId = 111, ncands = 8, minMargin = minMargin, phantomPct = phantomPct,
            poolPct = 11, quiet = true
        )
        rcontestUA = makeRaireContestResult.first
        cvrs = makeRaireContestResult.second
        pools = makeRaireContestResult.third
        rcontest = rcontestUA.contest as RaireContest
        infos = mapOf(rcontest.id to rcontest.info())
    }

    @Test
    fun testBasics() {
        assertEquals(N, rcontestUA.Nc)
        assertEquals(N, cvrs.size)

        val np = cvrs.count { it.phantom }
        assertEquals(rcontestUA.Nphantoms, np)

        assertEquals(listOf(0), rcontest.winners)
        assertEquals(listOf("cand0"), rcontest.winnerNames)

        rcontestUA.rassertions.forEach { println("  $it marginPct=${it.marginInVotes / N.toDouble()}") }
    }

    @Test
    fun testCvrs() {
        assertEquals(N, rcontestUA.Nc)
        assertEquals(N, cvrs.size)

        val np = cvrs.count { it.phantom }
        assertEquals(np, rcontestUA.Nphantoms)

        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).values.first()
        println(cvrTab)

        pools.forEach { println(it) }

        var countPool = 0
        var countCvr = 0
        cvrs.forEach { cvr ->
            if (cvr.hasContest(rcontestUA.id)) {
                if (cvr.poolId != null) countPool++ else countCvr++
            }
        }

        val pool = pools.first()
        assertEquals(pool.ncards(), countPool)
    }

    @Test
    fun testMargins() {
        rcontestUA.rassertions.forEach { println("  $it marginPct=${it.marginInVotes / N.toDouble()}") }

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).values.first()
        val irvVotes = cvrTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin = ${rassorter.calcMargin(irvVotes, N)}")
    }

    @Test
    fun testPoolMargins() {
        rcontestUA.rassertions.forEach { println("  $it marginPct=${it.marginInVotes / N.toDouble()}") }

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as ClcaAssorterOneAudit
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).values.first()
        val irvVotes = cvrTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin = ${rassorter.calcMargin(irvVotes, N)}")

        // cvrs ignore pools
        val avgIgnorePool = AssortAvg()
        cvrs.forEach { cvr ->
            if (cvr.hasContest(rcontestUA.id)) {
               val assortVal = rassorter.assort(cvr, usePhantoms = false)
                avgIgnorePool.totalAssort += assortVal
                avgIgnorePool.ncards++
            }
        }
        println(avgIgnorePool)
        println("avgIgnorePool.margin = ${avgIgnorePool.margin()}")

        // use pool Avg
        val avgWithPool = AssortAvg()
        cvrs.forEach { cvr ->
            if (cvr.hasContest(rcontestUA.id)) {
                val assortVal = if (cvr.poolId != null)
                    cassorter.poolAverages.assortAverage[cvr.poolId]!!
                else
                    rassorter.assort(cvr, usePhantoms = false)
                avgWithPool.totalAssort += assortVal
                avgWithPool.ncards++
            }
        }
        println(avgWithPool)
        println("assortAvg.margin = ${avgWithPool.margin()}")

        assertEquals(cassorter.dilutedMargin, rassorter.dilutedMargin(), doublePrecision)
        assertEquals(cassorter.dilutedMargin, rassorter.calcMargin(irvVotes, N), doublePrecision)
        assertEquals(cassorter.dilutedMargin, avgIgnorePool.margin(), doublePrecision)
        assertEquals(cassorter.dilutedMargin, avgWithPool.margin(), doublePrecision)

        // pool margin
        println("cassorter.poolAverages.assortAverage = ${cassorter.poolAverages.assortAverage[42]}")
        val cassorterPoolMargin = mean2margin(cassorter.poolAverages.assortAverage[42]!!)
        println("cassorter.poolAverages.assortMargin = ${cassorterPoolMargin}")

        val pool = pools.first()
        val poolIrvVotes = pool.contestTabs[rcontestUA.id]!!.irvVotes.makeVotes(rcontestUA.ncandidates)
        val poolIrvMargin = rassorter.calcMargin(poolIrvVotes, pool.ncards())
        println("rassorter pool calcMargin = ${poolIrvMargin}")

        assertEquals(cassorterPoolMargin, poolIrvMargin, doublePrecision)

    }
}
