package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunRoundAgainCli {

    @Test
    fun testRunRoundAgainOneAudit() {
        val topdir = "$testdataDir/persist/testRunCli/oneaudit"

        RunRoundAgainCli.main(
            arrayOf(
                "-topdir", topdir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainClca() {
        val topdir = "$cases/belgium/belgium2024/Bruxelles"

        RunRoundAgainCli.main(
            arrayOf(
                "-topdir", topdir,
                "-contest", "2",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainPolling() {
        val topdir = "$testdataDir/persist/testRunCli/polling"

        RunRoundAgainCli.main(
            arrayOf(
                "-topdir", topdir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainRaire() {
        val topdir = "$testdataDir/persist/testRunCli/raire"

        RunRoundAgainCli.main(
            arrayOf(
                "-topdir", topdir,
                "-contest", "1",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }

    @Test
    fun testRunRoundAgainComposite() {
        val topdir = "$cases/belgium/belgium2024"

        RunRoundAgainCli.main(
            arrayOf(
                "-topdir", topdir,
                "-contestName", "Namur",
                "-round", "1",
                "-assertion", "first",
            )
        )
    }
}