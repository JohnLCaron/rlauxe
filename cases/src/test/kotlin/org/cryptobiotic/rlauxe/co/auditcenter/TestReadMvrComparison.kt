package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

class TestReadMvrComparison {

    @Test
    fun testReadMvrComparison() {
        val round1 = testReadMvrComparison(1)
        val round2 = testReadMvrComparison(2)
    }

    fun testReadMvrComparison(round:Int) {
        val filename = "$auditcenter/2026/general/files/round${round}/contestComparison.csv"
        val lines: List<MvrComparison> = MvrComparison.readFromFile(filename)
        val mvrs = MvrComparison.mvrByCountyAndContest(lines)

        println()
        println(filename)
        // println("${ContestSelection.showHeader}, new")

        mvrs.forEach { (county, countyMap) ->
            println(county)
            countyMap.forEach { (contest, contestList) ->
                println("   ${nfn(contestList.size, 3)}, $contest")
            }
        }
    }

}