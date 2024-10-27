package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.makeCvrsByExactCount
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestAudit {

    @Test
    fun testPollingBasics() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
        )
        val audit = makePollingAudit(listOf(contest), riskLimit = .01)
        assertIs<AuditPolling>(audit)
        println("audit = $audit")

        assertEquals(.01, audit.riskLimit)
        assertEquals(1, audit.contests.size)

        val assertions = audit.assertions[contest]
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())
        }
    }

    @Test
    fun testPollingSuper() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .42
        )
        val audit = makePollingAudit(listOf(contest), riskLimit = .01)
        assertIs<AuditPolling>(audit)
        println("audit = $audit")

        assertEquals(.01, audit.riskLimit)
        assertEquals(1, audit.contests.size)

        val assertions = audit.assertions[contest]
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<SuperMajorityAssorter>(it.assorter)
            assertEquals(1.0 / (2.0 * contest.minFraction!!), it.assorter.upperBound())
        }
    }

    @Test
    fun testComparisonBasics() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        val audit = makeComparisonAudit(listOf(contest), cvrs, riskLimit = .01, )
        assertIs<AuditComparison>(audit)
        println("audit = $audit")

        assertEquals(.01, audit.riskLimit)
        assertEquals(1, audit.contests.size)

        val assertions = audit.assertions[contest]
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<ComparisonAssertion>(it)
            assertIs<ComparisonAssorter>(it.assorter)
            assertIs< PluralityAssorter>(it.assorter.assorter)
            assertEquals(1.0, it.assorter.assorter.upperBound())
        }
    }

    @Test
    fun testComparisonSuperMajority() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .33,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        val audit = makeComparisonAudit(listOf(contest), cvrs, riskLimit = .01)
        assertIs<AuditComparison>(audit)
        println("audit = $audit")

        assertEquals(.01, audit.riskLimit)
        assertEquals(1, audit.contests.size)

        val assertions = audit.assertions[contest]
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<ComparisonAssertion>(it)
            assertIs<ComparisonAssorter>(it.assorter)
            assertIs< SuperMajorityAssorter>(it.assorter.assorter)
            assertEquals(1.0 / (2.0 * contest.minFraction!!), it.assorter.assorter.upperBound())
        }
    }

    @Test
    fun testComparisonSuperMajorityFail() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .66,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        // TODO: no winners have minFraction = .66, where do we test that ?
        val exception = assertFailsWith<RuntimeException> {
            makeComparisonAudit(listOf(contest), cvrs, riskLimit = .01)
        }
        println(exception)
        assertNotNull(exception.message)
        assertTrue(exception.message!!.contains("avgCvrAssortValue must be > .5"))
    }

}