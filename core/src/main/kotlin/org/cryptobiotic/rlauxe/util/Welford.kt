package org.cryptobiotic.rlauxe.util

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
    fun update(newValue: Double) {
        count++
        val delta = newValue - mean
        mean += delta / count
        val delta2 = newValue - mean
        M2 += delta * delta2
    }

    /** Retrieve the current mean, variance and sample variance */
    fun result() : Triple<Double, Double, Double> {
        if (count < 2) return Triple(mean, 0.0, 0.0)
        val variance = M2 / count
        val sampleVariance = M2 / (count - 1)
        return Triple(mean, variance, sampleVariance)
    }

    // current variance
    fun variance() = if (count == 0) 0.0 else M2 / count

    override fun toString(): String {
        return "(mean, variance and sample) = ${this.result()}"
    }
}