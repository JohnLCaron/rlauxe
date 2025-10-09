package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class TestRunCli {

    @Test
    fun testCliRoundClca() {
        // val topdir = "/home/stormy/rla/persist/testCliRoundClca"
        val topdir = createTempDirectory().toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.005",
                "-fuzzMvrs", ".0123",
                "-ncards", "50000",
                "-ncontests", "25",
            )
        )

        repeat(3) {
            RunRliRoundCli.main(arrayOf("-in", topdir))
        }

        println("============================================================")
        RunVerifier.main(arrayOf("-in", topdir))
    }

    @Test
    fun testCliRoundPolling() {
        // val topdir = "/home/stormy/rla/persist/testCliRoundPolling"
        val topdir = createTempDirectory().toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-ncards", "20000",
            )
        )

        repeat(3) {
            RunRliRoundCli.main(arrayOf("-in", topdir))
        }

        RunVerifier.main(arrayOf("-in", topdir))
    }

    @Test
    fun testCliRoundRaire() {
        // val topdir = "/home/stormy/rla/persist/testCliRoundRaire"
        val topdir = createTempDirectory().toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addRaireContest",
                "--addRaireCandidates", "5",
            )
        )

        repeat(3) {
            RunRliRoundCli.main(arrayOf("-in", topdir))
        }

        RunVerifier.main(arrayOf("-in", topdir))
    }
}