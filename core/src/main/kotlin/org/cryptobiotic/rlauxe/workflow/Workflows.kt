package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.TestH0Status

data class Round(val round: Int, val sampledIndices: List<Int>, val previousSamples: Set<Int>) {
    var newSamples: Int = 0
    init {
        newSamples = sampledIndices.count { it !in previousSamples }
    }

    override fun toString(): String {
        return "Round(round=$round, newSamples=$newSamples)"
    }
}

// 2.a) Check that the winners according to the CVRs are the reported winners on the Contest.
fun checkWinners(contestUA: ContestUnderAudit, sortedVotes: List<Map.Entry<Int, Int>>) {
    val contest = contestUA.contest
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("winners in contest ${contest} have duplicates")
        contestUA.done = true
        contestUA.status = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("tie in contest ${contest}")
            contestUA.done = true
            contestUA.status = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("winners ${contest.winners} does not contain candidateId $candId")
            contestUA.done = true
            contestUA.status = TestH0Status.ContestMisformed
            return
        }
    }
}