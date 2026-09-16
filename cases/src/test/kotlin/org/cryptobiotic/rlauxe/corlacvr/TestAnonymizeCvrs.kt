package org.cryptobiotic.rlauxe.corlacvr

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAnonymizeCvrs {
    val base = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/generated"
    val baseOut = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon"

    val scenarios = listOf(
        "no_redaction",
        "needs_borrowing",
        "rare_unique_contest",
        "near_unanimous_fixable",
        "near_unanimous_unavoidable",
        "blocked_style",
        "ballot_type_present",
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

// scenario_ballot_type_present
// has two ballot styles with same contest set [0,1], these generate a single CardStyle
// and ballot style [1] has rows with different set [0], [0,1]
// row 19 could create a second CardStyle with name "1+1" meaning  balot type 1, variant1, with different contest set
//                    type votes
// 9,1,1,9,1-1-9,P1,    1, 1,0,1,0
// 12,1,1,12,1-1-12,P1, 2, 0,1,0,1
// 19,1,1,19,1-1-19,P1, 1, 1,0,,
// could ignore BallotType field for the purpose of redaction?
// TODO fix CardStyles with same name.

    @Test
    fun testBallotType() {
        val cvrs = "$base/ballot_type_present.csv"
        val output = "$baseOut/ballot_type_present.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }

    @Test
    fun testBlockedStyle() {
        val cvrs = "$base/blocked_style.csv"
        val output = "$baseOut/blocked_style.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )

    }

    @Test
    fun testAllScenarios() {
        scenarios.forEach { scenario ->
            println("===========================================================================================")
            AnonymizeCvrsCli.main(
                arrayOf(
                    "-input", "$base/$scenario.csv",
                    "-output", "$baseOut/$scenario.csv",
                    "--mode", "redact"
                )
            )

            val actual = File("$baseOut/$scenario.csv").readLines()
            val expect = File("/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/converted/$scenario.csv").readLines()
            assertEquals(expect.size, actual.size)
            val combine = expect.zip(actual)
            combine.forEach {
                assertEquals(it.first, it.second)
            }
            println("success")
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