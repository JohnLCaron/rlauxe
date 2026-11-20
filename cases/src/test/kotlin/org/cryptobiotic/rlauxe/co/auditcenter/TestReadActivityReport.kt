package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

class TestReadActivityReport {

    @Test
    fun testReadActivityReport() {
        val round1 = testReadActivityReport(1)
        val round2 = testReadActivityReport(2)
    }

    fun testReadActivityReport(round:Int) {
        val filename = "$auditcenter/2026/general/files/round${round}/activityReport.xlsx"
        val contestMvrs = ActivityReport.readFromFile(filename)
        println()
        println(filename)

        contestMvrs.forEach { (contest, activityList) ->
            println("   ${nfn(activityList.size, 3)}, $contest")
        }

        val contestName = "Morgan - Proposition 137 (STATUTORY)"
        println()
        println(contestName)
        val morgan = contestMvrs[contestName]!!
        morgan.forEach { activity ->
            println("   $activity")
        }

        // Note that the reaudit adds another record for the same ballot; so need to replace...
        // ActivityReport(county=Morgan, imprintedId=103-74-21, scannerId=103, batchId=74, recordId=21, dbId=94087, round=1, auditBoard=1, recordType=REAUDITED, discrepency=+2, consensus=Yes, comment=, revision=1, reauditComment=No Data, timeOfSubmission=11/17/2026 10:04:27 AM)
        // ActivityReport(county=Morgan, imprintedId=103-74-21, scannerId=103, batchId=74, recordId=21, dbId=94087, round=1, auditBoard=1, recordType=AUDITOR_ENTERED, discrepency=No Data, consensus=Yes, comment=, revision=2, reauditComment=Wrong Response, timeOfSubmission=11/17/2026 11:43:49 AM)
    }

}