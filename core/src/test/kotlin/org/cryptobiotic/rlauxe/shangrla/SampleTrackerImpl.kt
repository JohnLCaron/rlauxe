package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.betting.Tracker
import org.cryptobiotic.rlauxe.util.Welford

// CANDIDATE FOR REMOVAL

interface SampleTracker {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
    fun addSample(sample : Double)
    fun reset()
}

/** Simple implementation of SampleTracker */
class SampleTrackerImpl : SampleTracker, Tracker {
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