package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topdir = "/home/stormy/temp/persist/testRunCli"
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val mvrs =  "$topdir/testMvrs.json"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.005",
                "-fuzzMvrs", ".0123",
                "-pctPhantoms", "0.001",
                "-mvrs", mvrs,
                "-ncards", "5000",
                "-ncontests", "5",
            )
        )

        repeat(3) {
            RunRound.main(arrayOf("-in", topdir, "-mvrs", mvrs))
        }

        println("============================================================")
        RunVerifier.main(arrayOf("-in", topdir))
    }

    @Test
    fun testCliRoundPolling() {
        val topdir = kotlin.io.path.createTempDirectory().toString()
        val mvrs =  "$topdir/testMvrs.json"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-mvrs", mvrs,
            )
        )

        repeat(3) {
            RunRound.main(arrayOf("-in", topdir, "-mvrs", mvrs))
        }

        RunVerifier.main(arrayOf("-in", topdir))
    }
}