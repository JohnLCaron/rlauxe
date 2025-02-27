package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaStartTest {

    @Test
    fun testRlaStartClca() {
        val topdir = "/home/stormy/temp/persist/testRunAuditClca"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/testMvrs.json",
                "-ncards", "10000",
                "-ncontests", "11",
            )
        )
    }

    @Test
    fun testRlaStartPolling() {
        val topdir = "/home/stormy/temp/persist/testRunAuditPolling"

        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-mvrs", "$topdir/testMvrs.json",
            )
        )
    }
}