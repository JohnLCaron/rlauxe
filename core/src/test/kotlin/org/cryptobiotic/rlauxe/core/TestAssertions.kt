package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
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
            println("$it: ${it.margin}")
            assertEquals(if (it.loser == 3) 3.0/9.0 else 2.0/9.0, it.margin, doublePrecision)
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
            println("$it: ${it.margin}")
            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr) }.average()
            val mean = margin2mean(it.margin)
            assertEquals(assortAvg, mean)
        }
    }

    @Test
    fun testComparisonBasics() {
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
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeComparisonAssertions(cvrs)

        val assertions = contestUA.comparisonAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<ComparisonAssertion>(it)
            assertIs<ComparisonAssorter>(it.cassorter)
            assertIs< PluralityAssorter>(it.cassorter.assorter)
            assertEquals(1.0, it.cassorter.assorter.upperBound())
        }
    }

    @Test
    fun testComparisonSuperMajority() {
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
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeComparisonAssertions(cvrs)

        val assertions = contestUA.comparisonAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<ComparisonAssertion>(it)
            assertIs<ComparisonAssorter>(it.cassorter)
            assertIs< SuperMajorityAssorter>(it.cassorter.assorter)
            assertEquals(1.0 / (2.0 * contest.info.minFraction!!), it.cassorter.assorter.upperBound())
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
            val contestUA = ContestUnderAudit(contest, isComparison = true).makeComparisonAssertions(cvrs)
        //}
        //println(exception)
        //assertNotNull(exception.message)
        //assertTrue(exception.message!!.contains("avgCvrAssortValue must be > .5"))
    }

}