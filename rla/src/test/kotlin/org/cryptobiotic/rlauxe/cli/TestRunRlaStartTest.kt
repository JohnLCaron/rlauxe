package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaStartTest {

    // @Test
    fun testRlaStartClca() {
        val topdir = "/home/stormy/temp/persist/runAuditClca"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.json",
                "-ncards", "10000",
                "-ncontests", "11",
            )
        )
    }

    // @Test
    fun testRlaStartRaireClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.json",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addRaireContest",
                "--addRaireCandidates", "5",
            )
        )
    }

    // @Test
    fun testRlaStartOAClca() {
        val topdir = "/home/stormy/temp/persist/runClcaRaire"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.json",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addOAContest",
                "--addRaireCandidates", "5",
            )
        )
    }

    // @Test
    fun testRunAuditPolling() {
        val topdir = "/home/stormy/temp/persist/runAuditPolling"

        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/private/testMvrs.json",
            )
        )
    }
}