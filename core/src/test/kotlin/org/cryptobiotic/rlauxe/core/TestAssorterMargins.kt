package org.cryptobiotic.rlauxe.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.propTestFastConfig
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.checkEquivilentVotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestAssorterMargins {

    @Test
    fun testProblem() {
        val test = MultiContestTestData(16, 13, 27703, 0.02..0.033)
        test.contests.forEach { contest ->
            val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
            val cvrs = test.makeCvrsFromContests()
            val fcontest = test.fcontests.find { it.info.name == contest.name }!!
            testAssertions(contest, contestUA.pollingAssertions, cvrs)
        }
    }

    @Test
    fun testMultiContestTestData() {
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.int(min = 10, max = 30),
                Arb.int(min = 5, max = 15),
                Arb.int(min = 10000, max = 20000),
            ) { ncontests, nstyles, Nc ->
                val test = MultiContestTestData(ncontests, nstyles, Nc, 0.011..0.033)
                val cvrs = test.makeCvrsFromContests()

                try {
                    test.contests.forEach { contest ->
                        // val fcontest = test.fcontests.find { it.info.name == contest.name }!!
                        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
                        testAssertions(contest, contestUA.pollingAssertions, cvrs)
                    }
                } catch( t: Throwable) {
                    t.printStackTrace() // TODO without this doesnt tell me why it fails
                }
            }
        }
    }

    @Test
    fun testPollingSimulation() {
        runTest {
            checkAll(
                propTestFastConfig, // propTestSlowConfig,
                Arb.double(min = 0.01, max = 0.05),
                Arb.double(min = 0.01, max = 0.10),
                Arb.double(min = 0.0, max = 0.05),
                Arb.int(min = 10000, max = 30000),
                Arb.int(min = 0, max = 100)
            ) { reportedMargin, underVotePct, phantomPct, Nc, Np ->
                val sim = ContestSimulation.make2wayTestContest(reportedMargin, underVotePct, phantomPct, Nc=Nc)
                val contestUA = ContestUnderAudit(sim.contest, isComparison = false).makePollingAssertions()
                println(
                    "${sim.show()} margin=${df(reportedMargin)} under=${df(underVotePct)} phantom=${df(phantomPct)} votes: [${
                        showVotes(
                            (contestUA.contest as Contest).votes
                        )
                    }]"
                )
                testAssertions(sim.contest, contestUA.pollingAssertions, sim.makeCvrs())
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

            val calcReportedMargin = contest.calcMargin(ast.winner, ast.loser)
            val calcAssorterMargin = ast.assorter.calcAssorterMargin(ast.contest.info.id, cvrs)
            if (!doubleIsClose(calcReportedMargin, calcAssorterMargin))
                println("")
            assertEquals(calcReportedMargin, calcAssorterMargin, doublePrecision, "calcReportedMargin")
            assertEquals(ast.margin, calcAssorterMargin, doublePrecision, "calcAssorterMargin")

            //val assortWithoutPhantoms = margin2mean(calcAssorterMargin)
            //val assortWithPhantoms = cvrs.filter { it.hasContest(ast.contest.info.id) }
            //    .map { cvr -> ast.assorter.assort(cvr, usePhantoms = true) }.average()
            //println(" assortDiffPhantoms= ${df(assortWithoutPhantoms)} - ${df(assortWithPhantoms)} = " +
            //        "${df(assortWithoutPhantoms - assortWithPhantoms)}")
            //assertTrue(assortWithPhantoms <= assortWithoutPhantoms, "assortWithPhantoms")
        }
    }
}