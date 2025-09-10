package org.cryptobiotic.rlauxe.raire

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*

private val logger = KotlinLogging.logger("IrvContestVotes")

// called from cases module to extract vote info from Cvrs
// do all the contests in one iteration
fun makeIrvContestVotes(irvContests: Map<Int, ContestInfo>, cvrIter: Iterator<Cvr>): Map<Int, IrvContestVotes> {
    val irvVotes = mutableMapOf<Int, IrvContestVotes>() // contestId -> contestVotes

    var count = 0
    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next()
        cvr.votes.forEach { (contestId, candidateRanks) ->
            if (irvContests.contains(contestId)) {
                val irvContest = irvContests[contestId]!!
                val irvVote = irvVotes.getOrPut(contestId) { IrvContestVotes(irvContest) }
                irvVote.addVotes(candidateRanks)

                count++
                if (count % 10000 == 0) print("$count ")
                if (count % 100000 == 0) println()
            }
        }
    }
    logger.debug{" read ${count} cvrs"}
    return irvVotes
}

data class IrvContestVotes(val irvContestInfo: ContestInfo) {
    val vc = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
    var ncards = 0

    // The candidate Ids must go From 0 ... ncandidates-1, for Raire; use the ordering from ContestInfo.candidateIds
    val candidateIdToIndex = irvContestInfo.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap()

    init {
        require(irvContestInfo.choiceFunction == SocialChoiceFunction.IRV)
    }

    fun addVotes(candidateRanks: IntArray) {
        candidateRanks.forEach {
            if (candidateIdToIndex[it] == null) {
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        // convert to index for Raire
        val mappedVotes = candidateRanks.map { candidateIdToIndex[it] }
        if (mappedVotes.isNotEmpty()) vc.addVote(mappedVotes.filterNotNull().toIntArray())
        ncards++
    }
}

// called from cases module to create RaireContestUnderAudit from ContestInfo and IrvContestVotes
fun makeRaireContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, IrvContestVotes>): List<RaireContestUnderAudit> {
    val contests = mutableListOf<RaireContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val irvContestVotes = contestVotes[info.id] // candidate indexes
        if (irvContestVotes == null) {
            logger.warn{"*** Cant find contest '${info.id}' in irvContestVotes"}
        } else {
            val rcontestUA = makeRaireContestUA(
                info,
                irvContestVotes.vc, // candidate indexes
                Nc = irvContestVotes.ncards, // TODO get this elsewhere?
                Ncast = irvContestVotes.ncards,
            )
            contests.add(rcontestUA)
        }
    }
    return contests
}
