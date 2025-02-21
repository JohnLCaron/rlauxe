package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.roundToInt
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

fun makeCvr(winner: Int, name:String?=null): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[0] = intArrayOf(winner)
    return Cvr(name?:"card", votes)
}

fun makeOtherCvrForContest(contestId: Int, name:String?=null): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[contestId] = IntArray(0)
    return Cvr(name?:"other", votes)
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




