package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.*
import org.junit.jupiter.api.Assertions.assertNotEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestAssertions {

    @Test
    fun testPollingBasics() {
        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
        )
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())
            println("$it: ${it.assorter.reportedMargin()}")
            assertEquals(if (it.loser == 3) 3.0/9.0 else 2.0/9.0, it.assorter.reportedMargin(), doublePrecision)
        }
    }

    @Test
    fun testPollingSuper() {
        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
            minFraction = .40
        )
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<SuperMajorityAssorter>(it.assorter)
            assertEquals(1.0 / (2.0 * contest.info.minFraction!!), it.assorter.upperBound())
            println("$it: ${it.assorter.reportedMargin()}")
            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr) }.average()
            val mean = margin2mean(it.assorter.reportedMargin())
            assertEquals(assortAvg, mean)
        }
        val firstAssertion = assertions.first()
        assertEquals(firstAssertion, firstAssertion)
        assertEquals(firstAssertion.hashCode(), firstAssertion.hashCode())

        val lastAssertion = assertions.last()
        assertNotEquals(firstAssertion, lastAssertion)
        assertNotEquals(firstAssertion.hashCode(), lastAssertion.hashCode())
        assertEquals("'AvB' (0) SuperMajorityAssorter winner=4 minFraction=0.4 margin=0.0385", firstAssertion.toString())
    }

    @Test
    fun testClcaBasics() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()

        val assertions = contestUA.clcaAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<ClcaAssertion>(it)
            assertIs<ClcaAssorter>(it.cassorter)
            assertIs< PluralityAssorter>(it.cassorter.assorter())
            assertEquals(1.0, it.cassorter.assorter().upperBound())
        }
        val firstAssertion = assertions.first()
        val lastAssertion = assertions.last()
        assertNotEquals(firstAssertion, lastAssertion)
        assertNotEquals(firstAssertion.hashCode(), lastAssertion.hashCode())
        assertEquals(" winner=4 loser=0 reportedMargin=0.2492", firstAssertion.toString())

        assertTrue(firstAssertion.checkEquals(firstAssertion).isEmpty())
        assertEquals(" assorter not equal cassorter not equal", firstAssertion.checkEquals(lastAssertion))

        val expectedShow =
""" contestInfo: 'AvB' (0) candidates={A=0, B=1, C=2, D=3, E=4}
 assorter:  winner=4 loser=0 reportedMargin=0.2492
 cassorter: avgCvrAssortValue=0.6245797534553604 margin=0.24915950691072086 noerror=0.5711542564540217 upperBound=1.1423085129080435
"""
        assertEquals(expectedShow, firstAssertion.show())
    }

    @Test
    fun testClcaSuperMajority() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
            minFraction = .33,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()

        val assertions = contestUA.clcaAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<ClcaAssertion>(it)
            assertIs<ClcaAssorter>(it.cassorter)
            assertIs< SuperMajorityAssorter>(it.cassorter.assorter())
            assertEquals(1.0 / (2.0 * contest.info.minFraction!!), it.cassorter.assorter().upperBound())
        }
    }

    @Test
    fun testComparisonSuperMajorityFail() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
            minFraction = .66,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

        // TODO: no winners have minFraction = .66, where do we test that ?
        //val exception = assertFailsWith<RuntimeException> {
            val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()
        //}
        //println(exception)
        //assertNotNull(exception.message)
        //assertTrue(exception.message!!.contains("avgCvrAssortValue must be > .5"))
    }

}