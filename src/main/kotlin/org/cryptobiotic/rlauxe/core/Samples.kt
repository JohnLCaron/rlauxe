package org.cryptobiotic.rlauxe.core

import kotlin.math.ln
import kotlin.random.Random

// keeps track of the latest sample and the current and previous sample sum.
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

interface SampleFn {
    fun sample(): Double // get next in sample
    fun reset()          // start over again with different permutation
    fun truePopulationMean(): Double // for simulations
    fun truePopulationCount(): Double
    fun N(): Int  // population size
}

class SampleFnFromArray(val assortValues : DoubleArray): SampleFn {
    var index = 0

    override fun sample(): Double {
        return assortValues[index++]
    }

    override fun reset() {
        index = 0
    }

    override fun truePopulationMean(): Double {
        return assortValues.toList().average()
    }

    override fun truePopulationCount(): Double {
        return assortValues.toList().sum()
    }

    override fun N(): Int {
        return assortValues.size
    }

}

class SampleFromArrayWithoutReplacement(val assortValues : DoubleArray): SampleFn {
    val selectedIndices = mutableSetOf<Int>()
    val N = assortValues.size

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(N) // withoutReplacement
            if (!selectedIndices.contains(idx)) {
                selectedIndices.add(idx)
                return assortValues[idx]
            }
            require(selectedIndices.size < assortValues.size)
        }
    }
    override fun reset() {
        selectedIndices.clear()
    }

    override fun truePopulationCount() = assortValues.sum()
    override fun truePopulationMean() = assortValues.average()
    override fun N() = N
}

class PollWithReplacement(val cvrs : List<Cvr>, val ass: AssorterFunction): SampleFn {
    val N = cvrs.size
    val sampleMean = cvrs.map{ ass.assort(it) }.average()
    val sampleCount = cvrs.map{ ass.assort(it) }.sum()

    override fun sample(): Double {
        val idx = Random.nextInt(N) // withoutReplacement
        return ass.assort(cvrs[idx])
    }

    override fun reset() {
    }

    override fun truePopulationMean() = sampleMean
    override fun truePopulationCount() = sampleCount
    override fun N() = N
}

class PollWithoutReplacement(val cvrs : List<Cvr>, val ass: AssorterFunction): SampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    var idx = 0

    init {
        reset()
    }

    override fun sample(): Double {
        val curr = cvrs[permutedIndex[idx++]]
        return ass.assort(curr)
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun truePopulationMean() = cvrs.map{ ass.assort(it) }.average()
    override fun truePopulationCount() = cvrs.map{
        ass.assort(it)
    }.sum()

    override fun N() = N
}


class CompareWithoutReplacement(val cvrs : List<Cvr>, val cass: ComparisonAssorter): SampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cass.assort(it, it)}.average()
        sampleCount = cvrs.map { cass.assort(it, it)}.sum()
    }

    override fun sample(): Double {
        val curr = cvrs[permutedIndex[idx++]]
        return cass.assort(curr, curr) // TODO mcr == cvr, no errors
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun truePopulationMean() = sampleMean
    override fun truePopulationCount() = sampleCount
    override fun N() = N
}

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