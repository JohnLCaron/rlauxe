package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AssorterFunction
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilders
import kotlin.random.Random


// For Polling, single contest and assorter.
// adapted from MultiContestTestData, doesnt need to adjust votes, just use them as is from Contest
// TODO allow empty votes
class SimContest(val contest: Contest, val assorter: AssorterFunction) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val margin = assorter.reportedMargin()
    val ncards = contest.votes.map { it.value }.sum()

    val votes: Map<Int, Int> = contest.votes
    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(votes.toList())
        votesLeft = ncards
    }

    // makes a new, independent set of Cvrs with the contest votes
    fun makeCvrs(): List<Cvr> {
        resetTracker()
        val cvrbs = CvrBuilders().addContests(listOf(this.info))
        val result = mutableListOf<Cvr>()
        repeat(this.ncards) {
            val cvrb = cvrbs.addCrv()
            cvrb.addContest(info.name, chooseCandidate(Random.nextInt(votesLeft))).done()
            result.add(cvrb.build())
        }
        return result.toList()
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    fun chooseCandidate(choice: Int): Int {
        val check = trackVotesRemaining.map { it.second }.sum()
        require(check == votesLeft)

        var sum = 0
        var nvotes = 0
        var idx = 0
        while (idx < ncands) {
            nvotes = trackVotesRemaining[idx].second
            sum += nvotes
            if (choice < sum) break
            idx++
        }
        val candidateId = trackVotesRemaining[idx].first
        require(nvotes > 0)
        trackVotesRemaining[idx] = Pair(candidateId, nvotes-1)
        votesLeft--
        return candidateId
    }
}