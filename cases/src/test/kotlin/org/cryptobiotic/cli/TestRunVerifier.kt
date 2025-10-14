package org.cryptobiotic.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord
import kotlin.test.Test

class TestRunVerifier {

    @Test
    fun testClcaVerify() {
        val topdir = "/home/stormy/rla/cases/sf2024/audit"
        RunVerifyAuditRecord.main(
            arrayOf(
                "-in", topdir
            )
        )
    }
}