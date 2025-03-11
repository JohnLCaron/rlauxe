package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRound {

    // @Test
    fun testRlaRoundClca() {
        val topdir = "/home/stormy/temp/persist/runAuditClca"
        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }

    // @Test
    fun testRlaRoundRaireClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }

    //@Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/runAuditPolling"

        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }
}