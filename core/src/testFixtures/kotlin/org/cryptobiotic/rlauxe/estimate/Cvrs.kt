package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * single contest (=0), make candCounts.sum() cvrs
 * where there are candCounts(i) cvrs that voted for for candidate i.
 */
fun makeCvrsByExactCount(candCounts : List<Int>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    var count = 0
    candCounts.forEachIndexed { candId, it ->
        repeat(it) {
            cvrs.add(makeCvr(candId,"card-$count"))
            count++
        }
    }
    cvrs.shuffle( Random )
    return cvrs
}

/** make a Cvr (contest 0) that voted for candId */
fun makeCvr(candId: Int, name:String?=null, poolId:Int?=null): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[0] = intArrayOf(candId)
    return Cvr(name?:"card", votes, poolId=poolId)
}

/** make a Cvr undervote (no votes) for contest contestId */
fun makeUndervoteForContest(contestId: Int, name:String?=null): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[contestId] = IntArray(0)
    return Cvr(name?:"other", votes)
}

/** make ncards Cvrs for contest 0, candidates 0 and 1, that have the exact mean for candidate 0. */
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
    val expectedAVotes = (ncards * wantAvg).roundToInt()
    val actualAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    val needToChangeVotesFromA = actualAvotes - expectedAVotes
    return add2voteOverstatements(cvrs, needToChangeVotesFromA)
}

fun makeFlippedMvrs(cvrs: List<Cvr>, N: Int, p2o: Double?, p1o: Double?): List<Cvr> {
    val mmvrs = mutableListOf<Cvr>()
    mmvrs.addAll(cvrs)
    val flippedVotes2 = if (p2o == null) 0 else {
        add2voteOverstatements(mmvrs, needToChangeVotesFromA = roundUp(N * p2o))
    }
    val flippedVotes1 = if (p1o == null) 0 else {
        add1voteOverstatements(mmvrs, needToChangeVotesFromA = roundUp(N * p1o))
    }
    return mmvrs.toList()
}

// change cvrs to add the given number of one-vote overstatements.
fun add1voteOverstatements(cvrs: MutableList<Cvr>, needToChangeVotesFromA: Int): Int {
    if (needToChangeVotesFromA == 0) return 0
    val ncards = cvrs.size
    val startingAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    var changed = 0
    while (changed < needToChangeVotesFromA) {
        val cvrIdx = Random.nextInt(ncards)
        val cvr = cvrs[cvrIdx]
        if (cvr.hasMarkFor(0, 0) == 1) {
            val votes = mutableMapOf<Int, IntArray>()
            votes[0] = intArrayOf(2)
            cvrs[cvrIdx] = Cvr(cvr.id, votes)
            changed++
        }
    }
    val checkAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    // if (debug) println("flipped = $needToChangeVotesFromA had $startingAvotes now have $checkAvotes votes for A")
    require(checkAvotes == startingAvotes - needToChangeVotesFromA)
    return changed
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
                cvrs[cvrIdx] = Cvr(cvr.id, votes)
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
                cvrs[cvrIdx] = Cvr(cvr.id, votes)
                changed++
            }
        }
    }
    val checkAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    // if (debug) println("flipped = $needToChangeVotesFromA had $startingAvotes now have $checkAvotes votes for A")
    require(checkAvotes == startingAvotes - needToChangeVotesFromA)
    return changed
}




