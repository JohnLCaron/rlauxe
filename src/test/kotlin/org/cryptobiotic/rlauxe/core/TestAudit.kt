package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.integration.makeCvrsByExactCount
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestAudit {

    @Test
    fun testPollingBasics() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidates = listOf(0, 1, 2, 3, 4),
            winners = listOf(2, 4),
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
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidates = listOf(0, 1, 2, 3, 4),
            winners = listOf(2, 4),
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
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidates = listOf(0, 1, 2, 3, 4),
            winners = listOf(2, 4),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        val audit = makeComparisonAudit(listOf(contest), riskLimit = .01, cvrs)
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
    fun testComparisonSuper() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidates = listOf(0, 1, 2, 3, 4),
            winners = listOf(2, 4),
            minFraction = .66,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        val audit = makeComparisonAudit(listOf(contest), riskLimit = .01, cvrs)
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

}