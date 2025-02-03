package org.cryptobiotic.rlauxe.raire

import kotlin.test.Test

class TestMakeRaireContest {

    @Test
    fun testMakeRaireContest() {
        val (rcontest, cvrs) = makeRaireContest(N=20000, minMargin=.05)
    }
}
