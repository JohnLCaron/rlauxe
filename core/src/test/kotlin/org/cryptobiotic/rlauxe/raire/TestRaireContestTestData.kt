package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRaireContestTestData {
    val N = 50000
    val ncontests = 40
    val nbs = 11
    val marginRange = 0.01..0.04
    val underVotePct = 0.234..0.345
    val phantomRange = 0.001..0.01

    val rcontestUA: RaireContestUnderAudit
    val rcontest: RaireContest
    val cvrs: List<Cvr>

    init {
        val minMargin = marginRange.start + Random.nextDouble(marginRange.endInclusive - marginRange.start)
        val phantomPct = phantomRange.start + Random.nextDouble(phantomRange.endInclusive - phantomRange.start)
        println("minMargin = $minMargin, phantomPct=${phantomPct}")

        val makeRaireContestResult = simulateRaireTestContest(N=N, contestId=111, ncands=4, minMargin=minMargin, phantomPct=phantomPct, quiet=false)
        rcontestUA = makeRaireContestResult.first
        cvrs = makeRaireContestResult.second
        rcontestUA.addClcaAssertionsFromReportedMargin()
        rcontest = rcontestUA.contest as RaireContest
    }

    @Test
    fun testBasics() {
        assertEquals(N, rcontestUA.Nc)
        assertEquals(N, cvrs.size)

        val np = cvrs.count { it.phantom }
        assertEquals(rcontestUA.Np, np)

        assertEquals(0, rcontestUA.winner)
        assertEquals(listOf(0), rcontest.winners)
        assertEquals(listOf("cand0"), rcontest.winnerNames)

        rcontestUA.rassertions.forEach { println("  $it marginPct=${it.marginInVotes/N.toDouble()}") }
    }

    @Test
    fun testCvrs() {
        assertEquals(N, rcontestUA.Nc)
        assertEquals(N, cvrs.size)

        val np = cvrs.count { it.phantom }
        assertEquals(np, rcontestUA.Np)

        assertEquals(0, rcontestUA.winner)
        rcontestUA.rassertions.forEach { println("  $it marginPct=${it.marginInVotes/N.toDouble()}") }
    }
}