package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunShowPersistantStateResults {

   //  @Test
    fun testRunCli() {
        // val topdir = "/home/stormy/temp/persist/testRunCli"
        val topdir = kotlin.io.path.createTempDirectory().toString()
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }

   //  @Test
    fun testClcaVerify() {
//        val topdir = "/home/stormy/temp/persist/testRlaStartClca"
        val topdir = kotlin.io.path.createTempDirectory().toString()
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }

    // @Test
    fun testPollingVerify() {
        //val topdir = "/home/stormy/temp/persist/testPersistentWorkflowPolling"
        val topdir = kotlin.io.path.createTempDirectory().toString()
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }
}