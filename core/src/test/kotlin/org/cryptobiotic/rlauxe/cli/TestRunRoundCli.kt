package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

// test RunCli in temp directory
class TestRunRoundCli {

    @Test
    fun testRunRoundCli() {
        val topdir = "$testdataDir/cases/sf2024/clca"
        val auditdir = "$topdir/audit"

        RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "49/148/149",
            )
        )
    }
}