package org.cryptobiotic.rlauxe

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import kotlin.math.abs

val doublePrecision = 1.0e-8

// def isclose(a, b, rtol=1.e-5, atol=1.e-8, equal_nan=False):
fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}

fun makeStandardContest() = AuditContest("standard", 0, listOf(0,1), listOf(0))
fun makeStandardPluralityAssorter() = PluralityAssorter(makeStandardContest(), 0, 1)
fun makeStandardComparisonAssorter(avgCvrAssortValue: Double) =
    ComparisonAssorter(makeStandardContest(), makeStandardPluralityAssorter(), avgCvrAssortValue)