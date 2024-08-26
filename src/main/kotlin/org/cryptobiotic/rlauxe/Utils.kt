package org.cryptobiotic.rlauxe

import kotlin.math.abs
import kotlin.random.Random

// def arange(start=None, *args, **kwargs):
// arange([start,] stop[, step,], dtype=None, *, like=None)
// Return evenly spaced values within a given interval.
fun numpy_arange(start: Int, stop: Int, step: Int): IntArray {
    var size = (stop - start) / step
    if (step * size != (stop - start)) size++
    return IntArray(size) { start + step * it}
}

// Return the cumulative product of elements
fun numpy_cumprod(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] * a[i]
    }
    return result
}

// Return the cumulative product of elements
fun numpy_cumsum(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] + a[i]
    }
    return result
}

// def isclose(a, b, rtol=1.e-5, atol=1.e-8, equal_nan=False):
fun numpy_isclose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
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
// TODO what happens if theres a tie? Should return a list
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
fun numpy_quantile2(data: IntArray, quantile: Double): Int {

    require (quantile in 0.0..1.0)
    val total = data.sum() * quantile

    val sortedData = data.copyOf() // or sort in place, which changes data
    sortedData.sort()

    var i = 0
    var runningTotal = 0
    while (runningTotal < total) {
        runningTotal += sortedData[i++]
    }
    return sortedData[i]
}

fun randomShuffle(samples : DoubleArray): DoubleArray {
    val n = samples.size
    val permutedIndex = MutableList(n) { it }
    permutedIndex.shuffle(Random)
    return DoubleArray(n) { samples[permutedIndex[it]] }
}

interface SampleFn {
    fun sample(): Double
    fun reset()
    fun sampleMean(): Double
    fun N(): Int
}

class SampleFromArrayWithoutReplacement(val samples : DoubleArray): SampleFn {
    val selectedIndices = mutableSetOf<Int>()
    val N = samples.size

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(N) // withoutReplacement
            if (!selectedIndices.contains(idx)) {
                selectedIndices.add(idx)
                return samples[idx]
            }
            require(selectedIndices.size < samples.size)
        }
    }
    override fun reset() {
        selectedIndices.clear()
    }

    override fun sampleMean() = samples.average()
    override fun N() = N
}

class Welford() {
    var count = 0
    var mean = 0.0 // mean accumulates the mean of the entire dataset
    var M2 = 0.0 // M2 aggregates the squared distance from the mean

    // For a new value new_value, compute the new count, new mean, the new M2.
    fun update(new_value: Double) {
        count++
        val delta = new_value - mean
        mean += delta / count
        val delta2 = new_value - mean
        M2 += delta * delta2
    }

    // Retrieve the mean, variance and sample variance from an aggregate
    fun result() : Triple<Double, Double, Double> {
        if (count < 2) return Triple(mean, Double.NaN, Double.NaN)
        val variance = M2 / count
        val sample_variance = M2 / (count - 1)
        return Triple(mean, variance, sample_variance)
    }
}
