package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.roundToInt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeContestOA {
    val N = 50000

    @Test
    fun testAllCvrs() {
        val margin = .02
        val contestOA: OneAuditContest = makeContestOA(margin, N, cvrPercent = 1.0, undervotePercent = 0.0, phantomPercent = 0.0)
        assertEquals(N, contestOA.Nc())
        val contest = contestOA.contest as Contest
        assertEquals(contestOA.cvrVotes, contest.votes)
        assertEquals(margin, contest.calcMargin(0, 1), doublePrecision)

        assertEquals(1, contestOA.pools.size)
        val pool = contestOA.pools[1]!!
        assertEquals("noCvr", pool.name)
        assertEquals(0, pool.ncards)
        assertEquals(0.0, pool.calcReportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    @Test
    fun testHalfCvrs() {
        val margin = .02
        val cvrPercent = 0.5
        val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, undervotePercent = 0.0, phantomPercent = 0.0)

        checkBasics(contestOA, margin, cvrPercent)
    }

    @Test
    fun testSkewCvrs() {
        val contest = makeContestOA(30000, 20000, 0.5, 0.0, 0.0, skewPct = .03)
        val contestUA = contest.makeContestUnderAudit()
        println(contestUA)
        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)

        val poolMargin = contest.pools[1]!!.calcReportedMargin(0, 1)
        val poolAverage = margin2mean(poolMargin)
        println("assorterMargin=${bassorter.cvrAssortMargin} poolMargin=$poolMargin ")
    }

    @Test
    fun testWithUndervotes() {
        val margin = .02
        val cvrPercent = 0.5

        val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, undervotePercent = 0.10, phantomPercent = 0.0)
        checkBasics(contestOA, margin, cvrPercent)
    }

    @Test
    fun testMakeOneAudit() {
        val undervotePercent = .33
        val phantomPercent = 0.03
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val cvrPercents = listOf(0.01, 0.5, 1.0)
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                println("======================================================================================================")
                println("margin=$margin cvrPercent=$cvrPercent phantomPercent=$phantomPercent undervotePercent=$undervotePercent")
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
                checkBasics(contestOA, margin, cvrPercent)
                checkAgainstCvrs(contestOA, margin, cvrPercent, undervotePercent, phantomPercent)
            }
        }
    }

    fun checkBasics(contestOA: OneAuditContest, margin: Double, cvrPercent: Double) {
        println(contestOA)

        val nvotes = contestOA.cvrNcards + contestOA.pools.values.map{ it.ncards}.sum()
        assertEquals(roundToInt(nvotes*cvrPercent), contestOA.cvrNcards)
        showPct("cvrs", contestOA.cvrVotes, contestOA.cvrNcards)

        assertEquals(1, contestOA.pools.size)
        val pool = contestOA.pools[1]!!
        assertEquals("noCvr", pool.name)
        println(pool)
        showPct("pool", pool.votes, pool.ncards)

        assertEquals(roundToInt(nvotes*(1.0 - cvrPercent)), pool.ncards)

        assertEquals(N, contestOA.Nc())
        val contest = contestOA.contest as Contest
        assertEquals(margin, contest.calcMargin(0, 1), doublePrecision)
        showPct("allVotes", contest.votes, contestOA.Nc())
        println()
    }

    fun checkAgainstCvrs(contest: OneAuditContest, margin: Double, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double) {
        val testCvrs = makeTestMvrs(contest)
        val contestOA = contest.makeContestUnderAudit()

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)
        // println("reportedMargin = ${bassorter.assorter.reportedMargin()} calcAssortMargin = ${mean2margin(bassorter.calcAssortMeanFromPools())} ")

        // sanity check
        val allCount = testCvrs.count()
        assertEquals(allCount, contest.Nc())

        val cvrCount = testCvrs.filter { it.poolId == null && !it.phantom }.count()
        val noCount = testCvrs.filter { it.poolId != null }.count()
        println("allCount = $allCount cvrCount=$cvrCount noCount=$noCount")
        assertEquals(cvrCount, contest.cvrNcards)
        assertEquals(noCount, contest.pools[1]!!.ncards)

        val nphantom = testCvrs.count { it.hasContest(contest.id) && it.phantom }
        assertEquals(contest.Np(), nphantom)
        val phantomPct = nphantom/ contestOA.Nc.toDouble()
        println("  nphantom=$nphantom pct= $phantomPct =~ ${phantomPct} abs=${abs(phantomPct - phantomPercent)} " +
                " rel=${abs(phantomPct - phantomPercent) /phantomPercent}")
        if (nphantom > 2) assertEquals(phantomPct, phantomPercent, .001)

        val nunder = testCvrs.count { it.hasContest(contest.id) && !it.phantom && it.votes[contest.id]!!.isEmpty() }
        // assertEquals(contest.undervotes, nunder)
        val underPct = nunder/ contestOA.Nc.toDouble()
        println("  nunder=$nunder == ${undervotePercent}; pct= $underPct =~ ${undervotePercent} abs=${abs(underPct - undervotePercent)} " +
                " rel=${abs(underPct - undervotePercent) /underPct}")
        if (nunder > 2) assertEquals(undervotePercent, underPct, .001)
    }
}

fun showPct(what: String, votes: Map<Int, Int>, Nc: Int, winner: Int = 0, loser: Int = 1) {
    val winnerVotes = votes[winner] ?: 0
    val loserVotes = votes[loser] ?: 0
    val hasMargin = (winnerVotes - loserVotes) / Nc.toDouble()
    println("$what winnerVotes = $winnerVotes loserVotes = $loserVotes diff=${winnerVotes-loserVotes} Nc=${Nc} hasMargin=$hasMargin ")
}