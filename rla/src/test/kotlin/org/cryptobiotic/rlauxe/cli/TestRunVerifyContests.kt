package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifyContests {

    @Test
    fun testRunVerifyContests() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                // "-show"
            )
        )
    }

    @Test
    fun testRunVerifyContestsAddPools() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        val contest = "16"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                // "-contest", contest,
                "-show"
            )
        )
    }
}