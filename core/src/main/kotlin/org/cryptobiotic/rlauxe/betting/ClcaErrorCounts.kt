package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln

data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) {
    val taus = Taus(upper)

    // errorCounts divided by totalSamples
    fun errorRates() : Map<Double, Double> = errorCounts.mapValues { if (totalSamples == 0) 0.0 else it.value / totalSamples.toDouble() }  // bassortValue -> rate
    fun errorCounts() = errorCounts // bassortValue -> count
    fun sumRates() = errorRates().map{ it.value }.sum()  // hey this includes noerror ??

    fun bassortValues(): List<Double> {
        return taus.values().map { it * noerror }
    }

    // is this bassort value the one that a phantom would generate?
    fun isPhantom(bassort: Double): Boolean {
        return taus.isPhantom(bassort / noerror)
    }

    fun getNamedRate(name: String): Double? {
        val tauValue = taus.getNamedValue(name)
        if (tauValue == null) return null
        val bassort = tauValue * noerror
        val errorCount: Int? = errorCounts[bassort]
        if (errorCount == null) return null
        return if (totalSamples == 0) 0.0 else errorCount / totalSamples.toDouble()
    }

    fun show() = buildString {
        // appendLine("totalSamples=$totalSamples, noerror=$noerror, upper=$upper")
        if (errorCounts.isNotEmpty()) {
            val sorted = errorCounts.toSortedMap()
            append("[")
            sorted.forEach { (bassort, count) ->
                val desc = taus.nameOf(bassort / noerror)
                append("$desc=$count, ")
            }
            append("]")
        } else {
            append("no errors")
        }
    }

    fun expectedValueLogt(lam: Double): Double {
        val p0 = 1.0 - sumRates()
        val mui = 0.5
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        errorRates().forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }
        val total = noerrorTerm + sumClcaTerm
        return total
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

// lightweight ErrorTracker for GeneralAdaptiveBetting.bet(), not a full blown SamplerTracker
class ClcaErrorTracker(val noerror: Double, val upper: Double): ErrorTracker {
    val taus = Taus(upper)

    private var last = 0.0
    private var sum = 0.0
    private var welford = Welford()

    fun last() = last
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

    fun addSample(sample : Double) {
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

    fun reset() {
        last = 0.0
        sum = 0.0
        welford = Welford()
        valueCounter.clear()
        noerrorCount = 0
    }

    override fun noerror() = noerror
    override fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = valueCounter.toList().filter { (key, value) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, numberOfSamples(), noerror, upper)
    }

    fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    fun errorCounts() = valueCounter.toSortedMap()

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}


