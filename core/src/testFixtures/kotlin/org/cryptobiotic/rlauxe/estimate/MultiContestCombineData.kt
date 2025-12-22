package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.makePhantomCards
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.shuffle
import kotlin.random.Random

// specify the contests with exact number of votes
data class MultiContestCombineData(
    val contests: List<Contest>,
    val totalBallots: Int, // including undervotes and phantoms
    val poolId: Int? = null,
) {
    val contestVoteTrackers: List<ContestVoteTracker>

    init {
        require(contests.size > 0)
        contestVoteTrackers = contests.map { ContestVoteTracker(it) }
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCardsFromContests(startCvrId : Int = 0, cardStyle:String?=null): List<AuditableCard> {
        contestVoteTrackers.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<AuditableCard>()
        repeat(totalBallots) {
            // add regular Cvrs including undervotes and phantoms
            result.add(makeCard(nextCardId++, contestVoteTrackers, cardStyle))
        }

        val phantoms = makePhantomCards(contests, startIdx = result.size)
        result.addAll(phantoms)
        result.shuffle(Random)
        return result
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestVoteTracker>, cardStyle:String?): AuditableCard {
        //     constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
        val cardBuilder = CardBuilder("card${nextCardId}", nextCardId, poolId=poolId, cardStyle=cardStyle)
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cardBuilder) }
        return cardBuilder.build(poolId)
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCvrsFromContests(startCvrId : Int = 0): List<Cvr> {
        contestVoteTrackers.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders(startCvrId).addContests(contestVoteTrackers.map { it.contest.info })
        val result = mutableListOf<Cvr>()
        repeat(totalBallots) {
            // add regular Cvrs including undervotes
            result.add(makeCvr(cvrbs, contestVoteTrackers))
        }

        result.addAll(makePhantomCvrs(contests))
        result.shuffle(Random)
        return result
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestVoteTracker>): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build(poolId)
    }
}

data class ContestVoteTracker(
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
        trackVotesRemaining.add( Pair(ncands, contest.Nundervotes()))
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
        if (votesLeft == 0)
            return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.replaceContestVote(info.id, null) // undervote
        } else {
            cvrb.replaceContestVote(info.id, info.candidateIds[candidateIdx])
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
        if (checkVoteCount != votesLeft)
            print("etet")
        require(checkVoteCount == votesLeft)
        return candidateIdx
    }
}