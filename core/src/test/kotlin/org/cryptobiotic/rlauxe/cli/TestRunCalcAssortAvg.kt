package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestRunCalcAssortAvg {

    @Test
    fun testRunCalcAssortAvg() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"

        RunCalcAssortAvg.main(
            arrayOf(
                "-in", auditdir,
                "-contest", "0",
                "-assertion", "0/1"
            )
        )
    }
}