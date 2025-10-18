package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeTestContestOAIrv {

    @Test
    fun testContestBasics() {
        val oaIrv = makeTestContestOAIrv()

        assertEquals(oaIrv, oaIrv)
        assertEquals(oaIrv.hashCode(), oaIrv.hashCode())
        assertEquals("TestOneAuditIrvContest (0) votes=N/A Nc=2120 minMargin=0.0198", oaIrv.showShort(), )
        assertEquals(-1.0, oaIrv.recountMargin(), doublePrecision)

        println(oaIrv.show())
    }
}