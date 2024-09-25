package org.cryptobiotic.rlauxe.core

import kotlin.math.ln
import kotlin.random.Random

//// keeps track of the latest sample and the current and previous sample sum.
interface Samples {
    fun last(): Double // latest sample
    fun size(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun prevSum(): Double // sum os samples excluding latest
}

class PrevSamples() : Samples {
    private var last = 0.0
    private var size = 0
    private var sum = 0.0
    private var prevSum = 0.0

    override fun sum() = sum
    override fun last() = last
    override fun size() = size
    override fun prevSum() = prevSum

    fun addSample(sample : Double) {
        prevSum = sum
        sum += sample
        size++
        last = sample
    }
}

//// abstraction for creating a sequence of samples
interface SampleFn { // TODO could be an Iterator
    fun sample(): Double // get next in sample
    fun reset()          // start over again with different permutation
    fun sampleMean(): Double // for simulations
    fun sampleCount(): Double
    fun N(): Int  // population size
}

class PollWithReplacement(val cvrs : List<Cvr>, val assorter: AssorterFunction): SampleFn {
    val N = cvrs.size
    val sampleMean = cvrs.map { assorter.assort(it) }.average()
    val sampleCount = cvrs.map { assorter.assort(it) }.sum()

    override fun sample(): Double {
        val idx = Random.nextInt(N) // with Replacement
        return assorter.assort(cvrs[idx])
    }

    override fun reset() {}
    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

class PollWithoutReplacement(val cvrs : List<Cvr>, val assorter: AssorterFunction): SampleFn {
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
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun sampleMean() = cvrs.map{ assorter.assort(it) }.average()
    override fun sampleCount() = cvrs.map{ assorter.assort(it) }.sum()
    override fun N() = N
}

///////////////////////////////////////////////////////////////
// the values produced here are the B assort values, SHANGRLA section 3.2.

class ComparisonNoErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter): SampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cassorter.bassort(it, it)}.average()
        sampleCount = cvrs.map { cassorter.bassort(it, it)}.sum()
    }

    override fun sample(): Double {
        require (idx < N)
        val curr = cvrs[permutedIndex[idx++]]
        return cassorter.bassort(curr, curr) // mvr == cvr, no errors
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

data class ComparisonWithErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter, val mvrMean: Double): SampleFn {
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
        val mmvrs: MutableList<Cvr> = mutableListOf<Cvr>()
        mmvrs.addAll(cvrs)
        flippedVotes = flipExactVotes(mmvrs, mvrMean)
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / N
    }

    override fun sample(): Double {
        val cvr = cvrs[permutedIndex[idx]]
        val mvr = mvrs[permutedIndex[idx]]
        idx++
        return cassorter.bassort(mvr, cvr)
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

//// DoubleArrays
fun randomPermute(samples : DoubleArray): DoubleArray {
    val n = samples.size
    val permutedIndex = MutableList(n) { it }
    permutedIndex.shuffle(Random)
    return DoubleArray(n) { samples[permutedIndex[it]] }
}

fun randomPermute(samples : MutableList<Cvr>): List<Cvr> {
    samples.shuffle(Random)
    return samples
}

// generate a sample thats approximately mean = theta
fun generateUniformSample(N: Int) : DoubleArray {
    return DoubleArray(N) {
        Random.nextDouble(1.0)
    }
}

// generate a sample thats approximately mean = theta
fun generateSampleWithMean(N: Int, ratio: Double) : DoubleArray {
    return DoubleArray(N) {
        val r = Random.nextDouble(1.0)
        if (r < ratio) 1.0 else 0.0
    }
}

class ArrayAsSampleFn(val assortValues : DoubleArray): SampleFn {
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

class SampleFromArrayWithReplacement(val N: Int, ratio: Double): SampleFn {
    val samples = generateSampleWithMean(N, ratio)
    override fun sample(): Double {
        val idx = Random.nextInt(N) // with Replacement
        return samples[idx]
    }
    override fun reset() {
        // noop
    }
    override fun sampleMean() = samples.average()
    override fun sampleCount() = samples.sum()
    override fun N() = N
}

class SampleFromArrayWithoutReplacement(val assortValues : DoubleArray): SampleFn {
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
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun sampleCount() = assortValues.sum()
    override fun sampleMean() = assortValues.average()
    override fun N() = N
}

class SampleFromArrayWithoutReplacementOld(val assortValues : DoubleArray): SampleFn {
    val selectedIndices = mutableSetOf<Int>()
    val N = assortValues.size

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(N)
            if (!selectedIndices.contains(idx)) { // withoutReplacement
                selectedIndices.add(idx)
                return assortValues[idx]
            }
            require(selectedIndices.size < assortValues.size)
        }
    }
    override fun reset() {
        selectedIndices.clear()
    }

    override fun sampleCount() = assortValues.sum()
    override fun sampleMean() = assortValues.average()
    override fun N() = N
}


///////////////////////////////////////////////////////////////
/**
 * Welford's algorithm for running mean and variance.
 * see https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm
 */
class Welford(
    var count: Int = 0,      // number of samples
    var mean: Double = 0.0,  // mean accumulates the mean of the entire dataset
    var M2: Double = 0.0,    // M2 aggregates the squared distance from the mean
) {
    // Update with new value
    fun update(new_value: Double) {
        count++
        val delta = new_value - mean
        mean += delta / count
        val delta2 = new_value - mean
        M2 += delta * delta2
    }

    /** Retrieve the current mean, variance and sample variance */
    fun result() : Triple<Double, Double, Double> {
        if (count < 2) return Triple(mean, 0.0, 0.0)
        val variance = M2 / count
        val sample_variance = M2 / (count - 1)
        return Triple(mean, variance, sample_variance)
    }

    override fun toString(): String {
        return "(mean, variance and sample) = ${this.result()}"
    }
}

// generate Bernoulli with probability p
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
