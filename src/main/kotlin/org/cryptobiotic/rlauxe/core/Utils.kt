package org.cryptobiotic.rlauxe.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import kotlin.text.appendLine

//// covers for numpy: will be replaced

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

/////////////////////////////////////////////////////////////////////////////////

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

fun randomPermute(samples : DoubleArray): DoubleArray {
    val n = samples.size
    val permutedIndex = MutableList(n) { it }
    permutedIndex.shuffle(Random)
    return DoubleArray(n) { samples[permutedIndex[it]] }
}

interface SampleFn {
    fun sample(): Double
    fun reset()
    fun popMean(): Double
    fun N(): Int
}

class SampleFromArrayWithoutReplacement(val assortValues : DoubleArray): SampleFn {
    val selectedIndices = mutableSetOf<Int>()
    val N = assortValues.size

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(N) // withoutReplacement
            if (!selectedIndices.contains(idx)) {
                selectedIndices.add(idx)
                return assortValues[idx]
            }
            require(selectedIndices.size < assortValues.size)
        }
    }
    override fun reset() {
        selectedIndices.clear()
    }

    override fun popMean() = assortValues.average()
    override fun N() = N
}

class PollWithReplacement(val cvrs : List<Cvr>, val ass: AssorterFunction): SampleFn {
    val N = cvrs.size
    val sampleMean = cvrs.map{ ass.assort(it) }.average()

    override fun sample(): Double {
        val idx = Random.nextInt(N) // withoutReplacement
        return ass.assort(cvrs[idx])
    }

    override fun reset() {
    }

    override fun popMean() = sampleMean
    override fun N() = N
}

class PollWithoutReplacement(val cvrs : List<Cvr>, val ass: AssorterFunction): SampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean = cvrs.map{ ass.assort(it) }.average()
    var idx = 0

    init {
        reset()
    }

    override fun sample(): Double {
        val curr = cvrs[permutedIndex[idx++]]
        return ass.assort(curr)
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun popMean() = sampleMean
    override fun N() = N
}

class CompareWithoutReplacement(val cvrs : List<Cvr>, val cass: ComparisonAssorter): SampleFn {
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cass.assort(it, it)}.average() // TODO seems wrong?
        // sampleMean = cvrs.map { cass.assorter.assort(it, it)}.average() // ??
    }

    override fun sample(): Double {
        val curr = cvrs[permutedIndex[idx++]]
        return cass.assort(curr, curr) // TODO currently identical
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun popMean() = sampleMean
    override fun N() = N
}

/**
 * Welford's algorithm for running mean and variance.
 * see https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm
 */
class Welford(
    var count: Int = 0,      // number of samples
    var mean: Double = 0.0,  // mean accumulates the mean of the entire dataset
    var M2: Double = 0.0,    // M2 aggregates the squared distance from the mean
) {
    // Update with new value
    fun update(new_value: Double) {
        count++
        val delta = new_value - mean
        mean += delta / count
        val delta2 = new_value - mean
        M2 += delta * delta2
    }

    /** Retrieve the current mean, variance and sample variance */
    fun result() : Triple<Double, Double, Double> {
        if (count < 2) return Triple(mean, 0.0, 0.0)
        val variance = M2 / count
        val sample_variance = M2 / (count - 1)
        return Triple(mean, variance, sample_variance)
    }
}

class Bernoulli(p: Double) {
    val log_q = ln(1.0 - p)
    val n = 1.0

    fun get(): Double {
        var x = 0.0
        var sum = 0.0
        while (true) {
            val wtf = ln( Math.random()) / (n - x)
            sum += wtf
            if (sum < log_q) {
                return x
            }
            x++
        }
    }
}

class Histogram(val incr: Int) {
    val hist = mutableMapOf<Int, Int>() // upper bound,count

    fun add(q: Int) {
        var bin = 0
        while (q > bin * incr) bin++
        val currVal = hist.getOrPut(bin) { 0 }
        hist[bin] = (currVal + 1)
    }

    override fun toString() = buildString {
        val shist = hist.toSortedMap()
        shist.forEach { append("${it.key}:${it.value} ") }
    }

    fun toString(keys:List<String>) = buildString {
        hist.forEach { append("${keys[it.key]}:${it.value} ") }
    }

    fun toStringBinned() = buildString {
        val shist = hist.toSortedMap()
        shist.forEach {
            val binNo = it.key
            val binDesc = "[${(binNo-1)*incr}-${binNo*incr}]"
            append("$binDesc:${it.value}; ")
        }
    }

    fun cumul() = buildString {
        val smhist = hist.toSortedMap().toMutableMap()
        var cumul = 0
        smhist.forEach {
            cumul += it.value
            append("${it.key}:${cumul} ")
        }
    }
}
