package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status

interface RlauxWorkflowIF {
    fun chooseSamples(roundIdx: Int, show: Boolean = false): List<Int> // return ballot indices to sample
    fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean  // return allDone
    fun showResultsOld(estSampleSize: Int)

    fun auditConfig() : AuditConfig
    fun getContests() : List<ContestUnderAudit>
    fun getBallotsOrCvrs() : List<BallotOrCvr>
}

// per round
data class AuditState(
    val name: String,
    val roundIdx: Int,
    val nmvrs: Int,
    val newMvrs: Int,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val contests: List<ContestUnderAudit>,
) {
    fun show() =
        "AuditState($name, $roundIdx, nmvrs=$nmvrs, newMvrs=$newMvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contests.size} ncontestsDone=${contests.filter { it.done }.count()}"
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

    // see if theres a tie TODO check this
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

// find first index where pvalue < riskLimit, and stays below the riskLimit for the rest of the sequence
fun samplesNeeded(pvalues: List<Double>, riskLimit: Double): Int {
    var firstIndex = -1
    pvalues.forEachIndexed { idx, pvalue ->
        if (pvalue <= riskLimit && firstIndex < 0) {
            firstIndex = idx
        } else if (pvalue >= riskLimit) {
            firstIndex = -1
        }
    }
    return firstIndex
}
