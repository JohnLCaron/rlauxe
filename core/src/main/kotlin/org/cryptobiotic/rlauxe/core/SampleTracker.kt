package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose

/** keeps track of the latest sample, number of samples, and the sample sum. */
interface SampleTracker {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
    fun addSample(sample : Double)
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

    override fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)
    }
}

////////////////////////////////////////////////////////////////

interface ClcaErrorRatesIF {
    fun errorRates(): Map<Double, Double>
    fun errorCounts(): Map<Double, Int>
}

class ClcaErrorTracker(val noerror: Double, val debug:Boolean=false) : SampleTracker, ClcaErrorRatesIF {
    private var last = 0.0
    private var sum = 0.0
    private val welford = Welford()

    override fun last() = last
    override fun numberOfSamples() = welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    val valueCounter = mutableMapOf<Double, Int>()
    var noerrorCount = 0

    override fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        if (noerror != 0.0) {
            if (doubleIsClose(sample, noerror)) noerrorCount++ else {
                val counter = valueCounter.getOrPut(sample) { 0 }
                valueCounter[sample] = counter + 1
                if (debug) println("--> error $sample")
            }
        }
    }

    override fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }
    override fun errorCounts() = valueCounter

    override fun toString(): String {
        return "SampleErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}

/**
 * CANDIDATE for removal
 * This also counts the under/overstatements for comparison audits.
 * @param noerror for comparison assorters who need rate counting. set to 0 for polling
 */
class PrevSamplesWithRates(val noerror: Double) : SampleTracker, ClcaErrorRatesIF {
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

    override fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        if (isClca) {
            if (doubleIsClose(sample, 0.0)) countP2o++
            else if (doubleIsClose(sample, noerror * 0.5)) countP1o++
            else if (doubleIsClose(sample, noerror)) countP0++
            else if (doubleIsClose(sample, noerror * 1.5)) countP1u++
            else if (doubleIsClose(sample, noerror * 2.0)) countP2u++
        }
    }

    override fun errorRates(): Map<Double, Double> {
        return mapOf(
            noerror * 0.0 to countP2o / numberOfSamples().toDouble(),
            noerror * 0.5 to countP1o / numberOfSamples().toDouble(),
            noerror * 1.5 to countP1u / numberOfSamples().toDouble(),
            noerror * 2.0 to countP2u / numberOfSamples().toDouble(),
        )
    }

    override fun errorCounts(): Map<Double, Int> {
        return mapOf(
            noerror * 0.0 to countP2o,
            noerror * 0.5 to countP1o,
            noerror * 1.5 to countP1u,
            noerror * 2.0 to countP2u,
        )
    }

    fun clcaErrorCounts() = listOf(countP0,countP2o,countP1o,countP1u,countP2u)

    // canonical order
    fun clcaErrorRates(): ClcaErrorRates {
        val n = if (numberOfSamples() > 0) numberOfSamples().toDouble() else 1.0
        val p =  clcaErrorCounts().map { it / n }
        return ClcaErrorRates(p[1], p[2], p[3], p[4]) // skip p0
    }
    fun errorRatesList(): List<Double> {
        val p =  clcaErrorCounts().map { it / numberOfSamples().toDouble()  /* skip p0 */ }
        return listOf(p[1], p[2], p[3], p[4])
    }
}



