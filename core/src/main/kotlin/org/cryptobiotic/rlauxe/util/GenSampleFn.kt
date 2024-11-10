package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import kotlin.math.ln
import kotlin.random.Random

//// abstraction for creating a sequence of samples
interface GenSampleFn { // TODO could be an Iterator
    fun sample(): Double // get next in sample
    fun reset()          // start over again with different permutation
    fun sampleMean(): Double // for simulations
    fun sampleCount(): Double
    fun N(): Int  // population size
}

//// For polling audits.

class PollWithReplacement(val cvrs : List<CvrIF>, val assorter: AssorterFunction): GenSampleFn {
    val N = cvrs.size
    val sampleMean = cvrs.map { assorter.assort(it) }.average()
    val sampleCount = cvrs.sumOf { assorter.assort(it) }

    override fun sample(): Double {
        val idx = secureRandom.nextInt(N) // with Replacement
        return assorter.assort(cvrs[idx])
    }

    override fun reset() {}
    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

class PollWithoutReplacement(val cvrs : List<CvrIF>, val assorter: AssorterFunction): GenSampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    var idx = 0

    init {
        reset()
    }

    override fun sample(): Double {
        require (idx < cvrs.size)
        require (permutedIndex[idx] < cvrs.size)
        val curr = cvrs[permutedIndex[idx++]]
        return assorter.assort(curr)
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sampleMean() = cvrs.map{ assorter.assort(it) }.average()
    override fun sampleCount() = cvrs.sumOf { assorter.assort(it) }
    override fun N() = N
}

//// For comparison audits
// the values produced here are the B assort values, SHANGRLA section 3.2.

class ComparisonSampler(val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, val contestUA: ContestUnderAudit, val cassorter: ComparisonAssorter): GenSampleFn {
    val N = cvrPairs.size
    val welford = Welford()
    var idx = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[idx]
            if (cvr.hasContest(contestUA.id) && cvr.sampleNum <= contestUA.sampleThreshold!!) {
                val result = cassorter.bassort(mvr, cvr) // not sure of cvr vs cvr.cvr, w/re raire
                welford.update(result)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        throw RuntimeException("reset not allowed")
    }

    // running values, not population values
    override fun sampleMean() = welford.mean
    override fun sampleCount() = welford.count.toDouble()

    override fun N() = N
}

// the mvr and cvr always agree.
class ComparisonNoErrors(val cvrs : List<CvrIF>, val cassorter: ComparisonAssorter): GenSampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cassorter.bassort(it, it)}.average()
        sampleCount = cvrs.sumOf { cassorter.bassort(it, it) }
    }

    override fun sample(): Double {
        require (idx < N)
        val curr = cvrs[permutedIndex[idx++]]
        return cassorter.bassort(curr, curr) // mvr == cvr, no errors. could just return cassorter.noerror
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

// generate mvr by starting with cvrs and flipping exact # votes (type 2 errors only)
// to make mvrs have mvrMean.
data class ComparisonWithErrors(val cvrs : List<CvrIF>, val cassorter: ComparisonAssorter, val mvrMean: Double,
                                val withoutReplacement: Boolean = true): GenSampleFn {
    val N = cvrs.size
    val mvrs : List<CvrIF>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes: Int
    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<CvrIF>()
        mmvrs.addAll(cvrs)
        flippedVotes = flipExactVotes(mmvrs, mvrMean)
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / N
    }

    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = secureRandom.nextInt(N) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

// generate mvr by starting with cvrs and flipping (N * p2) votes (type 2 errors) and (N * p1) votes (type 1 errors)
// TODO: generalize to p3, p4
data class ComparisonWithErrorRates(val cvrs : List<CvrIF>, val cassorter: ComparisonAssorter,
                                    val p2: Double, val p1: Double = 0.0,
                                    val withoutReplacement: Boolean = true): GenSampleFn {
    val N = cvrs.size
    val mvrs : List<CvrIF>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes2: Int
    val flippedVotes1: Int

    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<CvrIF>()
        mmvrs.addAll(cvrs)
        flippedVotes2 = add2voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p2).toInt())
        flippedVotes1 =  if (p1 == 0.0) 0 else {
            add1voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p1).toInt())
        }
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / N
    }

    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = secureRandom.nextInt(N) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

