package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunRoundAgainCli {

    @Test
    fun testRunRoundAgainOneAudit() {
        val auditDir = "$testdataDir/persist/testRunCli/oneaudit/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainClca() {
        val auditDir = "$testdataDir/persist/testRunCli/clca/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainPolling() {
        val auditDir = "$testdataDir/persist/testRunCli/polling/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainRaire() {
        val auditDir = "$testdataDir/persist/testRunCli/raire/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }
}