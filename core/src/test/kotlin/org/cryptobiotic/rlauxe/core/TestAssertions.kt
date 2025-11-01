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
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()

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

        val firstAssertion = assertions.first()
        assertEquals("contest 0 winner: 4 loser: 0", firstAssertion.id())

        val expectShow = """ contestInfo: 'AvB' (0) candidates=[0, 1, 2, 3, 4] choiceFunction=PLURALITY nwinners=2 voteForN=2
    assorter:  winner=4 loser=0 reportedMargin=0.22222222 reportedMean=0.61111111
"""
        assertEquals(expectShow, firstAssertion.show())
    }

    @Test
    fun testPollingSuper() {
        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 1,
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
            .addCvr().addContest("AvB", "4").ddone()
            .build()
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()

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
        assertEquals("'AvB' (0) SuperMajorityAssorter winner=4 minFraction=0.4 margin=0.1429", firstAssertion.toString())
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
        val contestUA = ContestUnderAudit(contest, isClca = true).addStandardAssertions()

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
        assertEquals(" winner=4 loser=0 reportedMargin=0.24915951 reportedMean=0.62457975", firstAssertion.toString())
        assertEquals("contest 0 winner: 4 loser: 0", firstAssertion.id())

        val expectShow = """ cassorter: ClcaAssorter for contest AvB (0)
  assorter= winner=4 loser=0 reportedMargin=0.24915951 reportedMean=0.62457975
  cvrAssortMargin=0.24915951 noerror=0.57115426 upperBound=1.14230851"""
        assertEquals(expectShow, firstAssertion.show())

        assertTrue(firstAssertion.checkEquals(firstAssertion).isEmpty())
        assertEquals(" assorter not equal cassorter not equal", firstAssertion.checkEquals(lastAssertion))
    }

    @Test
    fun testClcaSuperMajority() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 1,
            minFraction = .33,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isClca = true).addStandardAssertions()

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
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 1,
            minFraction = .66,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

        // TODO: no winners have minFraction = .66, where do we test that ?
        //val exception = assertFailsWith<RuntimeException> {
        ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        //}
        //println(exception)
        //assertNotNull(exception.message)
        //assertTrue(exception.message!!.contains("avgCvrAssortValue must be > .5"))
    }

}