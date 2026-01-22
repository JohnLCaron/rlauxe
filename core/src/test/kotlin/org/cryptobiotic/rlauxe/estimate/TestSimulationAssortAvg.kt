package org.cryptobiotic.rlauxe.estimate

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.showVotes
import org.cryptobiotic.rlauxe.propTestFastConfig
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestSimulationAssortAvg {

    @Test
    fun testProblem() {
        //repeat(100) {
            val test = MultiContestTestData(16, 13, 27703, 0.02..0.033)
            test.contests.forEach { contest ->
                val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()
                val cvrs = test.makeCvrsFromContests()
                assertNotNull(test.contestTestBuilders.find { it.info.name == contest.name })
                testAssertions(contest, contestUA.assertions, cvrs)
            }
        //}
    }

    @Test
    fun testMultiContestTestData() {
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.Companion.int(min = 3, max = 6),
                Arb.Companion.int(min = 2, max = 4),
                Arb.Companion.int(min = 10000, max = 20000),
            ) { ncontests, nstyles, Nc ->
                val test = MultiContestTestData(ncontests, nstyles, Nc, 0.011..0.033)
                val cvrs = test.makeCvrsFromContests()
                println("$ncontests, $nstyles, $Nc")

                try {
                    test.contests.forEach { contest ->
                        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()
                        testAssertions(contest, contestUA.assertions, cvrs)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace() // TODO without this doesnt tell me why it fails
                }
            }
        }
    }

    @Test
    fun testContestSimulation() {
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.Companion.double(min = 0.01, max = 0.05),
                Arb.Companion.double(min = 0.01, max = 0.10),
                Arb.Companion.double(min = 0.0, max = 0.05),
                Arb.Companion.int(min = 10000, max = 30000),
                Arb.Companion.int(min = 0, max = 100)
            ) { reportedMargin, underVotePct, phantomPct, Nc, Np ->
                val sim = ContestSimulation.make2wayTestContest(
                    Nc,
                    reportedMargin,
                    undervotePct = underVotePct,
                    phantomPct = phantomPct
                )
                // val sim = ContestSimulation.make2wayTestContestOld(reportedMargin, underVotePct, phantomPct, Nc=Nc)
                val contestUA = ContestWithAssertions(sim.contest, isClca = false).addStandardAssertions()
                println(
                    "${sim.show()} margin=${df(reportedMargin)} under=${df(underVotePct)} phantom=${df(phantomPct)} votes: [${
                        showVotes(
                            (contestUA.contest as Contest).votes
                        )
                    }]"
                )
                testAssertions(sim.contest, contestUA.assertions, sim.makeCvrs())
            }
        }
    }

    fun testAssertions(contest: Contest, assertions: List<Assertion>, cvrs: List<Cvr>) {
        assertions.forEach { ast ->
            val ncvrs = cvrs.filter { it.hasContest(contest.id) }.count()
            assertTrue(ncvrs == contest.Nc)
            val votem = mutableMapOf<Int, Int>()
            cvrs.filter { it.hasContest(contest.id) }.forEach {
                val cvotes: IntArray = it.votes[contest.id]!!
                cvotes.forEach { vote -> votem.merge(vote, 1) { a, b -> a + b } }
            }
            assertTrue(checkEquivilentVotes(contest.votes, votem))

            val calcReportedMargin = contest.reportedMargin(ast.winner, ast.loser)
            val calcAssorterMargin = ast.assorter.calcAssorterMargin(ast.info.id, cvrs)
            assertEquals(calcReportedMargin, calcAssorterMargin, doublePrecision, "calcReportedMargin")
            assertEquals(ast.assorter.dilutedMargin(), calcAssorterMargin, doublePrecision, "calcAssorterMargin")

            val assortWithoutPhantoms = margin2mean(calcAssorterMargin)
            val assortWithPhantoms = cvrs.filter { it.hasContest(ast.info.id) }
                .map { cvr -> ast.assorter.assort(cvr, usePhantoms = true) }.average()
            println(" assortDiffPhantoms= ${df(assortWithoutPhantoms)} - ${df(assortWithPhantoms)} = " +
                    df(assortWithoutPhantoms - assortWithPhantoms)
            )
            // TODO assert something
            //assertTrue(assortWithPhantoms <= assortWithoutPhantoms, "assortWithPhantoms")
        }
    }
}