package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

//     When we have styles, we can calculate Nb_c = physical ballots for contest C
//    Let V_c = votes for contest C, V_c <= Nb_c <= N_c.
//    Let U_c = undervotes for contest C = Nb_c - V_c >= 0.
//    Let Np_c = nphantoms for contest C = N_c - Nb_c, and are added to the ballots before sampling or sample size estimation.
//    Then V_c + U_c + Np_c = N_c.

class TestPollingSimulation {

    @Test
    fun testPollingSimulation() {
        val test = makePS(0.05, 0.10, 0.0, 10000)
        val assorter = test.assorter
        val cvrs = test.makeCvrs() // phantoms have not been added
        val mean = cvrs.map{ assorter.assort(it) }.average()

        // TODO margin has increased to .05 -> .050505050505050
        // ncvrs = 9900 average= 0.5252525252525253, margin = 0.05050505050505061
        println("ncvrs = ${cvrs.size} average= $mean, margin = ${mean2margin(mean)}")
    }

    @Test
    fun testPollingSimulation2withoutPhantoms() {
        val reportedMargin = .005
        val sim = PollingSimulation2.make(reportedMargin, 0.10, 0.0, 10000)
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
    fun testPollingSimulation2withPhantoms() {
        val reportedMargin = .005
        val pctPhantoms = .01
        val sim = PollingSimulation2.make(reportedMargin, 0.10, pctPhantoms, 10000)
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
}

// reportedMargin = (winner - loser) / Nc
// reportedMargin * Nc = (winner - loser)
// Nc = winner + loser + under + phantom
// Nc - under - phantom = winner + loser
// votes = Nc - under - phantom = winner + loser
// reportedMargin * Nc + votes = 2 * winner
// reportedMargin * Nc - votes = -2 * loser

fun makePS(reportedMargin: Double, underVotePct: Double, phantomPct: Double, Nc: Int): PollingSimulation {
    val info = ContestInfo(
        name = "AvB",
        id = 0,
        choiceFunction = SocialChoiceFunction.PLURALITY,
        candidateNames = listToMap( "A", "B", "C"),
    )
    val underCount = (Nc * underVotePct).toInt()
    val phantomCount = (Nc * phantomPct).toInt()
    val voteCount = Nc - underCount - phantomCount
    println("underCount = $underCount phantomCount = $phantomCount voteCount = $voteCount")

    val winnerCount = ((reportedMargin * Nc + voteCount) / 2.0) .toInt()
    val loserCount = ((voteCount - reportedMargin * Nc) / 2.0) .toInt()
    val calcMargin = (winnerCount - loserCount) / Nc.toDouble()
    assertEquals(reportedMargin, calcMargin)

    val contest = Contest(info, mapOf(0 to winnerCount, 1 to loserCount, 2 to underCount), Nc=Nc)
    val assorter = PluralityAssorter.makeWithVotes(contest, winner=0, loser=1)
    println("assorter = $assorter")
    return PollingSimulation(contest, assorter)
}
