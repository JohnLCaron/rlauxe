package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose

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



