package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifier {

    @Test
    fun testClcaVerify() {
        val topdir = "/home/stormy/temp/cases/boulder24"
        RunVerifier.main(
            arrayOf(
                "-in", topdir
            )
        )
    }
}