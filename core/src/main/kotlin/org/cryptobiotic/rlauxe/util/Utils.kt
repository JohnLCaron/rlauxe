package org.cryptobiotic.rlauxe.util

import java.security.SecureRandom
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


// find the sample value where percent of samples < that value equals quantile percent
fun quantile(data: List<Int>, quantile: Double): Int {
    require(quantile in 0.0..1.0)
    if (data.isEmpty()) return 0
    if (quantile == 0.0) return 0

    val sortedData = data.sorted()
    if (quantile == 100.0) return sortedData.last()
    // showQuantiles(sortedData)

    // rounding down
    val p = min((quantile * data.size).toInt(), data.size-1)
    return sortedData[p]
}

fun showQuantiles(sortedData: List<Int>) {
    print(" quantiles=[")
    val n = sortedData.size
    repeat(10) {
        val quantile = .10 * (it+1)
        val p = min((quantile * n).toInt(), n-1)
        print(" ${sortedData[p]}, ")
    }
    println("]")
}

/**
 * Normally, Kotlin's `Enum.valueOf` or [enumValueOf] method will throw an exception for an invalid
 * input. This method will instead return `null` if the string doesn't map to a valid value of the enum.
 */
inline fun <reified T : Enum<T>> safeEnumValueOf(name: String?): T? {
    if (name == null) {
        return null
    }

    return try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

fun MutableMap<String, Int>.mergeReduceS(others: List<Map<String, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

