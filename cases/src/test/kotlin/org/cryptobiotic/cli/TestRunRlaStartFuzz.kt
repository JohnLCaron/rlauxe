package org.cryptobiotic.cli

import org.cryptobiotic.rlauxe.cli.RunRlaStartFuzz
import org.cryptobiotic.rlauxe.cli.RunRlaStartOneAudit
import org.cryptobiotic.rlauxe.persist.clearDirectory
import java.nio.file.Path
import kotlin.test.Test

class TestRunRlaStartFuzz {

    @Test
    fun testRlaPollingFuzz() {
        val topdir = "/home/stormy/rla/persist/testRlaPollingFuzz"
        clearDirectory(Path.of(topdir))

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
            )
        )
    }

    @Test
    fun testRlaClcaFuzz() {
        val topdir = "/home/stormy/rla/persist/testRlaClcaFuzz"
        clearDirectory(Path.of(topdir))
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "11",
            )
        )
    }

    @Test
    fun testRlaRaire() {
        val topdir = "/home/stormy/rla/persist/testRlaRaire"
        clearDirectory(Path.of(topdir))

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
    }


    @Test
    fun testRlaOA() {
        val topdir = "/home/stormy/rla/persist/testRlaOA"
        clearDirectory(Path.of(topdir))

        RunRlaStartOneAudit.main(
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
    }

}