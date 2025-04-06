package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.roundToInt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeOneAudit {
    val N = 50000

    @Test
    fun testAllCvrs() {
        val margin = .02
        val contestOA: OneAuditContest = makeContestOA(margin, N, cvrPercent = 1.0, 0.0, undervotePercent = 0.0, phantomPercent = 0.0)
        assertEquals(N, contestOA.Nc)
        assertEquals(contestOA.cvrVotes, contestOA.votes)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        assertEquals(1, contestOA.pools.size)
        val pool = contestOA.pools[0]!!
        assertEquals("noCvr", pool.name)
        assertEquals(0, pool.ncards)
        assertEquals(0.0, pool.calcReportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    @Test
    fun testHalfCvrs() {
        val margin = .02
        val cvrPercent = 0.5
        val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0, phantomPercent = 0.0)

        checkBasics(contestOA, margin, cvrPercent)
    }

    @Test
    fun testWithUndervotes() {
        val margin = .02
        val cvrPercent = 0.5

        val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.10, phantomPercent = 0.0)
        checkBasics(contestOA, margin, cvrPercent)
    }

    @Test
    fun testMakeOneAudit() {
        val undervotePercent = .33
        val phantomPercent = 0.03
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val cvrPercents = listOf(0.01, 0.5, 1.0)
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                println("margin=$margin cvrPercent=$cvrPercent")
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
                checkBasics(contestOA, margin, cvrPercent)
                checkAgainstCvrs(contestOA, margin, cvrPercent, undervotePercent, phantomPercent)
            }
        }
    }

    fun checkBasics(contestOA: OneAuditContest, margin: Double, cvrPercent: Double) {
        println(contestOA)

        assertEquals(roundToInt(N*cvrPercent), contestOA.cvrNc)

        assertEquals(1, contestOA.pools.size)
        val pool = contestOA.pools[0]!!
        assertEquals("noCvr", pool.name)
        println(pool)

        assertEquals(roundToInt(N*(1.0 - cvrPercent)), pool.ncards)

        assertEquals(N, contestOA.Nc)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)
    }

    fun checkAgainstCvrs(contest: OneAuditContest, margin: Double, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double) {
        /* val testCvrs = makeTestCvrs(contest)
        val contestOA = contest.makeContestUnderAudit()

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OAClcaAssorter
        println(bassorter)
        println("reportedMargin = ${bassorter.assorter.reportedMargin()} clcaMargin = ${mean2margin(bassorter.meanAssort())} ")

        // sanity check
        val allCount = testCvrs.count()
        assertEquals(allCount, contest.Nc)

        val cvrCount = testCvrs.filter { it.id != "noCvr" }.count()
        val noCount = testCvrs.filter { it.id == "noCvr" }.count()
        println("allCount = $allCount cvrCount=$cvrCount noCount=$noCount")
        val strataCvr = contest.strata.find{ it.hasCvrs }
        val strataNo = contest.strata.find{ !it.hasCvrs }
        assertEquals(cvrCount, strataCvr?.Ng ?: 0)
        assertEquals(noCount, strataNo?.Ng ?: 0)

        val nphantom = testCvrs.count { it.hasContest(contest.id) && it.phantom }
        assertEquals(contest.Np, nphantom)
        val phantomPct = nphantom/ contestOA.Nc.toDouble()
        println("  nphantom=$nphantom pct= $phantomPct =~ ${phantomPct} abs=${abs(phantomPct - phantomPercent)} " +
                " rel=${abs(phantomPct - phantomPercent) /phantomPercent}")
        if (nphantom > 2) assertEquals(phantomPct, phantomPercent, .001)

        val nunder = testCvrs.count { it.hasContest(contest.id) && !it.phantom && it.votes[contest.id]!!.isEmpty() }
        assertEquals(contest.undervotes, nunder)
        val underPct = nunder/ contestOA.Nc.toDouble()
        println("  nunder=$nunder == ${undervotePercent}; pct= $underPct =~ ${undervotePercent} abs=${abs(underPct - undervotePercent)} " +
                " rel=${abs(underPct - undervotePercent) /underPct}")
        if (nunder > 2) assertEquals(undervotePercent, underPct, .001)

         */
    }
}