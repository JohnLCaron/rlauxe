package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunRlaRoundCli {

    @Test
    fun testRlaRoundSF() {
        val topdir = "/home/stormy/temp/cases/sf2024Poa/audit"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    @Test
    fun testRlaRoundCorla() {
        val topdir = "/home/stormy/temp/cases/corla"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    @Test
    fun testRrunBoulder() {
        val topdir = "/home/stormy/temp/cases/boulder23"

        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    @Test
    fun testRlaRoundPolling() {
        val topdir = "/home/stormy/temp/persist/testRlaPollingFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }

    @Test
    fun testRlaClcaFuzz() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        RunRliRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )
    }
}