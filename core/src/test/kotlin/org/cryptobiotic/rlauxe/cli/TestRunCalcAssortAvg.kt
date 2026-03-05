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
               "-contest", "50", // use name for composite ??
               "-assertion", "1/0"
            )
        )
    }
}