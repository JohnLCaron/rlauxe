package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.oneaudit.TausOA
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest

interface ClcaErrorRatesIF {
    fun errorRates(): Map<Double, Double>
    fun errorCounts(): Map<Double, Int>
}

// TODO back out handling OneAudit?
// primitive assorter upper bound, is always > 1/2
class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
    override fun errorRates() = errorCounts.mapValues { if (totalSamples == 0) 0.0 else it.value / totalSamples.toDouble() }
    override fun errorCounts() = errorCounts

    fun bassortValues(poolAvg: Double?=null): List<Double> {
        val tausValues = Taus(upper).values()
        val tausOAvalues = if (poolAvg != null) TausOA(upper, poolAvg).values() else emptyList()
        return (tausValues + tausOAvalues).map { it * noerror }.sorted()
    }

    // TODO what do phantom ballots do to the error counts?
    fun setPhantomRate(phantomRate: Double): ClcaErrorCounts {
        return this // TODO
    }

    fun changedFrom(other: ClcaErrorCounts): Boolean {
        val one = errorCounts.filter{ it.key != noerror }
        val two = other.errorCounts.filter{ it.key != noerror }
        return one != two
    }

    // only the errors from CLCA
    fun clcaErrorRate(): Double {
        val taus = Taus(upper)
        val clcaErrors = errorCounts.toList().filter { (key, value) -> taus.isClcaError(key / noerror) }.sumOf { it.second }
        return clcaErrors / totalSamples.toDouble()
    }

    fun show(poolAvg: Double?=null) = buildString {
        appendLine("totalSamples=$totalSamples, noerror=$noerror, upper=$upper poolAvg=$poolAvg")

        if (errorCounts.isNotEmpty()) {
            // appendLine(errorCounts.toList().associate { Pair(it.first / noerror, it.second) }.toSortedMap())

            val taus = Taus(upper)
            val tausOA = if (poolAvg != null) TausOA(upper, poolAvg) else null
            //appendLine(" cvr taus = $taus")
            //appendLine("pool taus = $tausOA")

            val sorted = errorCounts.toSortedMap()
            append("    cvr counts= [")
            sorted.forEach { (bassort, count) ->
                val desc = taus.desc(bassort / noerror)
                if (desc != null) append("$desc=$count, ")
            }
            appendLine("]")

            if (tausOA != null) {
                append("    pool counts= [")
                sorted.forEach { (bassort, count) ->
                    var desc = tausOA.desc(bassort / noerror)
                    if (desc != null) append("$desc=$count, ") // else append("${df(bassort / noerror)}=$count, ")
                }
                appendLine("]")
            }
        }
    }

    fun showShort(poolAvg: Double?=null) = buildString {
        val taus = Taus(upper)
        val tausOA = if (poolAvg != null) TausOA(upper, poolAvg) else null

        val sorted = errorCounts.toSortedMap()
        append("[")
        sorted.forEach { (bassort, count) ->
            val desc = taus.desc(bassort / noerror)
            if (desc != null) append("$desc=$count, ") else if (tausOA != null) {
                var desc = tausOA.desc(bassort / noerror)
                if (desc != null) append("$desc=$count, ") else append("${df(bassort / noerror)}=$count, ")
            } else {
                append("${df(bassort / noerror)}=$count, ")
            }
        }
        append("]")
    }

    override fun toString(): String {
        return "ClcaErrorCounts(errorCounts=$errorCounts, totalSamples=$totalSamples, noerror=$noerror, upper=$upper, bassortValues=${bassortValues()})"
    }

    companion object {

         // this only works for upper=1
        fun fromPluralityErrorRates(prates: PluralityErrorRates, noerror: Double, totalSamples: Int, upper: Double): ClcaErrorCounts {
             if (upper != 1.0) throw RuntimeException("fromPluralityErrorRates must have upper = 1")

             val errorRates = prates.errorRates(noerror)
             val errorCounts = errorRates.mapValues { roundToClosest(it.value * totalSamples) }

            // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
            return ClcaErrorCounts(errorCounts, totalSamples, noerror, upper)
        }

        // this only works for upper=1
        fun fromPluralityAndPrevRates(prates: PluralityErrorRates, prev: ClcaErrorCounts): ClcaErrorCounts {
            if (prev.upper != 1.0) throw RuntimeException("fromPluralityAndPrevRates must have upper = 1")
            val errorRates = prates.errorRates(prev.noerror)
            val errorCounts = errorRates.mapValues { roundToClosest(it.value * prev.totalSamples) }

            // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
            return ClcaErrorCounts(errorCounts, prev.totalSamples, prev.noerror, prev.upper)
        }
    }
}

