package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.auditcenter.auditcenter
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.votedatabase.votedatabase2020
import kotlin.test.Test

class TestDominionCvrReader {
    val show = false

    @Test
    fun showProblemExportCsv() {
        var filename = "$auditcenter/2026/primary/observerfiles/LaPlata_RedactedTest_NoContestBalance_CVR_Export_20260709081424.csv"
        println(filename)
        val export: DominionCvrExportCsv = readCvrExportsFromFile(filename)
        export.schema.contests.sortedBy { it.contestName }.forEach { contest ->
            println("${sfn(contest.contestName,-50)}  $contest")
        }
        println("read ${export.cvrs.size} cvrs")
    }

    @Test
    fun showProblemCountyContests() {
        var filename = "$votedatabase2020/Washington/cvr.csv"
        println(filename)
        val export: DominionCvrExportCsv = readCvrExportsFromFile(filename)
        export.schema.contests.sortedBy { it.contestName }.forEach { contest ->
            println("  $contest")
        }
        println("read ${export.cvrs.size} cvrs")
    }

    @Test
    fun testReadGarfield() {
        var filename = "$votedatabase2020/Garfield/cvr.csv"  // with fixed headers, originally cvr2.csv
        println(filename)
        val export = GarfieldCsvReader(filename)
        export.schema.contests.sortedBy { it.contestName }.forEach { contest ->
            println("  $contest")
        }
        println("read ${export.cvrs.size} cvrs")
        // read 30543 cvrs
    }

}
