package org.cryptobiotic.rlauxe

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val doublePrecision = 1.0e-8

// def isclose(a, b, rtol=1.e-5, atol=1.e-8, equal_nan=False):
fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}

fun doublesAreClose(a: List<Double>, b: List<Double>, rtol: Double=1.0e-5, atol:Double=1.0e-8) {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    assertEquals(a.size, b.size, "size differs")
    repeat(a.size) { assertTrue(doubleIsClose(a[it], b[it], rtol, atol), "$it: ${a[it]} !~ ${b[it]}") }
}

fun doublesAreClose(a: DoubleArray, b: DoubleArray, rtol: Double=1.0e-5, atol:Double=1.0e-8) {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    assertEquals(a.size, b.size, "size differs")
    repeat(a.size) { assertTrue(doubleIsClose(a[it], b[it], rtol, atol), "$it: ${a[it]} !~ ${b[it]}") }
}

fun doublesAreEqual(a: List<Double>, b: List<Double>) {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    assertEquals(a.size, b.size, "size differs")
    repeat(a.size) { assertEquals(a[it], b[it], "$it: ${a[it]} != ${b[it]}") }
}

class SampleFromArray(val array: DoubleArray) {
    var index = 0
    fun sample() = array[index++]
}

class SampleFromList(val list: List<Double>) {
    var index = 0
    fun sample() = list[index++]
}

fun makeStandardContest() = AuditContest("standard", 0, listOf(0,1), listOf(0))
fun makeStandardPluralityAssorter() = PluralityAssorter(makeStandardContest(), 0, 1)
fun makeStandardComparisonAssorter(avgCvrAssortValue: Double) =
    ComparisonAssorter(makeStandardContest(), makeStandardPluralityAssorter(), avgCvrAssortValue)


fun geometricMean(x: List<Double>): Double {
    if (x.size == 0) return 0.0
    val lnsum = x.filter{it > 0}.map{ ln(it) }.sum()
    return exp( lnsum / x.size )
}