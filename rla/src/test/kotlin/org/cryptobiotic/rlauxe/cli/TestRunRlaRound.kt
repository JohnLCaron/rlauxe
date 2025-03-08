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
    //@Test
    fun testRlaRoundRaireClca() {
        val topdir = kotlin.io.path.createTempDirectory().toString()
        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }

    //@Test
    fun testRlaRoundPolling() {
        val topdir = kotlin.io.path.createTempDirectory().toString()

        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }
}