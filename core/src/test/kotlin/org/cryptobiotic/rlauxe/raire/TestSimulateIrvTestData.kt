package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class TestSimulateIrvTestData {
    val N = 50000
    val marginRange = 0.01..0.50
    val undervotePct = 0.0
    val Np = 10

    val target: RaireContestWithAssertions
    val targetContest: RaireContest
    val cvrs: List<Cvr>
    val infos: Map<Int, ContestInfo>

    init {
        val minMargin = marginRange.start + Random.nextDouble(marginRange.endInclusive - marginRange.start)

        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV
        )
        // data class RaireContest(
        //    val info: ContestInfo,
        //    val winners: List<Int>, // actually only one winner is allowed
        //    val Nc: Int,
        //    val Ncast: Int,
        //    val undervotes: Int,
        //)
        //)
        val raireContest = RaireContest(info, winners=listOf(42), N, N-Np, (undervotePct * N).toInt())

        // data class SimulateIrvTestData(
        //    val contest: RaireContest,
        //    val minMargin: Double,
        //    val sampleLimits: Int?,
        //    val excessVotes: Int? = null,
        //    val quiet: Boolean = true
        //)
        val sim = SimulateIrvTestData(raireContest, minMargin, sampleLimits=null, quiet=false )
        assertEquals("SimulateIrvTestData(0} phantoms=$Np ncards=$N", sim.toString())

        cvrs = sim.makeCvrs()
        infos = mapOf(raireContest.id to raireContest.info())
        val tabs = tabulateCvrs(cvrs.iterator(), infos)
        val contestTab = tabs[raireContest.id]
        assertNotNull(contestTab)

        //println(contestTab.irvVotes)
        println("nvotes = ${contestTab.irvVotes.nvotes()}")
        val nvotes = contestTab.irvVotes.nvotes()
        target = makeRaireContest(info, contestTab, N, N)
        targetContest = target.contest as RaireContest
    }

    @Test
    fun testBasics() {
        assertEquals(N, target.Nc)
        assertEquals(N, cvrs.size)

        assertEquals(Np, target.Nphantoms)
        val np = cvrs.count { it.phantom }
        assertEquals(Np, np)

        target.rassertions.forEach { println("  $it marginPct=${it.marginInVotes / N.toDouble()}") }

        val minAssertion = target.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).values.first()
        val irvVotes = cvrTab.irvVotes.makeVotes(target.ncandidates)
        println("rassorter calcMargin = ${rassorter.calcMargin(irvVotes, N)}")
    }
}