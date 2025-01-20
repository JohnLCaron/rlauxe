package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals

//     When we have styles, we can calculate Nb_c = physical ballots for contest C
//    Let V_c = votes for contest C, V_c <= Nb_c <= N_c.
//    Let U_c = undervotes for contest C = Nb_c - V_c >= 0.
//    Let Np_c = nphantoms for contest C = N_c - Nb_c, and are added to the ballots before sampling or sample size estimation.
//    Then V_c + U_c + Np_c = N_c.

class TestContestSimulation {

    @Test
    fun testContestSimulationwWithoutPhantoms() {
        val Nc = 10000
        val reportedMargin = .005
        val sim = ContestSimulation.make2wayTestContest(Nc, reportedMargin, undervotePct=0.10, phantomPct=0.0)
        val contest = sim.contest
        val assorter = PluralityAssorter.makeWithVotes(contest, winner=0, loser=1)
        val cvrs = sim.makeCvrs() // phantoms have been added
        assertEquals(contest.Nc, cvrs.size)

        val margin = assorter.calcAssorterMargin(contest.id, cvrs)
        println("assorter= $assorter ncvrs = ${cvrs.size} margin= $margin")

        // true when phantoms = 0
        assertEquals(reportedMargin, margin, doublePrecision)
    }

    @Test
    fun testContestSimulationWithPhantoms() {
        val Nc = 10000
        val reportedMargin = .005
        val pctPhantoms = .01
        val sim = ContestSimulation.make2wayTestContest(Nc, reportedMargin, undervotePct=0.10, phantomPct=pctPhantoms)
        val contest = sim.contest
        val assorter = PluralityAssorter.makeWithVotes(contest, winner=0, loser=1)
        val cvrs = sim.makeCvrs() // phantoms have been added
        assertEquals(contest.Nc, cvrs.size)
        val calcMargin = assorter.calcAssorterMargin(contest.id, cvrs)
        val Ncd = contest.Nc.toDouble()
        val expectWithPhantoms = (margin2mean(calcMargin) * Ncd - 0.5 * sim.phantomCount) / Ncd
        val assortWithPhantoms = cvrs.map { cvr -> assorter.assort(cvr, usePhantoms = true)}.average()
        assertEquals(expectWithPhantoms, assortWithPhantoms, doublePrecision)
        println("assorter= $assorter ncvrs = ${cvrs.size} assortWithPhantoms= $assortWithPhantoms")
    }

    @Test
    fun testContestTestData() {
        val Nc = 50000
        val margin = 0.04
        val underVotePct = 0.20
        val phantomPercent = .05
        //             fun make2wayContest(Nc: Int,
        //                            margin: Double, // margin of top highest vote getters, not counting undervotePct, phantomPct
        //                            undervotePct: Double, // needed to set Nc
        //                            phantomPct: Double): ContestSimulation {
        val fcontest = ContestSimulation.make2wayTestContest(Nc, margin, underVotePct, phantomPercent)
        val contest = fcontest.contest
        assertEquals(Nc, contest.Nc)

        val cvrs = fcontest.makeCvrs()
        assertEquals(contest.Nc, cvrs.size)

        val ncvr = cvrs.count { it.hasContest(contest.id) }
        assertEquals(contest.Nc, ncvr)
        //val nbs = ballots.count { it.hasContest(contest.id) }
        //assertEquals(contest.Nc, nbs)

        val nphantom = cvrs.count { it.hasContest(contest.id) && it.phantom }
        assertEquals(fcontest.phantomCount, nphantom)
        val phantomPct = nphantom/ Nc.toDouble()
        println("  nphantom=$nphantom pct= $phantomPct =~ ${phantomPercent} abs=${abs(phantomPct - phantomPercent)} " +
                " rel=${abs(phantomPct - phantomPercent) /phantomPct}")
        if (nphantom > 2) assertEquals(phantomPercent, phantomPct, .001)

        val nunder = cvrs.count { it.hasContest(contest.id) && !it.phantom && it.votes[contest.id]!!.isEmpty() }
        assertEquals(fcontest.underCount, nunder)
        val underPct = nunder/ Nc.toDouble()
        println("  nunder=$nunder == ${fcontest.underCount}; pct= $underPct =~ ${underVotePct} abs=${abs(underPct - underVotePct)} " +
                " rel=${abs(underPct - underVotePct) /underPct}")
        if (nunder > 2) assertEquals(underVotePct, underPct, .02)
    }

}
