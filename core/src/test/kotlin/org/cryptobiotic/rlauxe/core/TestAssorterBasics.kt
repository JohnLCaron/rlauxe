package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestAssorterBasics {

    @Test
    fun testPluralityAssorters() {
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
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr)}.average()
            val mean = margin2mean(it.margin)
            println("$it: assortAvg=${assortAvg} mean=${mean}")
            assertEquals(assortAvg, mean, doublePrecision)
        }
    }

    val df = "%6.4f"
    @Test
    fun testPollingSuper() {
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

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
            nwinners = 2,
            minFraction = .42,
            )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        println("votes: [${showVotes((contestUA.contest as Contest).votes)}]")

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size, assertions.size)
        assertions.forEach { assertion ->
            assertIs<Assertion>(assertion)
            assertIs<SuperMajorityAssorter>(assertion.assorter)
            val facc = 1.0 / (2.0 * contest.info.minFraction!!)
            assertEquals(facc, assertion.assorter.upperBound())
            val assortAvg = cvrs.map { cvr ->
                val usew1 = cvr.hasMarkFor(contest.id, assertion.assorter.winner())
                val usew2 = cvr.hasOneVote(contest.id, contest.info.candidateIds)
                println("${cvr.id}: ${assertion.assorter.assort(cvr)} usew1=$usew1 usew2=$usew2")
                assertion.assorter.assort(cvr)
            }.average()
            val mean = margin2mean(assertion.margin)
            println("$assertion: facc=$facc assortAvg=${assortAvg} mean=${mean}")
            assertEquals(assortAvg, mean)
        }
    }
}

fun showVotes(votes: Map<Int, Int>) = buildString {
    votes.entries.forEach { (candidate, vote) -> append("$candidate=$vote, ") }
}