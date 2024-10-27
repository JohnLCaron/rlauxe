package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Cvr

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
