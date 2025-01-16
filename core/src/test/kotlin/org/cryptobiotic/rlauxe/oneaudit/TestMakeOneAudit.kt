package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.OneAuditContest
import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeOneAudit {
    val N = 50000

    @Test
    fun testAllCvrs() {
        val margin = .02
        val contestOA: OneAuditContest = makeContestOA(margin, N, cvrPercent = 1.0, 0.0, undervotePercent = 0.0)
        assertEquals(1, contestOA.strata.size)
        assertEquals("hasCvr", contestOA.strata[0].strataName)
        assertEquals(N, contestOA.strata[0].Ng)
        assertEquals(margin, contestOA.strata[0].reportedMargin(0, 1), doublePrecision)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    @Test
    fun testNoCvrs() {
        val margin = .02
        val contestOA = makeContestOA(margin, N, cvrPercent = 0.0, 0.0, undervotePercent = 0.0)
        assertEquals(1, contestOA.strata.size)
        assertEquals("noCvr", contestOA.strata[0].strataName)
        assertEquals(N, contestOA.strata[0].Ng)
        assertEquals(margin, contestOA.strata[0].reportedMargin(0, 1), doublePrecision)
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)

        println("contestOA = $contestOA")
    }

    @Test
    fun testMakeOneAudit() {
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val cvrPercents = listOf(0.0, 0.5, 1.0)
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                println("margin=$margin cvrPercent=$cvrPercent")
                val contestOA = makeContestOA(margin, N, cvrPercent = cvrPercent, 0.0, undervotePercent = 0.0)
                checkBasics(contestOA, margin, cvrPercent)
            }
        }
    }

    fun checkBasics(contestOA: OneAuditContest, margin: Double, cvrPercent: Double) {
        println("contestOA = $contestOA")
        assertEquals(margin, contestOA.reportedMargin(0, 1), doublePrecision)
        contestOA.strata.forEach { stratum ->
            assertEquals(margin, stratum.reportedMargin(0, 1), doublePrecision) // oh i see
        }
    }
}