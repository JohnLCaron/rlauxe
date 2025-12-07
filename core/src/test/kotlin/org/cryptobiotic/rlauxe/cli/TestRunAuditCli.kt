package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunAuditCli {

    @Test
    fun TestRunAuditCli() {
        val auditDir = "/home/stormy/rla/persist/testRunCli/oneaudit/audit"

        RunAuditCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "0/1",
            )
        )
    }
}