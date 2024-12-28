package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.cardsPerContest
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.cryptobiotic.rlauxe.util.tabulateVotes
import kotlin.collections.iterator
import kotlin.random.Random

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
    cvrs.shuffle( Random )
    return cvrs
}

fun makeCvr(idx: Int): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[0] = intArrayOf(idx)
    return Cvr("card", votes)
}


fun makeCvrsByExactMean(ncards: Int, mean: Double) : List<Cvr> {
    val randomCvrs = mutableListOf<Cvr>()
    repeat(ncards) {
        val random = Random.nextDouble(1.0)
        val cand = if (random < mean) 0 else 1
        val votes = mutableMapOf<Int, IntArray>()
        votes[0] = intArrayOf(cand)
        randomCvrs.add(Cvr("card-$it", votes))
    }
    flipExactVotes(randomCvrs, mean)
    return randomCvrs
}


// change cvrs to have the exact number of votes for wantAvg
fun flipExactVotes(cvrs: MutableList<Cvr>, wantAvg: Double): Int {
    val ncards = cvrs.size
    val expectedAVotes = (ncards * wantAvg).toInt()
    val actualAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    val needToChangeVotesFromA = actualAvotes - expectedAVotes
    return add2voteOverstatements(cvrs, needToChangeVotesFromA)
}

// change cvrs to add the given number of two-vote over/understatements.
// Note that we replace the Cvr in the list when we change it
fun add2voteOverstatements(cvrs: MutableList<Cvr>, needToChangeVotesFromA: Int): Int {
    if (needToChangeVotesFromA == 0) return 0
    val ncards = cvrs.size
    val startingAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    var changed = 0

    // we need more A votes, needToChangeVotesFromA < 0>
    if (needToChangeVotesFromA < 0) {
        while (changed > needToChangeVotesFromA) {
            val cvrIdx = Random.nextInt(ncards)
            val cvr = cvrs[cvrIdx]
            if (cvr.hasMarkFor(0, 1) == 1) {
                val votes = mutableMapOf<Int, IntArray>()
                votes[0] = intArrayOf(0)
                cvrs[cvrIdx] = Cvr("card-$cvrIdx", votes)
                changed--
            }
        }
    } else {
        // we need more B votes, needToChangeVotesFromA > 0
        while (changed < needToChangeVotesFromA) {
            val cvrIdx = Random.nextInt(ncards)
            val cvr = cvrs[cvrIdx]
            if (cvr.hasMarkFor(0, 0) == 1) {
                val votes = mutableMapOf<Int, IntArray>()
                votes[0] = intArrayOf(1)
                cvrs[cvrIdx] = Cvr("card-$cvrIdx", votes)
                changed++
            }
        }
    }
    val checkAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    // if (debug) println("flipped = $needToChangeVotesFromA had $startingAvotes now have $checkAvotes votes for A")
    require(checkAvotes == startingAvotes - needToChangeVotesFromA)
    return changed
}

fun makeContestsFromCvrs(
    cvrs: List<Cvr>,
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
): List<Contest> {
    val votes = tabulateVotes(cvrs)
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
