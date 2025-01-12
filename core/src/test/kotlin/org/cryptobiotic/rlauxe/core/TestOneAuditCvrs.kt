package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditCvrs {

    @Test
    fun testMakeCvrs() {
        val contestOA =  makeContestKalamazoo()
        println("contestOA = $contestOA")
        contestOA.strata.forEach { stratum ->
            val scvrs = stratum.makeCvrs()
            assertEquals(stratum.Nc, scvrs.size)
        }
        val cvrs = contestOA.makeCvrs()
        assertEquals(contestOA.Nc, cvrs.size)
    }

    @Test
    fun testMakeContestOAcvrs() {
        val contestOA = makeContestOA(23000, 21000, cvrPercent = .70, undervotePercent=.01)
        println("contestOA = $contestOA")
        contestOA.strata.forEach { stratum ->
            val scvrs = stratum.makeCvrs()
            assertEquals(stratum.Nc, scvrs.size)
        }
        val cvrs = contestOA.makeCvrs()
        assertEquals(contestOA.Nc, cvrs.size)
    }
}