package org.cryptobiotic.rlauxe.cli

// import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.Locations.testdataDir
import kotlin.test.Test

class TestRunCalcAssortAvg {

    @Test
    fun testRunCalcAssortAvg() {
        val topdir = "$cases/boulder/boulder24/oa"

        RunCalcAssortAvg.main(
            arrayOf(
                "-in", topdir,
               "-contest", "50", // use name for composite ??
               "-assertion", "1/0"
            )
        )
    }
}