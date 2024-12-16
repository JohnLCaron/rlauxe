package org.cryptobiotic.rlauxe.util

import kotlin.math.abs
import kotlin.math.ln

fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}

fun findFirstIndex(x: DoubleArray, pred: (Double) -> Boolean): Int {
    var firstIdx = -1
    for (idx in 0 until x.size) {
        if (pred(x[idx])) {
            firstIdx = idx
            break
        }
    }
    return firstIdx
}

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

fun df(d: Double) = "%6.4f".format(d)
fun dfn(d: Double, n: Int) = "%${n+2}.${n}f".format(d)
fun nfn(i: Int, n: Int) = "%${n}d".format(i)
fun sf(s: String, n: Int) = "%${n}s".format(s)

// https://www.baeldung.com/cs/sampling-exponential-distribution
// probability density function (PDF): f_lambda(x) = lambda * e^(-lambda * x)
// cumulative density function (CDF): F_lambda(x) =  1 - e^(-lambda * x)
// inverse cumulative density function (CDF): F_lambda^(-1)(u) = -(1/lambda) * ln(1-u)
// sample in [0, 1] with exponential distribution with decay lambda
class Exponential(lambda: Double) {
    val ilambda = 1.0 / lambda
    val n = 1.0

    fun next(): Double {
        val u = Math.random()
        val x = ilambda * ln(1.0-u)
        return x
    }
}

// this one assumes you cant change data array
// https://softwareengineering.stackexchange.com/questions/195652/how-to-calculate-percentile-in-java-without-using-library/453902
fun quantile(data: List<Int>, quantile: Double): Int {
    if (data.isEmpty())
        return 0

    require (quantile in 0.0..1.0)
    val total = data.sum() * quantile

    val sortedData = mutableListOf<Int>()
    sortedData.addAll(data) // or sort in place, which changes data
    sortedData.sort()

    var i = -1
    var runningTotal = 0
    while (runningTotal < total) {
        i++
        runningTotal += sortedData[i]
    }
    return sortedData[i]
}

fun cumul(data: List<Int>, value: Int): Double {
    if (data.isEmpty())
        return 0.0

    val sortedData = mutableListOf<Int>()
    sortedData.addAll(data)
    sortedData.sort()

    var i = -1
    var runningValue = 0
    while (runningValue < value && i < sortedData.size - 1) {
        i++
        runningValue = sortedData[i]
    }
    return 100.0 * i / data.size
}