package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.auditcenter.compareLists
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.test.Test

class TestDominionCvrReader {
    val show = false

    @Test
    fun showProblemCountyContests() {
        var filename = "$colorado2020/Washington/cvr.csv"
        println(filename)
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.schema.contests.sortedBy { it.contestName }.forEach { contest ->
            println("  $contest")
        }
        println("read ${export.cvrs.size} cvrs")
    }

    @Test
    fun testReadGarfield() {
        var filename = "$colorado2020/Garfield/cvr.csv"  // with fixed headers, originally cvr2.csv
        println(filename)
        val export: DominionCvrCsvSummary = GarfieldCsvReader(filename).read()
        export.schema.contests.sortedBy { it.contestName }.forEach { contest ->
            println("  $contest")
        }
        println("read ${export.cvrs.size} cvrs")
        // read 30543 cvrs
    }

}
