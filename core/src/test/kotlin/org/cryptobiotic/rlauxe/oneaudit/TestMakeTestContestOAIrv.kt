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
        assertEquals("TestOneAuditIrvContest (0) Nc=2120 winner 1 losers [0, 2, 3, 4, 42] minMargin=0.0198", oaIrv.showShort(), )
        assertTrue(oaIrv.minRecountMargin()!! > 0.0 && oaIrv.minRecountMargin()!! < 1.0)

        println(oaIrv.show())
    }
}