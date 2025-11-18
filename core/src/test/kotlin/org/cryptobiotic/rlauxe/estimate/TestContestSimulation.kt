package org.cryptobiotic.rlauxe.estimate

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.propTestFastConfig
import org.cryptobiotic.rlauxe.util.Welford
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
    fun testContestSimulationWithoutPhantoms() {
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
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.int(min = 10, max = 30),
                Arb.double(min = 0.005, max = 0.05),
                Arb.double(min = 0.01, max = 0.05),
            ) { ncontests, reportedMargin, pctPhantoms ->
                val sim = ContestSimulation.make2wayTestContest(Nc, reportedMargin, undervotePct = 0.10, phantomPct = pctPhantoms)
                val contest = sim.contest
                val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
                val cvrs = sim.makeCvrs() // phantoms have been added
                assertEquals(contest.Nc, cvrs.size)

                val calcMargin = assorter.calcAssorterMargin(contest.id, cvrs)
                val Ncd = contest.Nc.toDouble()
                val expectWithPhantoms = (margin2mean(calcMargin) * Ncd - 0.5 * sim.phantomCount) / Ncd
                val assortWithPhantoms = cvrs.map { cvr -> assorter.assort(cvr, usePhantoms = true) }.average()
                assertEquals(expectWithPhantoms, assortWithPhantoms, doublePrecision)
                println("assorter= $assorter ncvrs = ${cvrs.size} assortWithPhantoms= $assortWithPhantoms")
            }
        }
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

    @Test // flakey floont
    fun compareEstimationSimulation() {
        //// ClcaSingleRoundAuditTaskGenerator
        val Nc = 10000
        val margin = .02
        val welford = Welford()
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.double(min = 0.001, max = 0.05),
                Arb.double(min = 0.001, max = 0.01),
                Arb.double(min = 0.01, max = 0.05),
            ) { mvrsFuzzPct, phantomPct, underVotePct ->

                val sim = ContestSimulation.make2wayTestContest(
                    Nc = Nc,
                    margin,
                    undervotePct = underVotePct,
                    phantomPct = phantomPct
                )
                var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
                val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
                val testPairs = testMvrs.zip(testCvrs)

                // speculative if this is really what happens
                val contest = sim.contest
                val contestUA = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
                val cassertion: ClcaAssertion = contestUA.minClcaAssertion().first!!
                val cassorter = cassertion.cassorter
                val orgMargin = cassorter.calcClcaAssorterMargin(testPairs)
                // println("testPairs calcAssorterMargin= ${cassorter.calcAssorterMargin(testPairs)}")
                val errorRates = ClcaErrorTable.getErrorRates(contest.ncandidates, mvrsFuzzPct)

                //// what were we doing before ??

                // first generate some test data
                // val simOrg = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
                // var testCvrsOrg = simOrg.makeCvrs() // includes undervotes and phantoms
                // var testMvrsOrg = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrsOrg, mvrsFuzzPct) // audit, not sim

                // then in the sim, use the actuals cvrs
                val samplerOrg = ClcaSimulatedErrorRates(testCvrs, contest, cassorter, errorRates)
                val pairsOrg = samplerOrg.mvrs.zip(samplerOrg.cvrs)
                val marginOrg = cassorter.calcClcaAssorterMargin(pairsOrg)

                //// what are we doing now

                // instead of using testCvrs, we generate the cvrs again
                val contestSimNow = ContestSimulation(contest, contest.Nc)
                val cvrsNow = contestSimNow.makeCvrs()
                val samplerNow = ClcaSimulatedErrorRates(cvrsNow, contest, cassorter, errorRates)
                val pairsNow = samplerNow.mvrs.zip(samplerNow.cvrs)
                val marginNow = cassorter.calcClcaAssorterMargin(pairsNow)

                //println("org margin= $marginOrg now margin= $marginNow")
                // println("orgMargin margin= $orgMargin - errorRates margin= $marginNow = ${orgMargin - marginNow} ")
                welford.update(orgMargin - marginNow)
                assertEquals(marginOrg, marginNow, doublePrecision)
            }
        }
        println("welford= $welford")
    }

}
