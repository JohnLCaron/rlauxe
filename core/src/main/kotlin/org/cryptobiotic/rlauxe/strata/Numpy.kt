package org.cryptobiotic.rlauxe.strata

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// easier to work with lists if they arent big

fun numpy_linspace(start: Double, end: Double, n: Int): List<Double> {
    val grid_step = (end - start) / (n - 1)
    val result = DoubleArray(n) { start + it * grid_step }
    require(result.last() == end)
    return result.toList()
}

fun numpy_dotDI(A_c: List<Double>, N: List<Int>): Double {
    var sum = 0.0
    repeat(A_c.size) { sum += A_c[it] * N[it] }
    return sum
}

fun numpy_dotDD(a: List<Double>, b: List<Double>): Double {
    var sum = 0.0
    repeat(a.size) { sum += a[it] * b[it] }
    return sum
}

// # Stacking 1D arrays into a 2D array
//result = np.vstack((a, b))
//# Output:
//# [[1, 2, 3],
//#  [4, 5, 6]]
fun numpy_vstack(a1: List<Double>, a2: List<Double>): List<List<Double>> {
    return listOf(a1, a2)
}

// numpy.transpose typically returns a view of the original array rather than a copy, meaning it is a fast operation that shares the same memory.
// we are doing copies here. But "A view is returned whenever possible." so probably dont have to worry about it
fun numpy_transpose(matrix: List<List<Double>>): List<List<Double>> {
    return matrix[0].indices.map { i ->
        matrix.map { it[i] }
    }
}

// 2D Arrays: Standard matrix transpose where rows become columns.
// arange(stop): Values are generated within the half-open interval [0, stop) (in other words, the interval including start but excluding stop).
fun numpy_arange(stop: Int): List<Int> {
    val result = mutableListOf<Int>()
    var current = 0
    while (current < stop) {
        result.add(current)
    }
    return result
}

fun numpy_zeros(n:Int) = DoubleArray(n) { 0.0 }.toList()
fun numpy_ones(n:Int) = DoubleArray(n) { 1.0 }.toList()

// While numpy.random.choice is still widely used, the NumPy Quickstart recommends using the newer Generator.choice method
// for better performance and improved features, such as sampling along specific axes
// np.random.choice(x[k],  len(x[k]), replace = (not WOR))
// TODO I dont see how replace is used; it must be doing a vector, not a scalar ??
fun numpy_randomchoice(x: List<Double>): Double = x[Random.nextInt(x.size)]

// numpy.minimum: Performs an element-wise comparison between two arrays and returns a new array containing the smaller value at each position.
fun numpy_minimum(x1: List<Double>, x2: List<Double>): List<Double> {
    require(x1.size == x2.size)
    return x1.mapIndexed{ idx, x -> min(x, x2[idx]) }
}
fun numpy_minimum(x1: List<Double>, upper: Double) = x1.map{ min(it, upper) }

fun numpy_maximum(x1: List<Double>, x2: List<Double>): List<Double> {
    require(x1.size == x2.size)
    return x1.mapIndexed{ idx, x -> max(x, x2[idx]) }
}
fun numpy_maximum(x1: List<Double>, lower: Double) = x1.map{ max(it, lower) }

//// arrays, turn into List I think?

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