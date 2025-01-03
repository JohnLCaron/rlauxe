package org.cryptobiotic.rlauxe.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

val secureRandom = SecureRandom.getInstanceStrong()!!

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

fun margin2mean(margin: Double) = (margin + 1.0) / 2.0
fun mean2margin(mean: Double) = 2.0 * mean - 1.0

fun df(d: Double) = "%6.4f".format(d)
fun dfn(d: Double, n: Int) = "%${n+2}.${n}f".format(d)
fun nfn(i: Int, n: Int) = "%${n}d".format(i)
fun sfn(s: String, n: Int) = "%${n}s".format(s)

fun Double.sigfig(minSigfigs: Int = 4): String {
    val df = "%.${minSigfigs}G".format(this)
    return if (df.startsWith("0.")) df.substring(1) else df
}

fun quantile(data: List<Int>, quantile: Double): Int {
    if (data.isEmpty())
        return 0
    val p0 = ceil(quantile * data.size).toInt()
    val p = min((quantile * data.size).toInt(), data.size-1)

    val sortedData = mutableListOf<Int>()
    sortedData.addAll(data)
    sortedData.sort()
    return sortedData[p]
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

