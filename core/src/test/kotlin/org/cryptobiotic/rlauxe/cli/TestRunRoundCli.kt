package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

// dont run in coverage Tests
class TestRunRoundCli {

    // @Test
    fun testStartAuditFirstRound() {
        val auditdir = "$testdataDir/cases/corla/polling3/audit"

        StartAuditFirstRound.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "14-37/35",
            )
        )
    }

    @Test
    fun testRunRoundCli() {
        val auditdir = "$testdataDir/cases/sf2024/oap/audit"

        RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "17-1/0",
            )
        )
    }

    //@Test
    fun testRunAllRoundsCli() {
        val auditdir = "$testdataDir/cases/corla/polling/audit"

        /* RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "28-NEN 107/102",
            )
        ) */

        val stopRound = 7
        println("============================================================")
        var done = false
        var finalRound: AuditRoundIF? = null
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            if (lastRound != null) finalRound = lastRound
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
        }

        print("auditIsComplete = ${finalRound?.auditIsComplete}")
    }
}