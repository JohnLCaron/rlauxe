package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAssorterCvrMeans {

    @Test
    fun testMakeCvrsByExactMean() {
        val N = 10000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"),
            choiceFunction = SocialChoiceFunction.PLURALITY)

        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestFromCvrs(info, cvrs)
        println("\n$contest")

        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
        testMeanAssort(cvrs, contestUA)
    }

    @Test
    fun testWithUndervotes() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 111, 1 to 101))

        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            listOf(15, 123, 3), listOf(0, 0, 0))

        val contestUA = ContestUnderAudit(contests[2], isClca = false).addStandardAssertions()
        println("\n$contestUA")
        println("ncvrs = ${cvrs.size}")
        testMeanAssort(cvrs, contestUA)
    }

    @Test
    fun testWithPhantoms() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 111, 1 to 101))

        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            listOf(15, 123, 3), listOf(11, 12, 13))

        val contestUA = ContestUnderAudit(contests[1], isClca = false).addStandardAssertions()
        println("\n$contestUA")
        println("ncvrs = ${cvrs.size}")
        testMeanAssort(cvrs, contestUA)
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

        println("\n$contestUA")
        println("ncvrs = ${cvrs.size}")
        testMeanAssort(cvrs, contestUA)
    }

    fun testMeanAssort(cvrs: List<Cvr>, contestUA: ContestUnderAudit) {
        val assorter = contestUA.pollingAssertions[0].assorter
        val tracker = MeanMarginTracker(contestUA.id, assorter)
        cvrs.forEach {
            // println(it)
            tracker.addCvr(it)
        }
    }
}

private val show = false

class MeanMarginTracker(val contestId: Int, val assorter: AssorterIF) {
    val welford = Welford()
    val winner = assorter.winner()
    val loser = assorter.loser()

    var winnerCount = 0
    var loserCount = 0
    var ncards = 0

    fun addCvr(cvr: Cvr) {
        val cands = cvr.votes[contestId]
        if (cands != null) {
            if (cands.contains(winner)) winnerCount++
            if (cands.contains(loser)) loserCount++
        }
        ncards++

        val assortValue = assorter.assort(cvr, usePhantoms = false)
        welford.update(assortValue)
        testMeanMargin(assortValue, cvr.phantom)
    }

    fun testMeanMargin(assortValue: Double, isPhantom: Boolean) {
        val margin = (winnerCount - loserCount) / ncards.toDouble()
        val meanFromMargin = margin2mean(margin)
        val assortMean = welford.mean
        if (show) println(" assort = ${assortValue} assortMean=$assortMean meanFromMargin=$meanFromMargin")
        // if (isPhantom) println(" assort = ${assortValue} assortMean=$assortMean meanFromMargin=$meanFromMargin")

        if (!doubleIsClose(meanFromMargin, assortMean)) {
            println(" *** assortMean=$assortMean meanFromMargin=$meanFromMargin")
        }
        assertEquals(meanFromMargin, assortMean, doublePrecision)
    }

}