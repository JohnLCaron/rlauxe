package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.shuffle
import kotlin.math.abs
import kotlin.random.Random

private const val debugAdjust = false

data class MultiContestCombineData(
    val contests: List<Contest>,
    val totalBallots: Int, // including undervotes and phantoms
    val hasStyle: Boolean,
) {
    val contestBuilders: List<ContestTracker>

    init {
        require(contests.size > 0)
        contestBuilders = contests.map { ContestTracker(it) }
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCardsFromContests(startCvrId : Int = 0): List<AuditableCard> {
        contestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CardBuilders(startCvrId).addContests(contestBuilders.map { it.contest.info })
        val result = mutableListOf<AuditableCard>()
        repeat(totalBallots) {
            // add regular Cvrs including undervotes
            result.add(makeCard(cvrbs, contestBuilders))
        }

        val phantoms = makePhantomCards(contests)
        return result + phantoms
    }

    private fun makeCard(cvrbs: CardBuilders, fcontests: List<ContestTracker>): AuditableCard {
        val cvrb = cvrbs.addCard()
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cvrb) }
        return cvrb.build()
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCvrsFromContests(startCvrId : Int = 0): List<Cvr> {
        contestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders(startCvrId).addContests(contestBuilders.map { it.contest.info })
        val result = mutableListOf<Cvr>()
        repeat(totalBallots) {
            // add regular Cvrs including undervotes
            result.add(makeCvr(cvrbs, contestBuilders))
        }

        result.addAll(makePhantomCvrs(contests))
        result.shuffle(Random)
        return result
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTracker>): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build()
    }
}

data class ContestTracker(
    val contest: Contest,
) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val candIdToIdx = info.candidateIds.mapIndexed { idx, id -> Pair(id, idx) }.toMap()

    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        contest.votes.forEach{ (candId, votes) -> trackVotesRemaining.add( Pair(candIdToIdx[candId]!!, votes)) }
        votesLeft = contest.Ncast
    }

    // choose Candidate, add contest, including undervote
    fun addContestToCvr(cvrb: CvrBuilder) {
        if (votesLeft == 0) return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.addContest(info.name) // undervote
        } else {
            cvrb.addContest(info.name, info.candidateIds[candidateIdx])
        }
    }

    // choose Candidate, add contest, including undervote
    fun addContestToCard(cvrb: CardBuilder) {
        if (votesLeft == 0) return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.addContest(info.id, null) // undervote
        } else {
            cvrb.addContest(info.id, info.candidateIds[candidateIdx])
        }
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    fun chooseCandidate(choice: Int): Int {
        var sum = 0
        var nvotes = 0
        var idx = 0
        while (idx <= ncands) {
            nvotes = trackVotesRemaining[idx].second
            sum += nvotes
            if (choice < sum) break
            idx++
        }
        val candidateIdx = trackVotesRemaining[idx].first
        require(nvotes > 0)
        trackVotesRemaining[idx] = Pair(candidateIdx, nvotes - 1)
        votesLeft--

        val checkVoteCount = trackVotesRemaining.sumOf { it.second }
        require(checkVoteCount == votesLeft)
        return candidateIdx
    }
}