package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction

// for testing, here to share between modules
fun makeCvrsByExactCount(counts : List<Int>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    var total = 0
    counts.forEachIndexed { idx, it ->
        repeat(it) {
            val votes = mutableMapOf<Int, Map<Int, Int>>()
            votes[0] = mapOf(idx to 1)
            cvrs.add(Cvr("card-$total", votes))
            total++
        }
    }
    cvrs.shuffle( secureRandom )
    return cvrs
}

fun makeCvr(idx: Int): Cvr {
    val votes = mutableMapOf<Int, Map<Int, Int>>()
    votes[0] = mapOf(idx to 1)
    return Cvr("card", votes)
}

// default one contest, two candidates ("A" and "B"), no phantoms, plurality
// margin = percent margin of victory of A over B (between += .5)
fun makeCvrsByMargin(ncards: Int, margin: Double = 0.0) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<Int, Map<Int, Int>>()
        val random = secureRandom.nextDouble(1.0)
        val cand = if (random < .5 + margin/2.0) 0 else 1
        votes[0] = mapOf(cand to 1)
        result.add(Cvr("card-$it", votes))
    }
    return result
}

fun margin2theta(margin: Double) = (margin + 1.0) / 2.0
fun theta2margin(theta: Double) = 2.0 * theta - 1.0

fun makeCvrsByExactMean(ncards: Int, mean: Double) : List<Cvr> {
    val randomCvrs = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<Int, Map<Int, Int>>()
        val random = secureRandom.nextDouble(1.0)
        val cand = if (random < mean) 0 else 1
        votes[0] = mapOf(cand to 1)
        randomCvrs.add(Cvr("card-$it", votes))
    }
    flipExactVotes(randomCvrs, mean)
    return randomCvrs
}


///////////////////////////////////////////////////////////////////////////////
// old, deprecated TODO get rid of?

fun tabulateVotes(cvrs: List<Cvr>): Map<Int, Map<Int, Int>> {
    val r = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val accumVotes = r.getOrPut(con) { mutableMapOf() }
            for ((cand, vote) in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + vote
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

fun makeContestsFromCvrs(
    votes: Map<Int, Map<Int, Int>>,  // contestId -> candidate -> votes
    cards: Map<Int, Int>, // contestId -> ncards
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
): List<Contest> {
    val svotes = votes.toSortedMap()
    val contests = mutableListOf<Contest>()

    for ((contestId, candidateMap) in svotes.toSortedMap()) {
        val scandidateMap = candidateMap.toSortedMap()
        val winner = scandidateMap.maxBy { it.value }.key

        contests.add(
            Contest(
                id = "contest$contestId",
                idx = contestId,
                choiceFunction = choiceFunction,
                candidateNames = scandidateMap.keys.map { "candidate$it" },
                winnerNames = listOf("candidate$winner"),
            )
        )
    }

    return contests
}
