package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose

/** keeps track of the latest sample, number of samples, and the sample sum. */
interface SampleTracker {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
    fun errorRates(): ClcaErrorRates   // only for clca
}

/**
 * This ensures that the called function doesnt have access to the current sample,
 * as required by "predictable function of the data X1 , . . . , Xi−1" requirement.
 * Its up to the method using this to make only "previous samples", by not adding the
 * current sample to it until the end of the iteration.
 */
class PrevSamples : SampleTracker {
    private var last = 0.0
    private var sum = 0.0
    private val welford = Welford()

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()
    override fun errorRates() = ClcaErrorRates(0.0, 0.0, 0.0, 0.0, )

    fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)
    }
}

/**
 * This also counts the under/overstatements for comparison audits.
 * @param noerror for comparison assorters who need rate counting. set to 0 for polling
 */
class PrevSamplesWithRates(val noerror: Double) : SampleTracker {
    private val isClca = (noerror > 0.0)
    private var last = 0.0
    private var sum = 0.0
    private val welford = Welford()
    private var countP0 = 0
    private var countP1o = 0
    private var countP2o = 0
    private var countP1u = 0
    private var countP2u = 0

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    fun countP1o() = countP1o
    fun countP2o() = countP2o
    fun countP1u() = countP1u
    fun countP2u() = countP2u

    fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        if (isClca) {
            if (doubleIsClose(sample, 0.0)) countP2o++
            else if (doubleIsClose(sample, noerror * 0.5)) countP1o++
            else if (doubleIsClose(sample, noerror)) countP0++
            else if (doubleIsClose(sample, noerror * 1.5)) countP1u++
            else if (doubleIsClose(sample, noerror * 2.0)) countP2u++
            else println(" isClca not assigned ${df(sample / noerror)}")
        }
    }

    fun errorCounts() = listOf(countP0,countP2o,countP1o,countP1u,countP2u) // canonical order
    override fun errorRates(): ClcaErrorRates {
        val p =  errorCounts().map { it / numberOfSamples().toDouble()  /* skip p0 */ }
        return ClcaErrorRates(p[1], p[2], p[3], p[4])
    }
    fun errorRatesList(): List<Double> {
        val p =  errorCounts().map { it / numberOfSamples().toDouble()  /* skip p0 */ }
        return listOf(p[1], p[2], p[3], p[4])
    }
}

