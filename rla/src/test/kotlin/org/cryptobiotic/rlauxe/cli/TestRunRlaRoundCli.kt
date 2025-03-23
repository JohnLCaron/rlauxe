package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRoundCli {

    @Test
    fun testRlaRoundClca() {
        val topdir = "/home/stormy/temp/workflow/runBoulder24"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.csv",
            )
        )
    }

    // @Test
    fun testRlaRoundRaireClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.csv",
            )
        )
    }

    // @Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaPollingFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.csv",
            )
        )
    }

    @Test
    fun testRlaClcaFuzz() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.csv",
            )
        )
    }
}