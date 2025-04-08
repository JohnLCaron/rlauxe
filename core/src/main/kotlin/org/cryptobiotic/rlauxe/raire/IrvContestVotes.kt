package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*

data class IrvContestVotes(val irvContestInfo: ContestInfo) {
    // The candidate Ids must from 0 ... ncandidates-1, for Raire; use the ordering from ContestInfo.candidateIds
    val candidateIdMap = irvContestInfo.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap()
    val vc = VoteConsolidator()
    var countBallots = 0
    val notfound = mutableMapOf<Int, Int>()

    init {
        require(irvContestInfo.choiceFunction == SocialChoiceFunction.IRV)
    }

    fun addVote(votes: IntArray) {
        votes.forEach {
            if (candidateIdMap[it] == null) {
                // println("*** Cant find candidate '${it}' in irvContestVotes ${irvContestInfo}") // TODO why ?
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        val mappedVotes = votes.map { candidateIdMap[it] }
        vc.addVote(mappedVotes.filterNotNull().toIntArray())
    }
}

fun makeIrvContestVotes(irvContests: Map<Int, ContestInfo>, cvrIter: Iterator<Cvr>): Map<Int, IrvContestVotes> {
    val irvVotes = mutableMapOf<Int, IrvContestVotes>()

    println("makeIrvContestVotes")
    var count = 0
    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next()
        cvr.votes.forEach { (contestId, choiceIds) ->
            if (irvContests.contains(contestId)) {
                val irvContest = irvContests[contestId]!!
                val irvVote = irvVotes.getOrPut(contestId) { IrvContestVotes(irvContest) }
                irvVote.countBallots++
                if (!choiceIds.isEmpty()) {
                    irvVote.addVote(choiceIds)
                }
                count++
                if (count % 10000 == 0) print("$count ")
                if (count % 100000 == 0) println()
            }
        }
    }
    println(" read ${count} cvrs")
    return irvVotes
}

fun makeIrvContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, IrvContestVotes>): List<RaireContestUnderAudit> {
    val contests = mutableListOf<RaireContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val irvContestVotes = contestVotes[info.id]
        if (irvContestVotes == null) {
            println("*** Cant find contest '${info.id}' in irvContestVotes")
        } else {
            // TODO undervotes
            val rcontestUA = makeRaireContestUA(
                info,
                irvContestVotes.vc,
                Nc = irvContestVotes.countBallots, // TODO get this elsewhere?
                Np = 0,
            )
            contests.add(rcontestUA)

            //// annotate RaireContest with IrvRounds

            // The candidate Ids go from 0 ... ncandidates-1 because of Raire; use the ordering from ContestInfo.candidateIds
            // this just makes the candidateIds the sequential indexes (0..ncandidates-1)
            val candidateIdxs = info.candidateIds.mapIndexed { idx, candidateId -> idx }
            val cvotes = irvContestVotes.vc.makeVotes()
            val irvCount = IrvCount(cvotes, candidateIdxs)
            val roundResultByIdx = irvCount.runRounds()

            // now convert results back to using the real Ids:
            val candidateIndexToId = info.candidateIds.mapIndexed { idx, candidateId -> Pair(idx, candidateId) }.toMap()
            val roundPathsById = roundResultByIdx.ivrRoundsPaths.map { roundPath ->
                val roundsById = roundPath.rounds.map { round -> round.convert(candidateIndexToId) }
                IrvRoundsPath(roundsById, roundPath.irvWinner.convert(candidateIndexToId))
            }
            (rcontestUA.contest as RaireContest).roundsPaths.addAll(roundPathsById)
        }
    }
    return contests
}
