package org.cryptobiotic.rlauxe.raire

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class TestIrvCount {
    var nties = 0
    var nwinners = mutableMapOf<Int, Int>()

    // Need a lot of tries to be sure to get some double ties
    // @Test
    fun testRepeat() {
        var idx = 0
        repeat(1000) {
            print("\n$idx ")
            testIrvCount()
            idx++
        }
        println("\nnties=$nties nwinners=$nwinners")
    }

    @Test
    fun testIrvCount() {
        for (ncands in 3..7) {
            //println("\n====================================\n")
            print("$ncands,")
            testIrvCount(ncands)
        }
    }

    fun testIrvCount(ncands: Int) {
        val N = 20000
        val minMargin = .05
        val undervotePct = 0.0
        val phantomPct = 0.0
        val testContest = RaireContestTestData(
            0,
            ncands = ncands,
            ncards = N,
            minMargin = minMargin,
            undervotePct = undervotePct,
            phantomPct = phantomPct,
            excessVotes = 0,
        )
        val candidateIds = testContest.info.candidateIds
        val testCvrs = testContest.makeCvrs()

        val vc = VoteConsolidator()
        testCvrs.forEach { cvr ->
            val votes = cvr.votes[testContest.info.id]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val cvotes = vc.makeVotes()
        val irvCount = IrvCount(cvotes, candidateIds)

        val result = irvCount.runRounds()
        if (result.ivrRoundsPaths.size > 1) {
            nties++
            countWinners(result)
            println(showIrvCountResult(result, testContest.info))
            println()
        }

        val show = showIrvCountResult(result, testContest.info)
        println(show)
        assertTrue(show.contains("(winner)"))
     }

    fun countWinners(result: IrvCountResult) {
        val winners = mutableSetOf<Int>()
        result.ivrRoundsPaths.forEach { ivrRoundsPath ->
            winners.addAll(ivrRoundsPath.irvWinner.winners)
        }
        assertTrue(winners.isNotEmpty())
    }
}
