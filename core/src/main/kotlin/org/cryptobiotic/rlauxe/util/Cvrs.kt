package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*

fun makeContestFromCvrs(
    info: ContestInfo,
    cvrs: List<Cvr>,
): Contest {
    val votes = tabulateVotes(cvrs)
    val ncards = cardsPerContest(cvrs)
    return Contest(
        info,
        votes[info.id] ?: emptyMap(),
        Nc=ncards[info.id] ?: 0,
        Np=0
    )
}

// Number of votes in each contest, return contestId -> candidateId -> nvotes
fun tabulateVotes(cvrs: List<Cvr>): Map<Int, Map<Int, Int>> {
    val r = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val accumVotes = r.getOrPut(con) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }
    return r
}

// Number of cards in each contest, return contestId -> ncards
fun cardsPerContest(cvrs: List<Cvr>): Map<Int, Int> {
    val d = mutableMapOf<Int, Int>()
    for (cvr in cvrs) {
        for (con in cvr.votes.keys) {
            val accum = d.getOrPut(con) { 0 }
            d[con] = accum + 1
        }
    }
    return d
}
