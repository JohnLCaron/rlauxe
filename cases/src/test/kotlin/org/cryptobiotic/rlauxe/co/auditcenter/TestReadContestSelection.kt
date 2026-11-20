package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.test.Test

class TestReadContestSelection {

    @Test
    fun testContestSelection() {
        val round1 = testContestSelection(1, emptyMap())
        val round2 = testContestSelection(2, round1)
    }

    fun testContestSelection(round:Int, prev: Map<String, ContestSelection>): Map<String, ContestSelection> {
        val filename = "$auditcenter/2026/general/files/round${round}/contestSelection.csv"
        val mcs: Map<String, ContestSelection> = ContestSelection.readFromFile(filename)
        println()
        println(filename)
        println("${ContestSelection.showHeader}, new")

        mcs.values.forEach {
            val prevSize = prev[it.contest_name]?.dbList?.size ?: 0
            val diff = it.dbList.size - prevSize
            println("${it.show()}, $diff")
        }
        return mcs
    }

}