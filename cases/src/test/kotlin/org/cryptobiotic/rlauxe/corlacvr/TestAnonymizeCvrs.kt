package org.cryptobiotic.rlauxe.corlacvr

import kotlin.test.Test

class TestAnonymizeCvrs {
    val base = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/generated"
    val baseOut = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon"

    val scenarios = listOf(
        "no_redaction",
        "needs_borrowing",
        "rare_unique_contest",
        "near_unanimous_fixable",
        "near_unanimous_unavoidable",
        "ballot_type_present",
        "blocked_style",
    )

    @Test
    fun testCheckAnonymizeCvrs() {
        val cvrs = "$base/near_unanimous_fixable.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "--mode", "check",
                "-input", cvrs,
            )
        )
    }

    @Test
    fun testAnonymizeCvrs() {
        val cvrs = "$base/near_unanimous_fixable.csv"
        val output = "$baseOut/near_unanimous_fixable.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }

    @Test
    fun testAllScenarioss() {
        scenarios.forEach { scenario ->
            println("===========================================================================================")
            AnonymizeCvrsCli.main(
                arrayOf(
                    "-input", "$base/$scenario.csv",
                    "-output", "$baseOut/$scenario.csv",
                    "--mode", "redact"
                )
            )
        }
    }

    @Test
    fun testAnonymizeDenver() {
        val cvrs = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Denver/cvr.csv"
        val output = "$baseOut/denver.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }
}