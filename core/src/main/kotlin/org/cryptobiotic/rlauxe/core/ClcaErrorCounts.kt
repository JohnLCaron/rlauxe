package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest

interface ClcaErrorRatesIF {
    fun errorRates(): Map<Double, Double>
    fun errorCounts(): Map<Double, Int>
}

data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
    override fun errorRates() = errorCounts.mapValues { it.value / totalSamples.toDouble() }
    override fun errorCounts() = errorCounts

    val bassortValues: List<Double>
    init {
        // p2o, p1o, ? noerror, ?, p1u, p2u
        // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
        val u12 = 1.0 / (2 * upper)
        val taus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
        bassortValues = taus.map { it * noerror }.toSet().toList().sorted()
        // println("  bassortValues = ${bassortValues}")
    }

    // assume phantoms cause p1 errors
    fun setPhantomRate(phantoms: Double): ClcaErrorCounts {
        return this // TODO
    }

    fun toPluralityErrorRates() = PluralityErrorRates.fromCounts(errorCounts, noerror, totalSamples)

    companion object {

        fun fromPluralityErrorRates(prates: PluralityErrorRates, noerror: Double, totalSamples: Int, upper: Double): ClcaErrorCounts {
            val errorRates = prates.errorRates(noerror)
            val errorCounts = errorRates.mapValues { roundToClosest(it.value * totalSamples) }

            // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
            return ClcaErrorCounts(errorCounts, totalSamples, noerror, upper)
        }
    }
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



