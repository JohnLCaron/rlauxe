package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.makeCvr
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// assorter_mean_all = (whitmer-schuette)/N
// v = 2*assorter_mean_all-1
// u_b = 2*u/(2*u-v)  # upper bound on the overstatement assorter
// noerror = u/(2*u-v)

// Let
//     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
//     margin ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, aka the _diluted margin_).
//
//     ωi ≡ A(ci) − A(bi)   overstatementError
//     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
//     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)

//    Ng = |G_g|
//    Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng
//    margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
//    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
//    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
//    otherwise = 1/2

class TestAssorterComparisonOneAudit {

    @Test
    fun testBasics() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, loserCvr, otherCvr))

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val awinnerAvg = .555
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin

        val assorter_mean_poll = awinnerAvg
        val bassorter = OneAuditComparisonAssorter(contest, assorter, awinnerAvg, assorter_mean_poll=assorter_mean_poll)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        //    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
        //    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
        val loserVote = (1.0-assorter_mean_poll)/(2-margin)
        val winnerVote = (2.0-assorter_mean_poll)/(2-margin)
        println("loserVote=$loserVote winner=$winnerVote ")

        println(" mvr other overstatementError=${bassorter.overstatementError(otherCvr, winnerCvr, true)} ")
        println(" mvr winner overstatementError=${bassorter.overstatementError(winnerCvr, winnerCvr, true)} ")
        println(" mvr loser overstatementError=${bassorter.overstatementError(loserCvr, winnerCvr, true)} ")

        println(" mvr other bassort=${bassorter.bassort(otherCvr, winnerCvr)} ")
        println(" mvr winner bassort=${bassorter.bassort(winnerCvr, winnerCvr)} ")
        println(" mvr loser bassort=${bassorter.bassort(loserCvr, winnerCvr)} ")

        assertEquals(0.5, bassorter.bassort(otherCvr, winnerCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, winnerCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, winnerCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, loserCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, loserCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, loserCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, otherCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, otherCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, otherCvr), doublePrecision)
    }

    @Test
    fun testMakeContestUnderAudit() {
        val contest = makeContestOA(200, 180, cvrPercent = .50, undervotePercent = .0)
        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit(testCvrs)
        println(contestOA)

        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val phantomCvr = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        val bassorter = contestOA.minComparisonAssertion()!!.cassorter as OneAuditComparisonAssorter
        println(bassorter)
        // val noerror = 1.0 / (2.0 - margin / assorter.upperBound())
        println("  noerror=${bassorter.noerror()}")
        assertEquals(1.0 / (2.0 - bassorter.margin()) / bassorter.assorter().upperBound(), bassorter.noerror())

        assertEquals(0.0, bassorter.overstatementError(winnerCvr, winnerCvr, true))
        assertEquals(-1.0, bassorter.overstatementError(winnerCvr, loserCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, otherCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, phantomCvr, true))

        assertEquals(1.0, bassorter.overstatementError(loserCvr, winnerCvr, true))
        assertEquals(0.0, bassorter.overstatementError(loserCvr, loserCvr, true))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, otherCvr, true))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, phantomCvr, true))

        assertEquals(0.5, bassorter.overstatementError(otherCvr, winnerCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(otherCvr, loserCvr, true))
        assertEquals(0.0, bassorter.overstatementError(otherCvr, otherCvr, true))

        assertEquals(1.0, bassorter.overstatementError(phantomCvr, winnerCvr, true)) // check
        assertEquals(0.0, bassorter.overstatementError(phantomCvr, loserCvr, true)) // check
        assertEquals(0.5, bassorter.overstatementError(phantomCvr, phantomCvr, true)) // check, usual case
        // so overstatementError in [-1, -.5, 0, .5, 1]
    }
}