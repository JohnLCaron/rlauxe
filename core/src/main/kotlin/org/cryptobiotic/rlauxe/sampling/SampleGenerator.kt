package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.math.ln
import kotlin.random.Random

// TODO move as much as possible into testing

//// abstraction for creating a sequence of samples
interface SampleGenerator {
    fun sample(): Double // get next in sample
    fun N(): Int  // population size
    fun reset()   // start over again with different permutation (may be prohibited)
}

//// For polling audits.

class PollWithReplacement(val contest: ContestUnderAudit, val cvrs : List<Cvr>, val assorter: AssorterFunction): SampleGenerator {
    val N = cvrs.size
    val sampleMean = cvrs.map { assorter.assort(it) }.average()
    val sampleCount = cvrs.sumOf { assorter.assort(it) }

    override fun sample(): Double {
        while (true) {
            val idx = secureRandom.nextInt(N) // with Replacement
            val cvr = cvrs[idx]
            if (cvr.hasContest(contest.id)) return assorter.assort(cvr)
        }
    }

    override fun reset() {}
    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun N() = N
}

class PollWithoutReplacement(val contest: ContestUnderAudit, val cvrs : List<Cvr>, val assorter: AssorterFunction): SampleGenerator {
    val ncvrs = cvrs.size
    val permutedIndex = MutableList(ncvrs) { it }
    var idx = 0

    init {
        reset()
    }

    override fun sample(): Double {
        while (idx < ncvrs) {
            val cvr = cvrs[permutedIndex[idx++]]
            if (cvr.hasContest(contest.id)) return assorter.assort(cvr)
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    fun sampleMean() = cvrs.map{ assorter.assort(it) }.average()
    fun sampleCount() = cvrs.sumOf { assorter.assort(it) }
    override fun N() = ncvrs
}

//// For comparison audits
// the values produced here are the B assort values, SHANGRLA section 3.2.

class ComparisonSamplerGen(
    val cvrPairs: List<Pair<Cvr, CvrUnderAudit>>, // (mvr, cvr)
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter,
    val allowReset: Boolean,
): SampleGenerator {
    val N = cvrPairs.size
    val permutedIndex = MutableList(N) { it }
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
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("ComparisonSamplerGen reset not allowed")
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun N() = N
}

// the mvr and cvr always agree.
class ComparisonNoErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter): SampleGenerator {
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

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun N() = N
}

// generate mvr by starting with cvrs and flipping exact # votes (type 2 errors only)
// to make mvrs have mvrMean.
data class ComparisonWithErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter, val mvrMean: Double,
                                val withoutReplacement: Boolean = true): SampleGenerator {
    val N = cvrs.size
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes: Int
    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
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

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun N() = N
}

// generate mvr by starting with cvrs and flipping (N * p2) votes (type 2 errors) and (N * p1) votes (type 1 errors)
// TODO: generalize to p3, p4
data class ComparisonWithErrorRates(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter,
                                    val p2: Double, val p1: Double = 0.0,
                                    val withoutReplacement: Boolean = true): SampleGenerator {
    val N = cvrs.size
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes2: Int
    val flippedVotes1: Int

    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
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

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun N() = N
}

///////////////////////

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
private fun add2voteOverstatements(cvrs: MutableList<Cvr>, needToChangeVotesFromA: Int): Int {
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

// change cvrs to add the given number of one-vote overstatements.
private fun add1voteOverstatements(cvrs: MutableList<Cvr>, needToChangeVotesFromA: Int): Int {
    if (needToChangeVotesFromA == 0) return 0
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

class ArrayAsGenSampleFn(val assortValues : DoubleArray): SampleGenerator {
    var index = 0

    override fun sample(): Double {
        return assortValues[index++]
    }

    override fun reset() {
        index = 0
    }

    fun sampleMean(): Double {
        return assortValues.toList().average()
    }

    fun sampleCount(): Double {
        return assortValues.toList().sum()
    }

    override fun N(): Int {
        return assortValues.size
    }
}

// generate random values with given mean
class GenSampleMeanWithReplacement(val N: Int, ratio: Double): SampleGenerator {
    val samples = generateSampleWithMean(N, ratio)
    override fun sample(): Double {
        val idx = secureRandom.nextInt(N) // with Replacement
        return samples[idx]
    }
    override fun reset() {
        // noop
    }
    fun sampleMean() = samples.average()
    fun sampleCount() = samples.sum()
    override fun N() = N
}

class GenSampleMeanWithoutReplacement(val N: Int, val ratio: Double): SampleGenerator {
    var samples = generateSampleWithMean(N, ratio)
    var index = 0
    override fun sample(): Double {
        return samples[index++]
    }
    override fun reset() {
        samples = generateSampleWithMean(N, ratio)
        index = 0
    }
    fun sampleMean() = samples.average()
    fun sampleCount() = samples.sum()
    override fun N() = N
}

class SampleFromArrayWithoutReplacement(val assortValues : DoubleArray): SampleGenerator {
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

    fun sampleCount() = assortValues.sum()
    fun sampleMean() = assortValues.average()
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

// https://www.baeldung.com/cs/sampling-exponential-distribution
// probability density function (PDF): f_lambda(x) = lambda * e^(-lambda * x)
// cumulative density function (CDF): F_lambda(x) =  1 - e^(-lambda * x)
// inverse cumulative density function (CDF): F_lambda^(-1)(u) = -(1/lambda) * ln(1-u)
// sample in [0, 1] with exponential distribution with decay lambda
class Exponential(lambda: Double) {
    val ilambda = 1.0 / lambda
    val n = 1.0

    fun next(): Double {
        val u = Math.random()
        val x = ilambda * ln(1.0-u)
        return x
    }
}
