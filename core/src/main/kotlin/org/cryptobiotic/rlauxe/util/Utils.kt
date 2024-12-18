package org.cryptobiotic.rlauxe.util

import kotlin.math.abs

fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}

fun listToMap(vararg names: String): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun listToMap(names: List<String>): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun df(d: Double) = "%6.4f".format(d)
fun dfn(d: Double, n: Int) = "%${n+2}.${n}f".format(d)
fun nfn(i: Int, n: Int) = "%${n}d".format(i)
fun sfn(s: String, n: Int) = "%${n}s".format(s)
