package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunRoundAgainCli {

    @Test
    fun testRunRoundAgainCli() {
        val auditDir = "/home/stormy/rla/persist/testRunCli/oneaudit/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "0/1",
            )
        )
    }
}