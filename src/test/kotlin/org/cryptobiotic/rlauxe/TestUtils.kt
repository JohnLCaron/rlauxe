package org.cryptobiotic.rlauxe

import kotlin.math.abs
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

class SampleFromList(val list: DoubleArray) {
    var index = 0
    fun sample() = list[index++]
}