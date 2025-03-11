package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifier {

    @Test
    fun testClcaVerify() {
        val topdir = "/home/stormy/temp/persist/testRunCli"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }

}