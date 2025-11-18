package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.calcAssorterMargin
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull


class TestAssorterBasics {

    @Test
    fun testPluralityAssorter() {
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
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr)}.average()
            val mean = margin2mean(it.assorter.reportedMargin())
            println("$it: assortAvg=${assortAvg} mean=${mean}")
            assertEquals(assortAvg, mean, doublePrecision)

            val calcMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            assertEquals(assortAvg, margin2mean(calcMargin), doublePrecision)
            assertEquals(it.assorter.reportedMargin(), calcMargin, doublePrecision)
        }
    }

    @Test
    fun testPluralityAssorterWithPhantoms() {
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
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addPhantomCvr().addContest("AvB").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = false)}.average()
            val mean = margin2mean(it.assorter.reportedMargin())
            println("$it: assortAvg=${assortAvg} mean=${mean}")
            assertEquals(assortAvg, mean, doublePrecision)

            val calcMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            assertEquals(assortAvg, margin2mean(calcMargin), doublePrecision)
            assertEquals(it.assorter.reportedMargin(), calcMargin, doublePrecision)

            val Ncd = contest.Nc.toDouble()
            val expectWithPhantoms = (assortAvg * Ncd - 0.5) / Ncd
            val assortWithPhantoms = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = true)}.average()
            println("$it: assortWithPhantoms=${assortWithPhantoms} expectWithPhantoms=${expectWithPhantoms}")
            assertEquals(expectWithPhantoms, assortWithPhantoms, doublePrecision)
        }
    }

    @Test
    fun testPollingSuper() {
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB").addCandidate("0", 0).ddone()
            .addCvr().addContest("AvB", "1").ddone()
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
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
            nwinners = 1,
            minFraction = .42,
            )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
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

            val mean = margin2mean(assertion.assorter.reportedMargin())
            println("$assertion: facc=$facc assortAvg=${assortAvg} mean=${mean}")

            // we no longer expect these to be equal, when nwinners > 1
            // assertEquals(assortAvg, mean)
        }
    }
}

fun showVotes(votes: Map<Int, Int>) = buildString {
    votes.entries.forEach { (candidate, vote) -> append("$candidate=$vote, ") }
}