package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.test.Test

class TestReadResultReport {

    // ResultsReportSummary.csv

    // data class ResultsReportContest(
    //        val contestName: String,
    //        val targeted: Boolean,
    //        val winner: String,
    //        val risk: Double,
    //        val margin: Double,
    //        val mvrCount: Int,  // "audited sample count"
    //        val ballotCount: Int, // "ballot count"
    //        val voteMargin: Int, // test winner - loser
    //        val winnerVotes: Int,
    //        val loserVotes: Int,
    //        val totalVotes: Int, // just the sum of the winner and loser
    //    )

    @Test
    fun readResultsReportContest2024() {
        val filename = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
        val contests = readResultsReportContest(filename) { it }
        println("read ${contests.size} contests from $filename")
        println("\n${trunc("contest", -50)} margin, mvrCount, ballotCount,")

        contests.sortedBy { it.contestName }.forEach {
            val star = if (it.targeted) "*" else " "
            print("${trunc("$star${it.contestName}", -50)}   ${trunc(dfn(it.margin, 2), 4)}, ")
            println("${nfn(it.mvrCount, 7)},    ${nfn(it.ballotCount, 7)},")
        }
    }

}