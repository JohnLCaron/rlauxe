package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import kotlin.random.Random

fun makeContestFromCvrs(
    info: ContestInfo,
    cvrs: List<Cvr>,
): Contest {
    val contestTabs = tabulateCvrs(cvrs.iterator(), mapOf(info.id to info))
    val contestTab = contestTabs[info.id]
    return if (contestTab == null)
        Contest(info, emptyMap(), 0, 0)
    else
        // the cvrs include the phantoms
        Contest(
            info,
            contestTab.votes,
            Nc=contestTab.ncards,
            Ncast=contestTab.ncards - contestTab.nphantoms,
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

    for ((contestId, candidateMap) in svotes) {
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
                Ncast=cards[contestId]!!,
            )
        )
    }

    return contests
}

fun makeContestFromFakeCvrs(info: ContestInfo, ncvrs: Int): Contest {
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

// candsv: candidate votes for each contest
// undervotes: undervotes for each contest
// phantoms: phantoms for each contest
// voteForNs: voteForN for each contest, default is 1
fun makeContestsWithUndervotesAndPhantoms(
    candsv: List<Map<Int, Int>>, undervotes: List<Int>, phantoms: List<Int>, voteForNs: List<Int>? = null)
: Pair<List<Contest>, List<Cvr>> {
    val candsMap = candsv.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()
    val phantomMap = phantoms.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()

    val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
    candsv.forEachIndexed { idx: Int, cands: Map<Int, Int> ->  // use the idx as the Id
        val voteForN = if (voteForNs == null) 1 else voteForNs[idx]
        contestVotes[idx] = VotesAndUndervotes(cands, undervotes[idx], voteForN = voteForN)
    }

    val cvrs = makeVunderCvrs(contestVotes, "ballot", null)

    // make the infos
    val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
    val infos = tabVotes.keys.associate { id ->
        val orgCands = candsMap[id]!!
        val candidateNames = orgCands.keys.associate { "cand$it" to it }
        val voteForN = if (voteForNs == null) 1 else voteForNs[id]
        Pair(id, ContestInfo("contest$id", id, candidateNames, SocialChoiceFunction.PLURALITY, voteForN = voteForN))
    }

    // make the contests
    val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
    val contests = contestTabs.map { (id, tab) ->
        val phantoms = phantomMap[id]!!
        Contest(infos[id]!!, tab.votes, tab.ncards + phantoms, tab.ncards)
    }

    // add the phantoms
    val phantoms =  makePhantomCvrs(contests)

    return Pair(contests, cvrs + phantoms)
}

//// use these when you dont have ContestInfo yet
// Number of votes in each contest, return contestId -> candidateId -> nvotes
fun tabulateVotesFromCvrs(cvrs: Iterator<Cvr>): Map<Int, Map<Int, Int>> {
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val accumVotes = votes.getOrPut(con) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }
    return votes
}

fun tabulateVotesWithUndervotes(cvrs: Iterator<Cvr>, contestId: Int, ncands: Int, voteForN: Int = 1): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    cvrs.forEach{ cvr ->
        if (cvr.hasContest(contestId) && !cvr.phantom) {
            val candVotes = cvr.votes[contestId] // should always succeed
            if (candVotes != null) {
                if (candVotes.size < voteForN) {  // undervote
                    val count = result[ncands] ?: 0
                    result[ncands] = count + (voteForN - candVotes.size)
                }
                for (cand in candVotes) {
                    val count = result[cand] ?: 0
                    result[cand] = count + 1
                }
            }
        }
    }
    return result
}