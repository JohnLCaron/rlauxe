package org.cryptobiotic.rlauxe.util

import java.security.SecureRandom
import kotlin.enums.EnumEntries
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

val secureRandom = SecureRandom.getInstanceStrong()!!

fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}

fun roundToInt(x: Double) = round(x).toInt()
fun roundUp(x: Double) = ceil(x).toInt()

fun ceilDiv(numerator: Int, denominator: Int): Int {
    val frac = numerator.toDouble() / denominator
    val fracFloor = frac.toInt()
    val fracCeil = if (frac == fracFloor.toDouble()) fracFloor else fracFloor + 1
    return fracCeil
}

fun listToMap(vararg names: String): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun listToMap(names: List<String>): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun margin2mean(margin: Double) = (margin + 1.0) / 2.0
fun mean2margin(mean: Double) = 2.0 * mean - 1.0
fun noerror(margin: Double, upper: Double) = 1.0 / (2.0 - margin / upper)

fun df(d: Double) = "%6.4f".format(d)
fun dfn(d: Double, n: Int) = "%${n+2}.${n}f".format(d)
fun nfn(i: Int, n: Int) = "%${n}d".format(i)
fun sfn(s: String, n: Int) = "%${n}s".format(s)

fun Double.sigfig(minSigfigs: Int = 4): String {
    val df = "%.${minSigfigs}G".format(this)
    return if (df.startsWith("0.")) df.substring(1) else df
}

fun showLast(x: List<Double>, n: Int) = buildString {
    val last = x.takeLast(n)
    last.forEach { append("${df(it)}, ") }
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

fun MutableMap<String, Int>.mergeReduceS(others: List<Map<String, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

fun <T : Enum<T>> enumValueOf(name: String, entries: EnumEntries<T>): T? {
    for (status in entries) {
        if (status.name.lowercase() == name.lowercase()) return status
    }
    return null
}

