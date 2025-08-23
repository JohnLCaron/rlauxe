package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*

// called from cases module to extract vote info from Cvrs
// do all the contests in one iteration
fun makeIrvContestVotes(irvContests: Map<Int, ContestInfo>, cvrIter: Iterator<Cvr>): Map<Int, IrvContestVotes> {
    val irvVotes = mutableMapOf<Int, IrvContestVotes>() // contestId -> contestVotes

    println("makeIrvContestVotes")
    var count = 0
    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next()
        cvr.votes.forEach { (contestId, candidateRanks) ->
            if (irvContests.contains(contestId)) {
                val irvContest = irvContests[contestId]!!
                val irvVote = irvVotes.getOrPut(contestId) { IrvContestVotes(irvContest) }
                irvVote.addVote(candidateRanks)

                count++
                if (count % 10000 == 0) print("$count ")
                if (count % 100000 == 0) println()
            }
        }
    }
    println(" read ${count} cvrs")
    return irvVotes
}

data class IrvContestVotes(val irvContestInfo: ContestInfo) {
    val vc = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
    var countBallots = 0

    // The candidate Ids must go From 0 ... ncandidates-1, for Raire; use the ordering from ContestInfo.candidateIds
    val candidateIdToIndex = irvContestInfo.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap()

    init {
        require(irvContestInfo.choiceFunction == SocialChoiceFunction.IRV)
    }

    fun addVote(candidateRanks: IntArray) {
        candidateRanks.forEach {
            if (candidateIdToIndex[it] == null) {
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        // convert to index for Raire
        val mappedVotes = candidateRanks.map { candidateIdToIndex[it] }
        if (mappedVotes.isNotEmpty()) vc.addVote(mappedVotes.filterNotNull().toIntArray())
        countBallots++
    }
}

// called from cases module to create RaireContestUnderAudit from ContestInfo and IrvContestVotes
fun makeRaireContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, IrvContestVotes>): List<RaireContestUnderAudit> {
    val contests = mutableListOf<RaireContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val irvContestVotes = contestVotes[info.id] // candidate indexes
        if (irvContestVotes == null) {
            println("*** Cant find contest '${info.id}' in irvContestVotes")
        } else {
            val rcontestUA = makeRaireContestUA(
                info,
                irvContestVotes.vc, // candidate indexes
                Nc = irvContestVotes.countBallots, // TODO get this elsewhere?
                Ncast = irvContestVotes.countBallots,
            )
            contests.add(rcontestUA)

            //// annotate RaireContest with IrvRounds TODO put inside RaireContestUnderAudit or RaireContest or makeRaireContestUA

            // The candidate Ids go from 0 ... ncandidates-1 because of Raire; use the ordering from ContestInfo.candidateIds
            // this just makes the candidateIds the sequential indexes (0..ncandidates-1)
            val candidateIdxs = info.candidateIds.mapIndexed { idx, candidateId -> idx } // TODO use candidateIdToIndex?
            val cvotes = irvContestVotes.vc.makeVotes()
            val irvCount = IrvCount(cvotes, candidateIdxs)
            val roundResultByIdx = irvCount.runRounds()

            // now convert results back to using the real Ids:
            val roundPathsById = roundResultByIdx.ivrRoundsPaths.map { roundPath ->
                val roundsById = roundPath.rounds.map { round -> round.convert(info.candidateIds) }
                IrvRoundsPath(roundsById, roundPath.irvWinner.convert(info.candidateIds))
            }
            (rcontestUA.contest as RaireContest).roundsPaths.addAll(roundPathsById)
        }
    }
    return contests
}
