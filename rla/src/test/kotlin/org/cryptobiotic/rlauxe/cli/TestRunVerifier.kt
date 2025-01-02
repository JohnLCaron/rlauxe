package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifier {

    @Test
    fun testVerify() {
        val topdir = "/home/stormy/temp/persist/testPersistentWorkflow"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }
}