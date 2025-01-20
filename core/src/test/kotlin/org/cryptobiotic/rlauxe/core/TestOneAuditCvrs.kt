package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditCvrs {

    @Test
    fun testMakeCvrs() {
        val contestOA =  makeContestKalamazoo()
        println("contestOA = $contestOA")
        contestOA.strata.forEach { stratum ->
            val scvrs = stratum.makeFakeCvrs()
            assertEquals(stratum.Ng, scvrs.size)
        }
        val cvrs = contestOA.makeTestCvrs()
        assertEquals(contestOA.Nc, cvrs.size)
    }

    @Test
    fun testMakeContestOAcvrs() {
        val contestOA = makeContestOA(23333, 21678, cvrPercent = .70, .11, undervotePercent=.01, phantomPercent = .0)
        println("contestOA = $contestOA")
        contestOA.strata.forEach { stratum ->
            val scvrs = stratum.makeFakeCvrs()
            assertEquals(stratum.Ng, scvrs.size)
        }
        val cvrs = contestOA.makeTestCvrs()
        assertEquals(contestOA.Nc, cvrs.size)

        val ncvrs = cvrs.size.toDouble()
        val hasCount = cvrs.filter{ it.id.startsWith("card") }.count()
        val noCount = cvrs.filter{ it.id == "noCvr"}.count()
        println(" noCvrs=${df(noCount/ncvrs)} withCvrs=${df(hasCount/ncvrs)}")
    }
}