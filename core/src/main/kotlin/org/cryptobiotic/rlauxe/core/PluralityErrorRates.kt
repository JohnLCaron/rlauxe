package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sfn

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

class ClcaErrorRatesCumul {
    val avgs = List(4) { Welford() }
    val sums = MutableList(4) { 0.0 }

    fun add(rates: PluralityErrorRates) {
        rates.toList().forEachIndexed { idx, rate ->
            avgs[idx].update(rate)
            sums[idx] = sums[idx] + rate
        }
    }

    fun avgRates(): List<Double> = avgs.map { it.mean }
    fun sumRates(): List<Double> = sums

    override fun toString() = buildString {
        avgRates().forEach { append("${pfn(it, 6)}, ") }
        val sum = avgRates().sum()
        append("${pfn(sum, 6)}")
    }

    companion object {
        val header = "p2o p1o p1u p2u sum"
        fun header() = buildString {
            header.split(" ").forEach { append("${sfn(it, 9)}, ") }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////
// the idea is that the errorRates are proportional to fuzzPct
// Then p1 = fuzzPct * r1, p2 = fuzzPct * r2, p3 = fuzzPct * r3, p4 = fuzzPct * r4.
// margin doesnt matter (TODO show this)

object ClcaErrorTable {
    val rrates = mutableMapOf<Int, List<Double>>() // errorRates / FuzzPct
    val standard = PluralityErrorRates(.01, 1.0e-4, 0.01, 1.0e-4)

    fun getErrorRates(ncandidates: Int, fuzzPct: Double?): PluralityErrorRates {
        if (fuzzPct == null) return standard

        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!.map { it * fuzzPct }
        return PluralityErrorRates(rr[0], rr[1], rr[2], rr[3])
    }

    fun calcErrorRates(contestId: Int,
                       cassorter: ClcaAssorter,
                       cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    ) : PluralityErrorRates {
        require(cvrPairs.size > 0)
        val samples = PluralityErrorTracker(cassorter.noerror()) // accumulate error counts here
        cvrPairs.filter { it.first.hasContest(contestId) }.forEach { samples.addSample(cassorter.bassort(it.first, it.second)) }
        // require( samples.errorCounts().sum() ==  cvrPairs.size)
        return samples.pluralityErrorRates()
    }

    // given an error rate, what fuzz pct does it corresond to ?
    fun calcFuzzPct(ncandidates: Int, errorRates: PluralityErrorRates ) : List<Double> {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!
        // p1 = fuzzPct * r1
        // fuzzPct = p1 / r1
        val p2o = errorRates.p2o / rr[0]
        val p1o = errorRates.p1o / rr[1]
        val p1u = errorRates.p1u / rr[2]
        val p2u = errorRates.p2u / rr[3]
        return listOf(p2o, p1o, p1u, p2u)
    }

    init {
        // GenerateClcaErrorTable.generateErrorTable()
        // N=100000 ntrials = 200
        // generated 1/26/2025
        rrates[2] = listOf(0.2623686, 0.2625469, 0.2371862, 0.2370315,)
        rrates[3] = listOf(0.1400744, 0.3492912, 0.3168304, 0.1245060,)
        rrates[4] = listOf(0.1277999, 0.3913025, 0.3519773, 0.1157800,)
        rrates[5] = listOf(0.0692904, 0.3496153, 0.3077332, 0.0600383,)
        rrates[6] = listOf(0.0553841, 0.3398728, 0.2993941, 0.0473467,)
        rrates[7] = listOf(0.0334778, 0.2815991, 0.2397504, 0.0259392,)
        rrates[8] = listOf(0.0351272, 0.3031122, 0.2591883, 0.0280541,)
        rrates[9] = listOf(0.0308620, 0.3042787, 0.2585768, 0.0254916,)
        rrates[10] = listOf(0.0276966, 0.2946918, 0.2517076, 0.0225628,)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * CANDIDATE for removal
 * This counts the under/overstatements for clca plurality audits.
 * @param noerror for comparison assorters who need rate counting. set to 0 for polling
 */
class PluralityErrorTracker(val noerror: Double) : SampleTracker, ClcaErrorRatesIF {
    private val isClca = (noerror > 0.0)
    private var last = 0.0
    private var sum = 0.0
    private var welford = Welford()
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

    override fun reset() {
        last = 0.0
        sum = 0.0
        welford = Welford()
        countP0 = 0
        countP1o = 0
        countP2o = 0
        countP2u = 0
        countP1u = 0
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

