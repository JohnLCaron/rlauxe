package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir.toString(),
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

        topPath.deleteRecursively()
    }

    @Test
    fun testCliRoundPolling() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
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

        topPath.deleteRecursively()
    }

    @Test
    fun testCliRoundRaire() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()

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

        topPath.deleteRecursively()
    }
}