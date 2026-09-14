package org.cryptobiotic.rlauxe.cvr

import kotlin.test.Test

class TestAnonymizeCvrs {

    @Test
    fun testCheckAnonymizeCvrs() {
        val cvrs = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/generated/rare_unique_contest.csv"
        AnonymizeCvrs2.main(
            arrayOf(
                "--mode", "check",
                "-input", cvrs,
            )
        )
    }

    @Test
    fun testAnonymizeCvrs() {
        val cvrs = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/generated/needs_borrowing.csv"
        val output = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/needs_borrowing.csv"
        AnonymizeCvrs2.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }
}