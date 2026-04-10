package org.cryptobiotic.rlauxe.betting

import kotlin.collections.component1
import kotlin.collections.component2

// bassortValue -> rate
data class ClcaErrorRates(val noerror: Double, val upper: Double, val errorRates: Map<Double, Double>) {
    val taus = Taus(upper)

    fun sumRates() = errorRates.map{ it.value }.sum()

    fun getNamedRate(name: String): Double? {
        val tauValue = taus.getNamedValue(name)
        if (tauValue == null) return null
        val bassort = tauValue * noerror
        return errorRates[bassort]
    }

    // is this bassort value the one that a phantom would generate?
    fun isPhantom(bassort: Double): Boolean {
        return taus.isPhantom(bassort / noerror)
    }

    companion object {
        fun empty(noerror: Double, upper: Double) = ClcaErrorRates(noerror, upper, emptyMap())
    }
}

data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) {
    val taus = Taus(upper)

    // errorCounts divided by totalSamples
    fun errorRates(): Map<Double, Double> = errorCounts.mapValues { if (totalSamples == 0) 0.0 else it.value / totalSamples.toDouble() } // bassortValue -> rate
    fun clcaErrorRates() = ClcaErrorRates(noerror, upper, errorRates())
    fun errorCounts() = errorCounts // bassortValue -> count

    fun bassortValues(): List<Double> {
        return taus.values().map { it * noerror }
    }

    // is this bassort value the one that a phantom would generate?
    fun isPhantom(bassort: Double): Boolean {
        return taus.isPhantom(bassort / noerror)
    }

    fun getNamedCount(name: String): Int? {
        val tauValue = taus.getNamedValue(name)
        if (tauValue == null) return null
        val bassort = tauValue * noerror
        return errorCounts[bassort]
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

    /* fun expectedValueLogt(lam: Double): Double {
        val p0 = 1.0 - sumRates()
        val mui = 0.5
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        errorRates().forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }
        val total = noerrorTerm + sumClcaTerm
        return total
    } */

    override fun toString() = buildString {
        appendLine("ClcaErrorCounts(totalSamples=$totalSamples, noerror=$noerror, upper=$upper")
        appendLine("  bassortValues=${bassortValues()}")
        appendLine("    errorCounts=$errorCounts")
    }

    companion object {
        fun empty(noerror: Double, upper: Double) = ClcaErrorCounts(emptyMap(), 0, noerror, upper)
    }
}
