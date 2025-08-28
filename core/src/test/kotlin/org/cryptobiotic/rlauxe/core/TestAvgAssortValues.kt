package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// when does (winner - loser) / Nc agree with AvgAssortValue?
class TestAvgAssortValues {

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
        val contest = makeContestFromCvrs(contestInfo, cvrs) // Nc is set as number of cvrs with that contest
        println(contest)
        val contestUA = ContestUnderAudit(contest, isComparison = false)

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvgMean = cvrs.map { cvr -> it.assorter.assort(cvr)}.average()
            val reportedMean = margin2mean(it.assorter.reportedMargin())
            println("${it.assorter.shortName()}: assortAvgMean=${assortAvgMean} reportedMean=${reportedMean}")
            assertEquals(assortAvgMean, reportedMean, doublePrecision)

            val calcMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            assertEquals(assortAvgMean, margin2mean(calcMargin), doublePrecision)
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
        val contestUA = ContestUnderAudit(contest, isComparison = false)

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
    fun testPluralityAssorterWithMissingContests() {
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
            // a cvr that doesnt have the contest on it; if you include it in the assortAvg, then assortAvg != reportedMean
            .addCvr().addContest("other", "1").ddone()

            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false)

        val assertions = contestUA.pollingAssertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = false)}.average()
            val reportedMean = margin2mean(it.assorter.reportedMargin())
            println("$it: assortAvg=${assortAvg} reportedMean=${reportedMean}")
            // assertEquals(assortAvg, reportedMean, doublePrecision)

            // this skips cvrs that dont have the contest
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
}