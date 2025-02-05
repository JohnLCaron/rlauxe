package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifier {

    @Test
    fun testClcaVerify() {
        val topdir = "/home/stormy/temp/persist/testPersistentWorkflowClca"
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