package org.cryptobiotic.rlauxe.util

import java.security.SecureRandom
import kotlin.math.abs
import kotlin.random.Random

val secureRandom = SecureRandom.getInstanceStrong()!!

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

/////////////////////////////////////////////////////////////////////////////
// covers for numpy: will be replaced

// def arange(start=None, *args, **kwargs):
// arange([start,] stop[, step,], dtype=None, *, like=None)
// Return evenly spaced values within a given interval.
fun numpy_arange(start: Int, stop: Int, step: Int): IntArray {
    var size = (stop - start) / step
    if (step * size != (stop - start)) size++
    return IntArray(size) { start + step * it}
}

// Return the cumulative product of elements
fun np_cumprod(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] * a[i]
    }
    return result
}

// Return the cumulative product of elements
fun np_cumsum(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] + a[i]
    }
    return result
}

// Return an array of ones with the same shape and type as a given array.
fun numpy_ones_like(a: DoubleArray): DoubleArray {
    return DoubleArray(a.size) { 1.0 }
}

// Return the cumulative product of elements
fun numpy_repeat(a: DoubleArray, nrepeat: Int) : DoubleArray {
    val result = DoubleArray(a.size * nrepeat )
    var start = 0
    a.forEach { elem ->
        repeat(nrepeat) { result[start + it] = elem }
        start += nrepeat
    }
    return result
}

// Returns the indices of the maximum values along an axis.
// TODO what happens if theres a tie? Should return a list?
fun numpy_argmax(a: List<Double>) : Int {
    var max = Double.MIN_VALUE
    var maxIdx = -1
    a.forEachIndexed { idx, it ->
        if (it > max) {
            maxIdx = idx
            max = it
        }
    }
    return maxIdx
}

// Returns the first index thats true. Dont know why
fun indexFirstTrue(a: List<Boolean>) : Int {
    return a.indexOfFirst { it }
}

fun numpy_append(pfx: DoubleArray, a: DoubleArray) : DoubleArray {
    val n = pfx.size
    return DoubleArray(pfx.size + a.size) { if (it<n) pfx[it] else a[it-n] }
}

// computes the q-th quantile of data along the specified axis.
// The q-th quantile represents the value below which q percent of the data falls.
// i think a has to be sorted
fun numpy_quantile(a: IntArray, q: Double): Int {
    // for (i=0, sum=0; i<n; i++) sum += Number[i];
    //tot = sum;
    //for (i=0, sum=0; i<n && sum < 0.95*tot; i++) sum += Number[i];
    //// i is about it
    val total = a.sum() * q
    var i = 0
    var runningTotal = 0
    while ( runningTotal < total) {
        runningTotal += a[i++]
    }
    return a[i]
}

// this one assumes you cant change data array
// https://softwareengineering.stackexchange.com/questions/195652/how-to-calculate-percentile-in-java-without-using-library/453902
fun quantile(data: List<Int>, quantile: Double): Int {

    require (quantile in 0.0..1.0)
    val total = data.sum() * quantile

    val sortedData = mutableListOf<Int>()
    sortedData.addAll(data) // or sort in place, which changes data
    sortedData.sort()

    var i = 0
    var runningTotal = 0
    while (runningTotal < total) {
        runningTotal += sortedData[i++]
    }
    return sortedData[i]
}
