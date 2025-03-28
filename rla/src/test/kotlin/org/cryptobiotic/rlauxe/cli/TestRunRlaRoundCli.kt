package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRoundCli {

    @Test
    fun testRlaRoundCorla() {
        val topdir = "/home/stormy/temp/corla/election"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    // @Test
    fun testRlaRoundRaireClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    // @Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaPollingFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    // @Test
    fun testRlaClcaFuzz() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }
}