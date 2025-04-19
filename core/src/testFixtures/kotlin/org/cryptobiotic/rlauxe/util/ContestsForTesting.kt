package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.checkEquivilentVotes
import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.*
import kotlin.random.Random

fun makeContestFromCvrs(
    info: ContestInfo,
    cvrs: List<Cvr>,
): Contest {
    val votes = tabulateVotesFromCvrs(cvrs.iterator())
    val ncards = cardsPerContest(cvrs)
    return Contest(
        info,
        votes[info.id] ?: emptyMap(),
        Nc=ncards[info.id] ?: 0,
        Np=0
    )
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

// tabulate votes, make sure of correct winners, count ncvrs for each contest
fun makeNcvrsPerContest(contests: List<Contest>, cvrs: List<Cvr>): Map<Int, Int> {
    val ncvrs = mutableMapOf<Int, Int>()  // contestId -> ncvr
    contests.forEach { ncvrs[it.id] = 0 } // make sure map is complete
    for (cvr in cvrs) {
        for (conId in cvr.votes.keys) {
            val accum = ncvrs.getOrPut(conId) { 0 }
            ncvrs[conId] = accum + 1
        }
    }
    contests.forEach {
        val ncvr = ncvrs[it.id]!!
        //	2.b) If there are more CVRs that contain the contest than the upper bound, something is seriously wrong.
        if (it.Nc < ncvr) throw RuntimeException(
            "upperBound ${it.Nc} < ncvrs ${ncvr} for contest ${it.id}"
        )
    }

    return ncvrs
}

fun makeContestsFromCvrs(
    cvrs: List<Cvr>,
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
): List<Contest> {
    val votes = tabulateVotesFromCvrs(cvrs.iterator())
    val ncards = cardsPerContest(cvrs)
    return makeContestsFromCvrs(votes, ncards, choiceFunction)
}

fun makeContestsFromCvrs(
    votes: Map<Int, Map<Int, Int>>,  // contestId -> candidate -> votes
    cards: Map<Int, Int>, // contestId -> ncards
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
): List<Contest> {
    val svotes = votes.toSortedMap()
    val contests = mutableListOf<Contest>()

    for ((contestId, candidateMap) in svotes.toSortedMap()) {
        val scandidateMap = candidateMap.toSortedMap()

        contests.add(
            Contest(
                ContestInfo(
                    name = "contest$contestId",
                    id = contestId,
                    choiceFunction = choiceFunction,
                    candidateNames = scandidateMap.keys.associate { "candidate$it" to it },
                    nwinners=1,
                ),
                voteInput = votes[contestId]!!,
                Nc = cards[contestId]!!,
                Np=0, // TODO
            )
        )
    }

    return contests
}

fun makeFakeContest(info: ContestInfo, ncvrs: Int): Contest {
    val cvrs = mutableListOf<Cvr>()
    repeat(ncvrs) {
        val votes = mutableMapOf<Int, IntArray>()
        val choice = Random.nextInt(info.nwinners)
        votes[0] = intArrayOf(choice)
        cvrs.add(Cvr("card-$it", votes))
    }
    return makeContestFromCvrs(info, cvrs)
}

fun makeContestUAfromCvrs(info: ContestInfo, cvrs: List<Cvr>, isComparison: Boolean=true, hasStyle: Boolean=true) : ContestUnderAudit {
    return ContestUnderAudit( makeContestFromCvrs(info, cvrs), isComparison, hasStyle)
}

fun makeContestUAFromCvrs(contests: List<Contest>, cvrs: List<Cvr>, hasStyles: Boolean=true): List<ContestUnderAudit> {
    if (contests.isEmpty()) return emptyList()

    val allVotes = mutableMapOf<Int, MutableMap<Int, Int>>() // contestId -> votes (cand -> vote)
    for (cvr in cvrs) {
        for ((conId, conVotes) in cvr.votes) {
            val accumVotes = allVotes.getOrPut(conId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }

    return allVotes.keys.map { conId ->
        val contest = contests.find { it.id == conId }
        if (contest == null)
            throw RuntimeException("no contest for contest id= $conId")
        val accumVotes = allVotes[conId]!!
        val contestUA = ContestUnderAudit(contest, true, hasStyles)
        require(checkEquivilentVotes((contestUA.contest as Contest).votes, accumVotes))
        contestUA
    }
}