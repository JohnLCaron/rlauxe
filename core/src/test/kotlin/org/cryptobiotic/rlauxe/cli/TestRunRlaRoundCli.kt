package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

// use this to run a round, but not as a test
class TestRunRlaRoundCli {

    // @Test TODO wtf?
    fun testRliRoundCli() {
        // val topdir = "/home/stormy/rla/cases/boulder24oa"
        val topdir = "/home/stormy/rla/cases/corla/oneaudit"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
                "-test",
            )
        )
    }
}