// B(bi, ci) = (1-o/u)/(2-v/u) = (1-o/u) * noerror, where
//                o is the overstatement = (cvr_assort - mvr_assort)
//                u is the upper bound on the value the assorter assigns to any ballot
//                v is the assorter margin
//                noerror = 1/(2-v/u) == B(bi, ci) when o = 0 (no error)

// assort in [0, .5, u], u > .5, so overstatementError in
//      [-1, -.5, 0, .5, 1] (plurality)
//      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
//      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)

// so bassort in
// [1+(u-l)/u, 1+(.5-l)/u, 1+(u-.5)/u,  1, 1-(.5-l)/u, 1-(u-.5)/u, 1-(u-l)/u] * noerror
// [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
// [2, 1.5,  1, .5, 0] * noerror (l==0, u==1)

fun computeBassortValues(noerror: Double, upper: Double): List<Double> {
    // p2o, p1o, ? noerror, ?, p1u, p2u
    // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
    val u12 = 1.0 / (2 * upper)
    val taus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
    return taus.map { it * noerror }.toSet().toList().sorted()
}

interface TausIF {
    fun desc(tau: Double): String?
    fun values(): List<Double>
}

// o = cvr_assort - mvr_assort when l = 0:
// [0, .5, u] - [0, .5, u] = 0, -.5, -u
//                         = .5,  0, .5-u
//                         = u, u-.5, 0
// u, u-.5, .5,  0, .5-u, -.5, -u = (cvr - mvr) = (u-0), (u-.5), (.5-0), (cvr==mvr), (.5-u), (0-.5), (0-u)

// (u-0),       cvr has vote for winner, mvr has vote for loser : p2o = 2 vote overstatement
// (u-.5),      cvr has vote for winner, mvr has vote for other : p1o = 1 vote overstatement
// (.5-0),      cvr has vote for other, mvr has vote for loser : p1o = 1 vote overstatement
// (cvr==mvr),  no error
// (.5-u),      cvr has vote for other, mvr has vote for winner : p1u = 1 vote understatement
// (0-.5),      cvr has vote for loser, mvr has vote for other  : p1u = 1 vote understatement
// (0-u)        cvr has vote for loser, mvr has vote for winner : p2u = 2 vote understatement

//      winner-loser expect 0.0000 actual 0.0000 tau='     0' (p2o)
//     winner-other expect 0.2857 actual 0.2857 tau='  1/2u' (p1o)
//      other-loser expect 0.7143 actual 0.7143 tau='1-1/2u' (p1o)
//    winner-winner expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      other-other expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      loser-loser expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      loser-other expect 1.2857 actual 1.2857 tau='1+1/2u' (p1u)
//     other-winner expect 1.7143 actual 1.7143 tau='2-1/2u' (p1u)
//     loser-winner expect 2.0000 actual 2.0000 tau='     2' (p2u)
class Taus(upper: Double): TausIF {
    // p2o, p1o, ? noerror, ?, p1u, p2u
    // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
    val u12 = 1.0 / (2 * upper)
    val name = mapOf(0.0 to "0", u12 to "1/2u", 1 - u12 to "1-1/2u", 1.0 to "noerror", 2 - u12 to "2-1/2u", 1 + u12 to "1+1/2u", 2.0 to "2").toList().sortedBy{ it.first }
    val taus = mapOf(0.0 to "win-los", u12 to "win-oth", 1 - u12 to "oth-los", 1.0 to "noerror", 2 - u12 to "los-oth", 1 + u12 to "oth-win", 2.0 to "los-win").toList().sortedBy{ it.first }

    override fun desc(want: Double): String? {
        val pair = taus.find { doubleIsClose(it.first, want) }
        return pair?.second
    }

    fun isClcaError(tau: Double): Boolean {
        val pair = name.find { doubleIsClose(it.first, tau) }
        return (pair != null) && (pair.second != "noerror")
    }

    fun name(want: Double) : String {
        val pair = name.find { doubleIsClose(it.first, want) }
        return pair?.second ?: "N/A"
    }

    override fun values() = taus.map { it.first }.toList()

    override fun toString(): String {
        return taus.toString()
    }
}

class ClcaErrorTracker(val noerror: Double, val upper: Double, val sequences: DebuggingSequences?=null, val debug:Boolean=false) : SampleTracker, ClcaErrorRatesIF {
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

    // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
    fun measuredErrorCounts(): ClcaErrorCounts {
        return ClcaErrorCounts(valueCounter.toSortedMap(), numberOfSamples(), noerror, upper)
    }

    fun measuredAllCounts(): Map<Double, Int> {
        val complete = (valueCounter.toList() + Pair(noerror, noerrorCount)).toMap()
        return complete.toSortedMap()
    }

    override fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    override fun errorCounts() = valueCounter.toSortedMap()

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}