///////////////////////

// change cvrs to have the exact number of votes for wantAvg
fun flipExactVotes(cvrs: MutableList<CvrIF>, wantAvg: Double): Int {
    val ncards = cvrs.size
    val expectedAVotes = (ncards * wantAvg).toInt()
    val actualAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    val needToChangeVotesFromA = actualAvotes - expectedAVotes
    return add2voteOverstatements(cvrs, needToChangeVotesFromA)
}

// change cvrs to add the given number of two-vote overstatements.
// Note that we replace the Cvr in the list when we change it
fun add2voteOverstatements(cvrs: MutableList<CvrIF>, needToChangeVotesFromA: Int): Int {
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

// change cvrs to add the given number of one-vote overstatements.
fun add1voteOverstatements(cvrs: MutableList<CvrIF>, needToChangeVotesFromA: Int): Int {
    val ncards = cvrs.size
    val startingAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    var changed = 0
    while (changed < needToChangeVotesFromA) {
        val cvrIdx = secureRandom.nextInt(ncards)
        val cvr = cvrs[cvrIdx]
        if (cvr.hasMarkFor(0, 0) == 1) {
            val votes = mutableMapOf<Int, IntArray>()
            votes[0] = intArrayOf(2)
            cvrs[cvrIdx] = Cvr("card-$cvrIdx", votes)
            changed++
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

// generate a sample thats approximately mean = theta
fun generateUniformSample(N: Int) : DoubleArray {
    return DoubleArray(N) {
        secureRandom.nextDouble(1.0)
    }
}

// generate a sample thats approximately mean = theta
fun generateSampleWithMean(N: Int, ratio: Double) : DoubleArray {
    return DoubleArray(N) {
        val r = secureRandom.nextDouble(1.0)
        if (r < ratio) 1.0 else 0.0
    }
}

class ArrayAsGenSampleFn(val assortValues : DoubleArray): GenSampleFn {
    var index = 0

    override fun sample(): Double {
        return assortValues[index++]
    }

    override fun reset() {
        index = 0
    }

    override fun sampleMean(): Double {
        return assortValues.toList().average()
    }

    override fun sampleCount(): Double {
        return assortValues.toList().sum()
    }

    override fun N(): Int {
        return assortValues.size
    }
}

// generate random values with given mean
class GenSampleMeanWithReplacement(val N: Int, ratio: Double): GenSampleFn {
    val samples = generateSampleWithMean(N, ratio)
    override fun sample(): Double {
        val idx = secureRandom.nextInt(N) // with Replacement
        return samples[idx]
    }
    override fun reset() {
        // noop
    }
    override fun sampleMean() = samples.average()
    override fun sampleCount() = samples.sum()
    override fun N() = N
}

class GenSampleMeanWithoutReplacement(val N: Int, val ratio: Double): GenSampleFn {
    var samples = generateSampleWithMean(N, ratio)
    var index = 0
    override fun sample(): Double {
        return samples[index++]
    }
    override fun reset() {
        samples = generateSampleWithMean(N, ratio)
        index = 0
    }
    override fun sampleMean() = samples.average()
    override fun sampleCount() = samples.sum()
    override fun N() = N
}

class SampleFromArrayWithoutReplacement(val assortValues : DoubleArray): GenSampleFn {
    val N = assortValues.size
    val permutedIndex = MutableList(N) { it }
    var idx = 0

    init {
        reset()
    }

    override fun sample(): Double {
        require (idx < N)
        require (permutedIndex[idx] < N)
        return assortValues[permutedIndex[idx++]]
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sampleCount() = assortValues.sum()
    override fun sampleMean() = assortValues.average()
    override fun N() = N
}

///////////////////////////////////////////////////////////////

// generate Bernoulli with probability p.
// TODO where did I get this? numpy?
class Bernoulli(p: Double) {
    val log_q = ln(1.0 - p)
    val n = 1.0

    fun get(): Double {
        var x = 0.0
        var sum = 0.0
        while (true) {
            val wtf = ln( Math.random()) / (n - x)
            sum += wtf
            if (sum < log_q) {
                return x
            }
            x++
        }
    }
}
