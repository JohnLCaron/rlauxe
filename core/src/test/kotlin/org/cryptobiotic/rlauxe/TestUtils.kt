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

class SampleFromArray(val array: DoubleArray) {
    var index = 0
    fun sample() = array[index++]
}

class SampleFromList(val list: List<Double>) {
    var index = 0
    fun sample() = list[index++]
}

/*
fun makeStandardContest() = Contest("standard", 0, listToMap("A", "B"), listOf("A"), choiceFunction = SocialChoiceFunction.PLURALITY)
fun makeStandardPluralityAssorter() = PluralityAssorter(makeStandardContest(), 0, 1)
fun makeStandardComparisonAssorter(avgCvrAssortValue: Double) =
    ComparisonAssorter(makeStandardContest(), makeStandardPluralityAssorter(), avgCvrAssortValue)


fun makeStandardContest(ncands: Int): Contest {
    val candidateNames: List<String> = IntArray(ncands).map { "cand$it" }
    return Contest("standard$ncands", 0, candidateNames = listToMap(candidateNames), listOf("cand0"), choiceFunction = SocialChoiceFunction.PLURALITY)
}
fun makeStandardComparisonAssorter(ncands: Int, avgCvrAssortValue: Double) =
    ComparisonAssorter(makeStandardContest(ncands), makeStandardPluralityAssorter(), avgCvrAssortValue)
*/






