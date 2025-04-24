package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestWelford {
    val sample = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0)

    @Test
    fun testWelford() {
        val (pm, pv) = w2(sample)

        println("Welford")
        val welford = Welford()
        sample.forEachIndexed { idx, it ->
            welford.update(it)
            val (wm, wv, _) = welford.result()
            println(" $idx = ${welford.result()}")
            assertEquals(pm[idx], wm)
            assertEquals(pv[idx], wv)
        }

        val (wm, wv, swv) = welford.result()
        println("mean = ${wm}")
        assertEquals(0.6, wm)
        println("variance = ${wv}")
        assertEquals(0.24, wv)
        assertEquals(0.24, welford.variance())
        println("sample variance = ${swv}")
        assertEquals(0.26666666666666666, swv)
        println("stddev = ${sqrt(wv)}")

        assertContains(welford.toString(), "(0.6, 0.24, 0.26666666666666666)")
    }

    @Test
    fun testWelfordSL() {
        val (pm, pv) = SLwelford(sample)
        val last = sample.size - 1

        assertEquals(sample.average(), pm[last])
        assertEquals(sample.variance(), pv[last])

        val welford = Welford()
        sample.forEachIndexed { idx, it ->
            welford.update(it)
        }
        val (wm, wv, _) = welford.result()
        assertEquals(sample.average(), wm)
        assertEquals(sample.variance(), wv)
    }

    @Test
    fun testWelfordWeighted() {
        val welford = Welford()
        repeat(100) { welford.update(1.0) }
        repeat(10) { welford.update(2.0) }
        println("welford = $welford")

        val welfordW = Welford()
        welfordW.update(1.0, 100)
        welfordW.update(2.0, 10)
        println("welfordW = $welfordW")

        val (wm, wv, _) = welfordW.result()
        assertEquals((120.0 / 110), wm)

        assertEquals(welford.mean, welfordW.mean, doublePrecision)
        assertEquals(welford.M2, welfordW.M2, doublePrecision)
        assertEquals(welford.count, welfordW.count)
    }

    @Test
    fun testWelfordRandom() {
        val b = Bernoulli(.45)
        val sample = DoubleArray(10) { b.get() }
        w2(sample)
    }

    @Test
    fun testWelfordMeanVar() {
        val (pm, pv) = SLwelford(sample)
        val last = sample.size - 1

        assertEquals(sample.average(), pm[last])
        assertEquals(sample.variance(), pv[last])

        val (pm2, pv2) = welfordMeanVar(sample)
        assertEquals(sample.average(), pm2[last])
        assertEquals(sample.variance(), pv2[last])

        pm.forEachIndexed { idx, it ->
            assertEquals(it, pm2[idx])
        }
        pv.forEachIndexed { idx, it ->
            assertEquals(it, pv2[idx])
        }

        val welford = Welford()
        sample.forEachIndexed { idx, it ->
            welford.update(it)
            val (wm, wv, _) = welford.result()
            assertEquals(wm, pm2[idx])
            assertEquals(wv, pv2[idx])
        }
    }

    @Test
    fun testWelfordMeanVar2() {
        val x = listOf(0.75, 0.9, 0.9, 0.9, 0.75, 0.9, 0.9, 0.9, 0.9, 0.9)
        val welford = Welford()

        val means = mutableListOf<Double>()
        val stdev = mutableListOf<Double>()
        x.forEachIndexed { idx, it ->
            welford.update(it)
            val (wm, wv, _) = welford.result()
            means.add(wm)
            stdev.add(sqrt(wv))
        }
        println("means = ${means}")
        assertEquals(0.87, means.last(), doublePrecision)
        println("stdev = ${stdev}")
        assertEquals(0.06, stdev.last(), doublePrecision)
    }


}

