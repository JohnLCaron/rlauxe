package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestRunVerifyContests {

    @Test
    fun testRunVerifySf2024() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                "-show"
            )
        )
    }

    @Test
    fun testRunVerifySf2024oaContest1() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                "-contest", "1",
            )
        )
    }

    @Test
    fun testRunVerifyBoulder24oa() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                "-show"
            )
        )
    }

    @Test
    fun testRunVerifyBoulder24oaContest16() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        val contest = "16"
        RunVerifyContests.main(
            arrayOf(
                "-in", auditdir,
                "-contest", contest,
                "-show"
            )
        )
    }
}