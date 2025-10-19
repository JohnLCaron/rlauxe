package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.abs
import kotlin.test.Test


class TestMakeOneContestUA {
    val Nc = 50000

    @Test
    fun testAllAreCvrs() {
        val margin = .02
        val (contestOA, ballotPools, testCvrs) =
            makeOneContestUA(margin, Nc, cvrFraction = 0.9, undervoteFraction = 0.0, phantomFraction = 0.0)

        assertEquals(Nc, contestOA.Nc)
        val contest = contestOA.contest as Contest
        val cvrVotes =  tabulateVotesFromCvrs(testCvrs.iterator())[0]!!
        assertEquals(cvrVotes, contest.votes)
        assertEquals(margin, contest.calcMargin(0, 1), doublePrecision)

        val oaAssorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        assertEquals(1, oaAssorter.poolAverages.assortAverage.size)
        println("contestOA = $contestOA")
    }

    @Test
    fun testHalfAreCvrs() {
        val margin = .02
        val cvrPercent = 0.5
        val (contestOA, pools, cvrs) = makeOneContestUA(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.0, phantomFraction = 0.0)

        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithUndervotes() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, pools, cvrs) = makeOneContestUA(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.10, phantomFraction = 0.0)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithPhantoms() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, pools, cvrs) = makeOneContestUA(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.0, phantomFraction = 0.03)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithUnderVotesAndPhantoms() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, pools, cvrs) = makeOneContestUA(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.10, phantomFraction = 0.03)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testMakeOneAudit() {
        val undervotePercent = .33
        val phantomPercent = 0.03
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val cvrPercents = listOf(0.01, 0.5, 0.99)
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                println("======================================================================================================")
                println("margin=$margin cvrPercent=$cvrPercent phantomPercent=$phantomPercent undervotePercent=$undervotePercent")
                val (contestOA, cardPools, testCvrs) =
                    makeOneContestUA(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = undervotePercent, phantomFraction = phantomPercent)
                checkBasics(contestOA, cardPools, margin, cvrPercent)
                checkAgainstCvrs(contestOA, cardPools, testCvrs, cvrPercent, undervotePercent, phantomPercent)
            }
        }
    }

    fun checkBasics(contestOA: OAContestUnderAudit, cardPools: List<CardPoolIF>, margin: Double, cvrPercent: Double) {
        println(contestOA)

        //val nvotes = contestOA.cvrNcards + ballotPools.map{ it.ncards }.sum()
        //assertEquals(roundToClosest(nvotes*cvrPercent), contestOA.cvrNcards)
        //showPct("cvrs", contestOA.cvrVotes, contestOA.cvrNcards)

        assertEquals(1, cardPools.size)
        val cardPool = cardPools.first()
        assertEquals("noCvr", cardPool.poolName)
        println(cardPool)
        val vunder = cardPool.votesAndUndervotes(contestOA.id)
        showPct("pool", vunder.candVotesSorted, cardPool.ncards())

        val ncast = contestOA.contest.Ncast()
        assertEquals(roundToClosest(ncast*(1.0 - cvrPercent)), cardPool.ncards())

        assertEquals(Nc, contestOA.Nc)
        val contest = contestOA.contest as Contest
        assertEquals(margin, contest.calcMargin(0, 1), doublePrecision)
        showPct("allVotes", contest.votes, contestOA.Nc)
        println()
    }

    fun checkAgainstCvrs(contestOA: OAContestUnderAudit, cardPools: List<CardPoolIF>, testCvrs: List<Cvr>, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double) {

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)
        // println("reportedMargin = ${bassorter.assorter.reportedMargin()} calcAssortMargin = ${mean2margin(bassorter.calcAssortMeanFromPools())} ")

        // sanity check
        val allCount = testCvrs.count()
        assertEquals(allCount, contestOA.Nc)

        val cvrCount = testCvrs.count { it.poolId == null && !it.phantom }
        val noCvrCount = testCvrs.count { it.poolId != null }
        println("allCount = $allCount cvrCount=$cvrCount noCvrCount=$noCvrCount")
        // assertEquals(cvrCount, contest.cvrNcards)
        // assertEquals(noCvrCount, ballotPools!!.ncards)

        val nphantom = testCvrs.count { it.hasContest(contestOA.id) && it.phantom }
        assertEquals(contestOA.Np, nphantom)
        val phantomPct = nphantom/ contestOA.Nc.toDouble()
        println("  nphantom=$nphantom pct= $phantomPct =~ ${phantomPct} abs=${abs(phantomPct - phantomPercent)} " +
                " rel=${abs(phantomPct - phantomPercent) /phantomPercent}")
        if (nphantom > 2) assertEquals(phantomPct, phantomPercent, .001)

        val nunder = testCvrs.count { it.hasContest(contestOA.id) && !it.phantom && it.votes[contestOA.id]!!.isEmpty() }
        // assertEquals(contest.undervotes, nunder)
        val underPct = nunder/ contestOA.Nc.toDouble()
        println("  nunder=$nunder == ${undervotePercent}; pct= $underPct =~ ${undervotePercent} abs=${abs(underPct - undervotePercent)} " +
                " rel=${abs(underPct - undervotePercent) /underPct}")
        if (nunder > 2) assertEquals(undervotePercent, underPct, .001)
    }
}