// this is the original code from SHANGRLA
fun w2(x: DoubleArray): Pair<DoubleArray, DoubleArray> {
    // Welford's algorithm for running mean and running sd
    val mj = mutableListOf<Double>()
    mj.add(x[0])
    var sdj = mutableListOf<Double>()
    sdj.add(0.0)

    //        for i, xj in enumerate(x[1:]):
    //            mj.append(mj[-1] + (xj - mj[-1]) / (i + 1))
    //            sdj.append(sdj[-1] + (xj - mj[-2]) * (xj - mj[-1]))
    // enumerate returns Pair(index, element)
    for (idx in 0 until x.size - 1) {
        val xj = x[idx + 1]
        // SHANGRLA python caught this at PR #89
        // mj.append(mj[-1] + (xj - mj[-1]) / (i + 1)) // BUG
        mj.add(mj.last() + (xj - mj.last()) / (idx + 2))
        // sdj.append(sdj[-1] + (xj - mj[-2]) * (xj - mj[-1]))
        sdj.add(sdj.last() + (xj - mj[mj.size - 2]) * (xj - mj.last()))
    }
    // sdj = np.sqrt(sdj / j)
    val sdj2 = sdj.mapIndexed { idx, it -> (it / (idx + 1)) }
    // end of Welford's algorithm.

    println("sample = ${x.contentToString()}")
    println("w2 running mean = ${mj}")
    println("w2 running variance = ${sdj2}")

    println("mean = ${x.average()}")
    println("variance = ${x.variance()}")
    println("stddev = ${sqrt(x.variance())}")

    val last = x.size - 1
    assertEquals(x.average(), mj[last], doublePrecision)
    assertEquals(x.variance(), sdj2[last], doublePrecision)
    return Pair(mj.toDoubleArray(), sdj2.toDoubleArray())
}

fun DoubleArray.variance(): Double {
    val mean = this.average()
    val variance = this.map { (it - mean) * (it - mean) }.average()
    return variance
}

// SHANGRLA python
// def welford_mean_var(x: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
//    """
//    Welford's algorithm for running mean and variance
//    """
//    m = [x[0]]
//    v = [0]
//    for i, xi in enumerate(x[1:]):
//        m.append(m[-1] + (xi - m[-1]) / (i + 2))
//        v.append(v[-1] + (xi - m[-2]) * (xi - m[-1]))
//    v = v / np.arange(1, len(x) + 1)
//    return np.array(m), v
//
// mj=array([1. , 1. , 1. , 1.  , 1. , 1. , 0.85714286, 0.75 , 0.66666667, 0.6])
// sdj=array([0.        , 0.        , 0.        , 0.        , 0.        ,
//       0.        , 0.12244898, 0.1875    , 0.22222222, 0.24      ])

fun SLwelford(x: DoubleArray): Pair<DoubleArray, DoubleArray> {
    val m = mutableListOf(x[0])
    val v = mutableListOf(0.0)
    for (idx in 0 until x.size - 1) {
        val xj = x[idx + 1]
        m.add(m.last() + (xj - m.last()) / (idx + 2))
        v.add(v.last() + (xj - m[m.size-2]) * (xj - m.last()))
    }
    v.forEachIndexed{ idx, it -> v[idx] = it / (idx+1)}

    return Pair(m.toDoubleArray(), v.toDoubleArray())
}

// AI conversion to kotlin
fun welfordMeanVar(x: DoubleArray): Pair<DoubleArray, DoubleArray> {
    val m = mutableListOf<Double>()
    val v = mutableListOf<Double>()
    m.add(x[0])
    v.add(0.0)

    for (i in 1 until x.size) {
        val xi = x[i]
        m.add((m.last() + (xi - m.last()) / (i + 1)))
        v.add(v.last() + (xi - m[m.size - 2]) * (xi - m.last()))
    }

    val r = IntArray(x.size) { i -> i + 1 }
    for (i in v.indices) {
        v[i] = v[i]/r[i]
    }

    return Pair(m.toDoubleArray(), v.toDoubleArray())
}

// generate Bernoulli with probability p.
// TODO where did I get this? numpy?
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
