package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.persist.clearDirectory
import java.nio.file.Path
import kotlin.test.Test

class TestRunRlaStartFuzz {

    @Test
    fun testRlaPollingFuzz() {
        val topdir = "/home/stormy/temp/persist/testRlaPollingFuzz"
        clearDirectory(Path.of(topdir))

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.csv",
            )
        )
    }

    @Test
    fun testRlaClcaFuzz() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        clearDirectory(Path.of(topdir))
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.csv",
                "-ncards", "10000",
                "-ncontests", "11",
            )
        )
    }

    @Test
    fun testRlaRaire() {
        val topdir = "/home/stormy/temp/persist/testRlaRaire"
        clearDirectory(Path.of(topdir))

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.csv",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addRaireContest",
                "--addRaireCandidates", "5",
            )
        )
    }

    /*
    @Test
    fun testRlaOA() {
        val topdir = "/home/stormy/temp/persist/testRlaOA"
        clearDirectory(Path.of(topdir))

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.csv",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addOAContest",
            )
        )
    }

     */

}