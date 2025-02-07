package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaStartTest {

    @Test
    fun testRlaStartClca() {
        val topdir = "/home/stormy/temp/persist/testRlaStartClca"
        val mvrs =  "/home/stormy/temp/persist/testRlaStartClca/testMvrs.json"
        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.005",
                "-fuzzMvrs", ".0123",
                "-pctPhantoms", "0.0",
                "-mvrs", mvrs,
            )
        )
    }

    @Test
    fun testRlaStartPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaStartPolling"
        val mvrs =  "/home/stormy/temp/persist/testRlaStartPolling/testMvrs.json"

        RunRlaStartTest.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0123",
                "-mvrs", mvrs,
            )
        )
    }
}