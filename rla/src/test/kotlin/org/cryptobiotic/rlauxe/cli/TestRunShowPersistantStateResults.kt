package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunShowPersistantStateResults {

    @Test
    fun testRunCli() {
        val topdir = "/home/stormy/temp/persist/testRunCli"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }

    @Test
    fun testClcaVerify() {
        val topdir = "/home/stormy/temp/persist/testRlaStartClca"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }

    @Test
    fun testPollingVerify() {
        val topdir = "/home/stormy/temp/persist/testPersistentWorkflowPolling"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }
}