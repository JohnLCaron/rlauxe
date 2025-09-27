package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifyContests {

    @Test
    fun testRunVerifyContests() {
        val topdir = "/home/stormy/rla/cases/boulder24oa"
        val contest = "16"
        RunVerifyContests.main(
            arrayOf(
                "-in", topdir,
                "-contest", contest,
            )
        )
    }
}