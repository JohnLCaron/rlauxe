package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

// use this to run a round, but not as a test
class TestRunRlaRoundCli {

    // @Test
    fun testRliRoundCli() {
        val topdir = "/home/stormy/temp/cases/sf2024Poa/audit"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }
}