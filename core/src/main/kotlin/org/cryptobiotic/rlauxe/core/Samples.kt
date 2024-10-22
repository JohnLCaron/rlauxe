package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.math.ln
import kotlin.random.Random

/** keeps track of the latest sample, number of samples, and the sample sum. */
interface Samples {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
}

/**
 * This ensures that the called function doesnt have access to the current sample,
 * as required by "predictable function of the data X1 , . . . , Xi−1" requirement.
 * Its up to the method using this to make it "previous samples", by not adding the
 * current sample to it until the end of the iteration.
 */
class PrevSamples : Samples {
    private var last = 0.0
    private var sum = 0.0
    private val welford = Welford()

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)
    }
}

/** This also counts the under/overstatements. */
class PrevSamplesWithRates(val noerror: Double) : Samples {
    private var last = 0.0
    private var sum = 0.0
    private val welford = Welford()
    private var countP0 = 0
    private var countP1 = 0
    private var countP2 = 0
    private var countP3 = 0
    private var countP4 = 0

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    // these are the rates from the previous samples.
    fun sampleP0count() = countP0
    fun sampleP1count() = countP1
    fun sampleP2count() = countP2
    fun sampleP3count() = countP3
    fun sampleP4count() = countP4

    fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        // or just say which overstatement it is?
        if (doubleIsClose(sample, noerror)) countP0++
        if (doubleIsClose(sample, noerror * 0.5)) countP1++
        if (doubleIsClose(sample, 0.0)) countP2++
        if (doubleIsClose(sample, noerror * 1.5)) countP3++
        if (doubleIsClose(sample, noerror * 2.0)) countP4++
    }
}

