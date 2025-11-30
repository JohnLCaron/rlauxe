package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest

interface ClcaErrorRatesIF {
    fun errorRates(): Map<Double, Double>
    fun errorCounts(): Map<Double, Int>
}

// primitive assorter upper bound, is always > 1/2
data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
    override fun errorRates() = errorCounts.mapValues { it.value / totalSamples.toDouble() }
    override fun errorCounts() = errorCounts

    val bassortValues: List<Double> = computeBassortValues(noerror, upper)

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

// B(bi, ci) = (1-o/u)/(2-v/u), where
//                o is the overstatement
//                u is the upper bound on the value the assorter assigns to any ballot
//                v is the assorter margin

// assort in [0, .5, u], u > .5, so overstatementError in
//      [-1, -.5, 0, .5, 1] (plurality)
//      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
//      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)

// so bassort in
// [1+(u-l)/u, 1+(.5-l)/u, 1+(u-.5)/u,  1, 1-(.5-l)/u, 1-(u-.5)/u, 1-(u-l)/u] * noerror
// [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)

fun computeBassortValues(noerror: Double, upper: Double): List<Double> {
    // p2o, p1o, ? noerror, ?, p1u, p2u
    // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
    val u12 = 1.0 / (2 * upper)
    val taus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
    return taus.map { it * noerror }.toSet().toList().sorted()
}

class ClcaErrorTracker(val noerror: Double, val debug:Boolean=false) : SampleTracker, ClcaErrorRatesIF {
    private var last = 0.0
    private var sum = 0.0
    private var welford = Welford()

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
            if (doubleIsClose(sample, noerror))
                noerrorCount++
            else {
                val counter = valueCounter.getOrPut(sample) { 0 }
                valueCounter[sample] = counter + 1
                if (debug) println("--> error $sample")
            }
        }
    }

    override fun reset() {
        last = 0.0
        sum = 0.0
        welford = Welford()
        valueCounter.clear()
        noerrorCount = 0
    }

    override fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }
    override fun errorCounts() = valueCounter

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}



