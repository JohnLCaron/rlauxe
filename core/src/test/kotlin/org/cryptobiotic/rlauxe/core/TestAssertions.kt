package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doublePrecision
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
        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.assertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())
            println("$it: ${it.assorter.dilutedMargin()}")
            assertEquals(if (it.loser == 3) 3.0/9.0 else 2.0/9.0, it.assorter.dilutedMargin(), doublePrecision)
        }

        val firstAssertion = assertions.first()
        assertEquals("contest 0 winner: 4 loser: 0 upper: 1.0", firstAssertion.id())

        val expectShow = """ contestInfo: 'AvB' (0) candidates=[0, 1, 2, 3, 4] choiceFunction=PLURALITY nwinners=2 voteForN=2
    assorter:  Plurality winner=4 loser=0 dilutedMargin=22.2222% dilutedMean=61.1111%
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
        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.assertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<AboveThreshold>(it.assorter)
            assertEquals(1.0 / (2.0 * contest.info.minFraction!!), it.assorter.upperBound())
            println("$it: ${it.assorter.dilutedMargin()}")
            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr) }.average()
            val mean = margin2mean(it.assorter.dilutedMargin())
            assertEquals(assortAvg, mean)
        }
        val firstAssertion = assertions.first()
        assertEquals(firstAssertion, firstAssertion)
        assertEquals(firstAssertion.hashCode(), firstAssertion.hashCode())
        assertEquals("'AvB' (0) AboveThreshold for 'E': dilutedMean=57.1429% noerror=53.0303% g= [-0.4 .. 0.6] h = [0.0 .. 1.25] margin=0.1429", firstAssertion.toString())
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
        val contestUA = ContestWithAssertions(contest, isClca = true).addStandardAssertions()

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
        assertEquals(" Plurality winner=4 loser=0 dilutedMargin=24.9160% dilutedMean=62.4580%", firstAssertion.toString())
        assertEquals("contest 0 winner: 4 loser: 0 upper: 1.0", firstAssertion.id())

        val expectShow = """ cassorter: ClcaAssorter for contest AvB (0)
  assorter= Plurality winner=4 loser=0 dilutedMargin=24.9160% dilutedMean=62.4580%
  dilutedMargin=0.24915951 dilutedMean=0.62457975 assortUpper=1.00000000 noerror=0.57115426"""
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
        val contestUA = ContestWithAssertions(contest, isClca = true).addStandardAssertions()

        val assertions = contestUA.clcaAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<ClcaAssertion>(it)
            assertIs<ClcaAssorter>(it.cassorter)
            assertIs<AboveThreshold>(it.cassorter.assorter())
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
        ContestWithAssertions(contest, isClca = true).addStandardAssertions()
        //}
        //println(exception)
        //assertNotNull(exception.message)
        //assertTrue(exception.message!!.contains("avgCvrAssortValue must be > .5"))
    }

}