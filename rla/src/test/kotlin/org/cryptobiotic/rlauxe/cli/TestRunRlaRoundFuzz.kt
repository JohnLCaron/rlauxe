package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRoundFuzz {

    // @Test
    fun testRlaRoundClca() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        RunRoundFuzz.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }

    // @Test
    fun testRlaRoundRaireClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRoundFuzz.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }

    // @Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaPollingFuzz"
        RunRoundFuzz.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }
}