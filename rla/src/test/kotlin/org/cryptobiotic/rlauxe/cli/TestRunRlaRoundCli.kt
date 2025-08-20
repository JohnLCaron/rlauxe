package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

// use this to run a round, but not as a test
class TestRunRlaRoundCli {

    @Test
    fun testRliRoundCli() {
        val topdir = "/home/stormy/rla/cases/boulder24"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }
}