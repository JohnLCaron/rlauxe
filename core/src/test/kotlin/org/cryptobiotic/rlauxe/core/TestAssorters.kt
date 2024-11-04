package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.tabulateVotes
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestAssorters {
    val cvrs = CvrBuilders()
        .addCrv().addContest("AvB", "0").ddone()
        .addCrv().addContest("AvB", "1").ddone()
        .addCrv().addContest("AvB", "2").ddone()
        .addCrv().addContest("AvB", "2").ddone()
        .addCrv().addContest("AvB", "2").ddone()
        // artifact of creating Contests and candidates from cvrs.
        .addCrv().addContest("AvB").addCandidate("3", 0).ddone()
        .addCrv().addContest("AvB", "4").ddone()
        .addCrv().addContest("AvB", "4").ddone()
        .addCrv().addContest("AvB", "4").ddone()
        .build()

    @Test
    fun testPluralityAssorters() {
        val contest = Contest(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
            winnerNames = listOf("2", "4"),
        )
        val audit = makePollingAudit(listOf(contest), cvrs, riskLimit = .01)

        val assertions = audit.assertions[contest.id]
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())
            println("$it: ${it.avgCvrAssortValue}, ${it.margin}")
            if (it.toString().contains("winner=4 loser=0"))
                println("ok")

            var count = 0
            var sum = 0.0
            cvrs.forEach { cvr ->
                println("   ${cvr.id}: ${it.assorter.assort(cvr)}")
                sum += it.assorter.assort(cvr)
                count++
            }
            println("$it: ${sum/count}, ${mean2margin(sum/count)}")
            println()
            //assertEquals(if (it.winner == 2) 0.125 else 0.25, it.margin)
        }
    }

    val df = "%6.4f"
    @Test
    fun testPollingSuper() {
        val contest = Contest(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .42
        )
        val cvrs = CvrBuilders()
            .addCrv().addContest("AvB").addCandidate("0", 0).ddone()
            .addCrv().addContest("AvB", "1").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCrv().addContest("AvB").addCandidate("3", 0).ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .build()
        val tallyAll = tabulateVotes(cvrs)
        val tallyFirst = tallyAll[0]!!
        val total = tallyFirst.values.map { it }.sum().toDouble()
        tallyFirst.forEach { (candidate, votes) -> println("$candidate = ${votes} = ${df.format(votes/total)}") }

        val audit = makePollingAudit(listOf(contest), cvrs, riskLimit = .01)
        assertIs<AuditPolling>(audit)
        println("audit = $audit")

        assertEquals(.01, audit.riskLimit)
        assertEquals(1, audit.contests.size)

        val assertions = audit.assertions[contest.id]
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<SuperMajorityAssorter>(it.assorter)
            assertEquals(1.0 / (2.0 * contest.minFraction!!), it.assorter.upperBound())
            println("$it: ${it.avgCvrAssortValue}, ${it.margin}")
            assertEquals(.5238095238095238, it.avgCvrAssortValue, doublePrecision)
            assertEquals(0.04761904761904767, it.margin, doublePrecision)
        }
    }
}