package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestOneAudit {

    @Test
    fun testMakeContest() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, 0.0, undervotePercent=.01)
        println(contest)
        val v0 = contest.strata[0].votes
        val v1 = contest.strata[1].votes

        val contest7 = makeContestOA(23000, 21000, cvrPercent = .70, .007, undervotePercent=.01)
        println(contest7)
        val v07 = contest7.strata[0].votes
        val v17 = contest7.strata[1].votes

        repeat(2) {
            println(if (it == 0) "winner" else "loser")
            val diff0 = -(v0[it]!! - v07[it]!!)
            val diff0p = diff0/ v0[it]!!.toDouble()
            println(" hasCvr $it diff=$diff0 pct=$diff0p (${v0[it]!!} ->  ${v07[it]!!})")

            val diff1 = -(v1[it]!! - v17[it]!!)
            val diff1p = diff1 / v1[it]!!.toDouble()
            println(" noCvr $it diff=$diff1 pct=$diff1p (${v1[it]!!} ->  ${v17[it]!!})")
        }
    }

    @Test
    fun testAssorters() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, 0.0, undervotePercent=.01)
        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit(testCvrs)
        val minAllAsserter = contestOA.minComparisonAssertion()
        assertNotNull(minAllAsserter)
        val minAllAssorter = minAllAsserter.assorter
        println(minAllAssorter)

        val minAssorterMargin = minAllAssorter.calcAssorterMargin(contest.id, testCvrs)
        println(" calcAssorterMargin for minAllAssorter = $minAssorterMargin")

        val cass = minAllAsserter.cassorter as OneAuditComparisonAssorter
        println("   $cass")

    }
}
