package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeOneAudit {
    val N = 50000

    @Test
    fun testAllCvrs() {
        val margin = .02
        val contestOA: OneAuditContest = makeContestOA(margin, N, cvrPercent = 1.0, 0.0, undervotePercent = 0.0, phantomPercent = 0.0)
        assertEquals(1, contestOA.strata.size)
        assertEquals("hasCvr", contestOA.strata[0].strataName)
        assertEquals(N, contestOA.strata[0].Ng)
        assertEquals(margin, contestOA.strata[0].reportedMargin(0, 1), doublePrecision)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    // @Test TODO not allowing this
    fun testNoCvrs() {
        val margin = .02
        val contestOA = makeContestOA(margin, N, cvrPercent = 0.0, 0.0, undervotePercent = 0.0, phantomPercent = 0.0)
        assertEquals(1, contestOA.strata.size)
        assertEquals("noCvr", contestOA.strata[0].strataName)
        assertEquals(N, contestOA.strata[0].Ng)
        assertEquals(margin, contestOA.strata[0].reportedMargin(0, 1), doublePrecision)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    @Test
    fun testHalfCvrs() {
        val margin = .02
        val contestOA = makeContestOA(margin, N, cvrPercent = 0.5, 0.0, undervotePercent = 0.0, phantomPercent = 0.0)
        assertEquals(2, contestOA.strata.size)
        assertEquals("noCvr", contestOA.strata[0].strataName)
        assertEquals(N/2, contestOA.strata[0].Ng)
        assertEquals(margin, contestOA.strata[0].reportedMargin(0, 1), doublePrecision)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        checkBasics(contestOA, .02, .5)
    }

    @Test
    fun testWithUndervotes() {
        val margin = .02
        val contestOA = makeContestOA(margin, N, cvrPercent = 0.5, 0.0, undervotePercent = 0.10, phantomPercent = 0.0)
        checkBasics(contestOA, .02, .5)
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
        println("contestOA = $contestOA")
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)
        contestOA.strata.forEach { stratum ->
            assertEquals(margin, stratum.reportedMargin(0, 1), .003) // equally divided
        }
    }

    fun checkAgainstCvrs(contest: OneAuditContest, margin: Double, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double) {
        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit(testCvrs)

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditComparisonAssorter
        println(bassorter)
        println("reportedMargin = ${bassorter.assorter.reportedMargin()} clcaMargin = ${bassorter.clcaMargin} ")

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
    }
}