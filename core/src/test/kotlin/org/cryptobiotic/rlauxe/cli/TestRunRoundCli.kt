package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

// test RunCli in temp directory
class TestRunRoundCli {

    @Test
    fun testRunRoundCli() {
        val topdir = "$testdataDir/cases/boulder24/oa"
        val auditdir = "$topdir/audit"

        RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "18/0/1",
            )
        )

    }

}