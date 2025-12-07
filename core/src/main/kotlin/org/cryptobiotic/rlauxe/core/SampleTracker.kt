package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford

/** keeps track of the latest sample, number of samples, and the sample sum, mean and variance. */
interface SampleTracker {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
    fun addSample(sample : Double)
    fun reset()
}

/**
 * This ensures that the called function doesnt have access to the current sample,
 * as required by "predictable function of the data X1 , . . . , Xi−1" requirement.
 * Its up to the method using this to make only "previous samples", by not adding the
 * current sample to it until the end of the iteration.
 * TODO CANDIDATE for removal
 */
class PrevSamples : SampleTracker {
    private var last = 0.0
    private var sum = 0.0
    private var welford = Welford()

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)
    }

    override fun reset() {
        last = 0.0
        sum = 0.0
        welford = Welford()
    }
}