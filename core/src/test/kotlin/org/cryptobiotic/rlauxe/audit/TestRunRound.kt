package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.cli.StartAuditFirstRound
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestRunRound {

    @Test
    fun testStartAuditFirstRound() {
        val auditdir = "$testdataDir/cases/corla/polling3/audit"

        StartAuditFirstRound.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "14-37/35",
            )
        )
    }

}