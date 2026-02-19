package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunRoundAgainCli {

    @Test
    fun testRunRoundAgainOneAudit() {
        val auditDir = "$testdataDir/cases/sf2024/oa/audit"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contest", "23",
                "-round", "1",
                "-assertion", "76/75",
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

    @Test
    fun testRunRoundAgainComposite() {
        val auditDir = "$testdataDir/cases/belgium/2024"

        RunRoundAgainCli.main(
            arrayOf(
                "-auditDir", auditDir,
                "-contestName", "Namur",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }
}