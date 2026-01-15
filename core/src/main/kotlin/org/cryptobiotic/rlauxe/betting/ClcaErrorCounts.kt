package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.core.DebuggingSequences
import org.cryptobiotic.rlauxe.core.SampleTracker
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose

data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) {
    val taus = Taus(upper)

    fun errorRates() : Map<Double, Double> = errorCounts.mapValues { if (totalSamples == 0) 0.0 else it.value / totalSamples.toDouble() }  // bassortValue -> rate
    fun errorCounts() = errorCounts // bassortValue -> count

    fun bassortValues(): List<Double> {
        return taus.values().map { it * noerror }.sorted()
    }

    fun clcaErrorRate(): Double {
        val clcaErrors = errorCounts.toList().filter { (key, value) -> taus.isClcaError(key / noerror) }.sumOf { it.second }
        return clcaErrors / totalSamples.toDouble()
    }

    fun isPhantom(bassort: Double): Boolean {
        val tau = bassort / noerror
        return taus.desc(tau) == "oth-los"
    }

    fun show() = buildString {
        // appendLine("totalSamples=$totalSamples, noerror=$noerror, upper=$upper")
        if (errorCounts.isNotEmpty()) {
            val sorted = errorCounts.toSortedMap()
            append("[")
            sorted.forEach { (bassort, count) ->
                val desc = taus.desc(bassort / noerror)
                if (desc != null) append("$desc=$count, ")
            }
            append("]")
        } else {
            append("no errors")
        }
    }

    override fun toString() = buildString {
        appendLine("ClcaErrorCounts(totalSamples=$totalSamples, noerror=$noerror, upper=$upper")
        appendLine("  bassortValues=${bassortValues()}")
        appendLine("    errorCounts=$errorCounts")
    }

    companion object {
        fun empty(noerror: Double, upper: Double) = ClcaErrorCounts(emptyMap(), 0, noerror, upper)
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
    val u12 = 1.0 / (2 * upper)  // (2 * upper) > 1, u12 < 1
    val name = mapOf(0.0 to "0", u12 to "1/2u", 1 - u12 to "1-1/2u", 1.0 to "noerror", 2 - u12 to "2-1/2u", 1 + u12 to "1+1/2u", 2.0 to "2").toList().sortedBy{ it.first }
    val taus = mapOf(0.0 to "win-los", u12 to "win-oth", 1 - u12 to "oth-los", 1.0 to "noerror", 2 - u12 to "los-oth", 1 + u12 to "oth-win", 2.0 to "los-win").toList().sortedBy{ it.first }

    override fun desc(tau: Double): String? {
        val pair = taus.find { doubleIsClose(it.first, tau) }
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

class ClcaErrorTracker(val noerror: Double, val upper: Double) : SampleTracker {
    val taus = Taus(upper)

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
    var sequences: DebuggingSequences?=null

    fun setDebuggingSequences(sequences: DebuggingSequences) {
        this.sequences = sequences
    }

    override fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        if (noerror != 0.0) {
            if (doubleIsClose(sample, noerror))
                noerrorCount++
            else if (taus.isClcaError(sample / noerror)) {
                val counter = valueCounter.getOrPut(sample) { 0 }
                valueCounter[sample] = counter + 1
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

    fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = valueCounter.toList().filter { (key, value) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, numberOfSamples(), noerror, upper)
    }

    fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    fun errorCounts() = valueCounter.toSortedMap()

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}



