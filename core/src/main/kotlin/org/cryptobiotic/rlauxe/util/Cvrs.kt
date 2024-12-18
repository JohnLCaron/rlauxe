package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.flipExactVotes
import java.security.SecureRandom
import kotlin.random.Random

val secureRandom = SecureRandom.getInstanceStrong()!!

// making CVRs for simulations and testing
fun makeCvrsByExactCount(counts : List<Int>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    var total = 0
    counts.forEachIndexed { idx, it ->
        repeat(it) {
            val votes = mutableMapOf<Int, IntArray>()
            votes[0] = intArrayOf(idx)
            cvrs.add(Cvr("card-$total", votes))
            total++
        }
    }
    cvrs.shuffle( secureRandom )
    return cvrs
}

fun makeCvr(idx: Int): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[0] = intArrayOf(idx)
    return Cvr("card", votes)
}

fun margin2mean(margin: Double) = (margin + 1.0) / 2.0
fun mean2margin(mean: Double) = 2.0 * mean - 1.0

fun makeCvrsByExactMean(ncards: Int, mean: Double) : List<Cvr> {
    val randomCvrs = mutableListOf<Cvr>()
    repeat(ncards) {
        val random = secureRandom.nextDouble(1.0)
        val cand = if (random < mean) 0 else 1
        val votes = mutableMapOf<Int, IntArray>()
        votes[0] = intArrayOf(cand)
        randomCvrs.add(Cvr("card-$it", votes))
    }
    flipExactVotes(randomCvrs, mean)
    return randomCvrs
}

///////////////////////////////////////////////////////////////////////////////

fun makeContestFromCvrs(
    info: ContestInfo,
    cvrs: List<CvrIF>,
): Contest {
    val votes = tabulateVotes(cvrs)
    val ncards = cardsPerContest(cvrs)

    if ((votes[info.id] == null) || (ncards[info.id] == null)) {
        print("")
    }

    return Contest(
        info,
        votes[info.id] ?: emptyMap(),
        ncards[info.id] ?: 0,
    )
}

fun makeContestsFromCvrs(
    cvrs: List<CvrIF>,
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
): List<Contest> {
    val votes = tabulateVotes(cvrs)
    val ncards = cardsPerContest(cvrs)
    return makeContestsFromCvrs(votes, ncards, choiceFunction)
}

// Number of votes in each contest, return contestId -> candidateId -> nvotes
fun tabulateVotes(cvrs: List<CvrIF>): Map<Int, Map<Int, Int>> {
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
fun cardsPerContest(cvrs: List<CvrIF>): Map<Int, Int> {
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
                votes[contestId]!!,
                cards[contestId]!!,
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
