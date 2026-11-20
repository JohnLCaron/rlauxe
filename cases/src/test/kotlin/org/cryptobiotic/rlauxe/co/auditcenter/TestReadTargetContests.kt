package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.Locations.home
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

class TestReadTargetContests {

    @Test
    fun testReadTargetContests() {
        val filename = "$auditcenter/2026/general/files/2026GeneralRLAContestList.xlsx"
        val targetContests = TargetContest.readFromFile(filename)
        println()
        println(filename)

        targetContests.forEach { println(it) }

        println()
        val morgan = targetContests.find{ it.countyName == "Morgan" }
        println("   $morgan")
    }

}