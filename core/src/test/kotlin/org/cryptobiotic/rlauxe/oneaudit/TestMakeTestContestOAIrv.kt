package org.cryptobiotic.rlauxe.oneaudit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMakeTestContestOAIrv {

    @Test
    fun testContestBasics() {
        val oaIrv = makeTestContestOAIrv()

        assertEquals(oaIrv, oaIrv)
        assertEquals(oaIrv.hashCode(), oaIrv.hashCode())
        assertEquals("TestOneAuditIrvContest (0) votes=N/A Nc=2120 minMargin=0.0198", oaIrv.showShort(), )
        assertTrue(oaIrv.recountMargin() > 0.0 && oaIrv.recountMargin() < 1.0)

        println(oaIrv.show())
    }
}