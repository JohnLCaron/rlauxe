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

class TestContestSimulation {

    @Test
    fun testContestSimulationwWithoutPhantoms() {
        val reportedMargin = .005
        val sim = ContestSimulation.make2wayTestContest(reportedMargin, 0.10, 0.0, 10000)
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
        val reportedMargin = .005
        val pctPhantoms = .01
        val sim = ContestSimulation.make2wayTestContest(reportedMargin, 0.10, pctPhantoms, 10000)
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
