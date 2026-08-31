package org.cryptobiotic.rlauxe.cvr

import org.cryptobiotic.rlauxe.cvr.AnonymizeCvrs.main
import kotlin.test.Test

class TestAnonymizeCvrs {

    @Test
    fun testCheckAnonymizeCvrs() {
        val cvrs = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/test_case_cvr.csv"
        AnonymizeCvrs.main(
            arrayOf(
                "-input", cvrs,
            )
        )
    }

    @Test
    fun testAnonymizeCvrs() {
        val cvrs = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/test_case_cvr.csv"
        val output = "anonymize_cvr.csv"
        AnonymizeCvrs.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }
}