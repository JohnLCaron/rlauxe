package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRound {

    @Test
    fun testRlaRoundClca() {
        val topdir = "/home/stormy/temp/persist/testRlaStartClca"
        val mvrs = "/home/stormy/temp/persist/testRlaStartClca/testMvrs.json"
        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", mvrs,
            )
        )
    }

    @Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaStartPolling"
        val mvrs =  "/home/stormy/temp/persist/testRlaStartPolling/testMvrs.json"

        RunRound.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", mvrs,
            )
        )
    }
}