package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunAuditCli {

    @Test
    fun TestRunAuditCli() {
        val auditDir = "/home/stormy/rla/attack/cardManifestAttack/audit"

        RunAuditCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1"
            )
        )
    }
}