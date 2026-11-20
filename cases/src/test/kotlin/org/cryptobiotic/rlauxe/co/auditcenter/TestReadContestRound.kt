package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

class TestReadContestRound {

    @Test
    fun TestReadContestRound() {
        val round1 = TestReadContestRound(1)
        val round2 = TestReadContestRound(2)
    }

    fun TestReadContestRound(round:Int) {
        val filename = "$auditcenter/2026/general/files/round${round}/contest.csv"
        val rounds: List<ContestRound> = ContestRound.readFromFile(filename)


        println()
        println(filename)
        // println("${ContestSelection.showHeader}, new")

        rounds.forEach {
            println(it)
        }
    }

}