package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.betting.SampleTracker
import org.cryptobiotic.rlauxe.util.Welford

/** Simple implementation of SampleTracker */
class SampleTrackerImpl : SampleTracker {
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