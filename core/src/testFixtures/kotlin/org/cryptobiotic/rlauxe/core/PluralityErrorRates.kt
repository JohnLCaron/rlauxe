package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.betting.Tracker
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.sfn

// CANDIDATE for removal
// CLCA assorter errors, where upper = 1 (as in plurality)
data class PluralityErrorRates(val p2o: Double, val p1o: Double, val p1u: Double, val p2u: Double) {
    init {
        require(p2o in 0.0..1.0) {
            "p2o out of range $p2o"
        }
        require(p1o in 0.0..1.0) {"p1o out of range $p1o"}
        require(p1u in 0.0..1.0) {"p1u out of range $p1u"}
        require(p2u in 0.0..1.0) {"p2u out of range $p2u"}
    }
    override fun toString(): String {
        return "[${dfn(p2o, 8)}, ${dfn(p1o, 8)}, ${dfn(p1u, 8)}, ${dfn(p2u, 8)}] sum =${dfn(sum(), 8)}"
    }
    fun toList() = listOf(p2o, p1o, p1u, p2u)
    fun areZero() = (p2o == 0.0 && p1o == 0.0 && p1u == 0.0 && p2u == 0.0)
    fun add(other: PluralityErrorRates): PluralityErrorRates {
        return PluralityErrorRates(p2o + other.p2o, p1o + other.p1o, p1u + other.p1u, p2u + other.p2u)
    }
    fun sum() = toList().sum()

    // assort value -> rate
    fun errorRates(noerror: Double): Map<Double, Double> {
        return mapOf(
            noerror * 0.0 to p2o,
            noerror * 0.5 to p1o,
            noerror * 1.5 to p1u,
            noerror * 2.0 to p2u,
        )
    }

    companion object {
        val Zero =  PluralityErrorRates(0.0, 0.0, 0.0, 0.0)

        fun fromList(list: List<Double>): PluralityErrorRates {
            require(list.size == 4) { "ErrorRates list must have 4 elements"}
            return PluralityErrorRates(list[0], list[1], list[2], list[3])
        }

        fun fromCounts(counts: Map<Double, Int>?, noerror: Double, N:Int): PluralityErrorRates {
            if (counts == null) return PluralityErrorRates.Zero

            val rlist = counts.toList()
            val p2o = rlist.find { doubleIsClose(it.first, 0.0 * noerror)} ?.second ?: 0
            val p1o = rlist.find { doubleIsClose(it.first, 0.5 * noerror) }?.second ?: 0
            val p1u = rlist.find { doubleIsClose(it.first, 1.5 * noerror) }?.second ?: 0
            val p2u = rlist.find { doubleIsClose(it.first, 2.0 * noerror) }?.second ?: 0

            val Nd = N.toDouble()
            return PluralityErrorRates(p2o/Nd, p1o/Nd, p1u/Nd, p2u/Nd)
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * CANDIDATE for removal
 * This counts the under/overstatements for clca plurality audits.
 * @param noerror for comparison assorters who need rate counting. set to 0 for polling
 */
class PluralityErrorTracker(val noerror: Double) : Tracker {
    private val isClca = (noerror > 0.0)
    private var last = 0.0
    private var welford = Welford()
    private var countP0 = 0
    private var countP1o = 0
    private var countP2o = 0
    private var countP1u = 0
    private var countP2u = 0

    override fun numberOfSamples() = welford.count
    override fun sum(): Double = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    fun countP1o() = countP1o
    fun countP2o() = countP2o
    fun countP1u() = countP1u
    fun countP2u() = countP2u

    fun addSample(sample : Double) {
        last = sample
        welford.update(sample)

        if (isClca) {
            if (doubleIsClose(sample, 0.0)) countP2o++
            else if (doubleIsClose(sample, noerror * 0.5)) countP1o++
            else if (doubleIsClose(sample, noerror)) countP0++
            else if (doubleIsClose(sample, noerror * 1.5)) countP1u++
            else if (doubleIsClose(sample, noerror * 2.0)) countP2u++
        }
    }

    fun reset() {
        last = 0.0
        welford = Welford()
        countP0 = 0
        countP1o = 0
        countP2o = 0
        countP2u = 0
        countP1u = 0
    }

    fun errorRates(): Map<Double, Double> {
        return mapOf(
            noerror * 0.0 to countP2o / numberOfSamples().toDouble(),
            noerror * 0.5 to countP1o / numberOfSamples().toDouble(),
            noerror * 1.5 to countP1u / numberOfSamples().toDouble(),
            noerror * 2.0 to countP2u / numberOfSamples().toDouble(),
        )
    }

    fun errorCounts(): Map<Double, Int> {
        return mapOf(
            noerror * 0.0 to countP2o,
            noerror * 0.5 to countP1o,
            noerror * 1.5 to countP1u,
            noerror * 2.0 to countP2u,
        )
    }

    fun pluralityErrorCounts() = listOf(countP0,countP2o,countP1o,countP1u,countP2u)

    // canonical order
    fun pluralityErrorRates(): PluralityErrorRates {
        val n = if (numberOfSamples() > 0) numberOfSamples().toDouble() else 1.0
        val p =  pluralityErrorCounts().map { it / n }
        return PluralityErrorRates(p[1], p[2], p[3], p[4]) // skip p0
    }

    fun pluralityErrorRatesList(): List<Double> {
        val p =  pluralityErrorCounts().map { it / numberOfSamples().toDouble()  /* skip p0 */ }
        return listOf(p[1], p[2], p[3], p[4])
    }
}

