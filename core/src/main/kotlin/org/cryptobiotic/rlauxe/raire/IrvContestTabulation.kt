package org.cryptobiotic.rlauxe.raire

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestTabulationOld
import org.cryptobiotic.rlauxe.core.*

private val logger = KotlinLogging.logger("IrvContestVotes")

// called from cases module to extract vote info from Cvrs
// do all the contests in one iteration
fun makeIrvContestVotes(irvContests: Map<Int, ContestInfo>, cvrIter: Iterator<Cvr>): Map<Int, IrvContestTabulation> { // contestId -> IrvContestVotes
    val irvVotes = mutableMapOf<Int, IrvContestTabulation>() // contestId -> contestVotes

    var count = 0
    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next()
        cvr.votes.forEach { (contestId, candidateRanks) ->
            if (irvContests.contains(contestId)) {
                val irvContest = irvContests[contestId]!!
                val irvVote = irvVotes.getOrPut(contestId) { IrvContestTabulation(irvContest) }
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

// return contestId -> ContestTabulation
fun tabulateCvrs(cvrs: Iterator<Cvr>, voteForN: Map<Int, Int>): Map<Int, IrvContestTabulation> {
    val votes = mutableMapOf<Int, IrvContestTabulation>()
    for (cvr in cvrs) {
        for ((contestId, conVotes) in cvr.votes) {
            val tab = votes.getOrPut(contestId) { ContestTabulationOld(voteForN[contestId]) }
            tab.addVotes(conVotes)
        }
    }
    return votes
}

// wrapper around VoteConsolidator; analog to ContestTabulation
data class IrvContestTabulation(val irvContestInfo: ContestInfo) {
    val vc = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
    var ncards = 0
    var novote = 0  // how many cards had no vote for this contest?

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
        if (candidateRanks.isEmpty()) novote++
    }
}

// called from cases module to create RaireContestUnderAudit from ContestInfo and IrvContestVotes
fun makeRaireContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, IrvContestTabulation>, contestNc: Map<Int, Int>): List<RaireContestUnderAudit> {
    val contests = mutableListOf<RaireContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val irvContestVotes = contestVotes[info.id] // candidate indexes
        if (irvContestVotes == null) {
            logger.warn{"*** Cant find contest '${info.id}' in irvContestVotes"}
        } else {
            val rcontestUA = makeRaireContestUA(
                info,
                irvContestVotes.vc, // candidate indexes
                Nc = contestNc[info.id] ?: irvContestVotes.ncards,
                Ncast = irvContestVotes.ncards,
            )
            contests.add(rcontestUA)
        }
    }
    return contests
}
