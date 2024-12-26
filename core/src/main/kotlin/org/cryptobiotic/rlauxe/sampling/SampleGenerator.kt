package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.random.Random

// TODO move as much as possible into testing

//// abstraction for creating a sequence of samples
interface SampleGenerator {
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // population size
    fun reset()   // start over again with different permutation (may be prohibited)
}

//// For polling audits.

class PollWithReplacement(val contest: Contest, val mvrs : List<Cvr>, val assorter: AssorterFunction): SampleGenerator {
    val maxSamples = mvrs.count { it.hasContest(contest.id) }

    override fun sample(): Double {
        while (true) {
            val idx = secureRandom.nextInt(mvrs.size) // with Replacement
            val cvr = mvrs[idx]
            if (cvr.hasContest(contest.id)) return assorter.assort(cvr, usePhantoms = true)
        }
    }

    override fun reset() {}
    override fun maxSamples() = maxSamples
}

class PollWithoutReplacement(
    val contest: Contest,
    val mvrs : List<Cvr>,
    val assorter: AssorterFunction,
    val allowReset: Boolean = true,
): SampleGenerator {
    val maxSamples = mvrs.count { it.hasContest(contest.id) }
    private val permutedIndex = MutableList(mvrs.size) { it }
    private var idx = 0

    init {
        if (allowReset) reset()
    }

    override fun sample(): Double {
        while (idx < mvrs.size) {
            val cvr = mvrs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contest.id)) {
                return assorter.assort(cvr, usePhantoms = true)
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("PollWithoutReplacement reset not allowed")
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun maxSamples() = maxSamples
}

//// For comparison audits
// the values produced here are the B assort values, SHANGRLA section 3.2.

class ComparisonWithoutReplacement(
    val contestUA: Contest,
    val cvrPairs: List<Pair<Cvr, CvrUnderAudit>>, // (mvr, cvr)
    val cassorter: ComparisonAssorter,
    val allowReset: Boolean,
): SampleGenerator {
    val maxSamples = cvrPairs.count { it.first.hasContest(contestUA.id) }
    val permutedIndex = MutableList(cvrPairs.size) { it }
    var idx = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("ComparisonWithoutReplacement no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("ComparisonWithoutReplacement reset not allowed")
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun maxSamples() = maxSamples
}

// the mvr and cvr always agree.
class ComparisonNoErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter): SampleGenerator {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
    val permutedIndex = MutableList(cvrs.size) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cassorter.bassort(it, it)}.average()
        sampleCount = cvrs.sumOf { cassorter.bassort(it, it) }
    }

    override fun sample(): Double {
        require (idx < cvrs.size)
        val curr = cvrs[permutedIndex[idx++]]
        return cassorter.bassort(curr, curr) // mvr == cvr, no errors. could just return cassorter.noerror
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples
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
            val cvrIdx = secureRandom.nextInt(ncards)
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
            val cvrIdx = secureRandom.nextInt(ncards)
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

///////////////////////
//// DoubleArrays
fun randomPermute(samples : DoubleArray): DoubleArray {
    val n = samples.size
    val permutedIndex = MutableList(n) { it }
    permutedIndex.shuffle(Random)
    return DoubleArray(n) { samples[permutedIndex[it]] }
}






