package org.cryptobiotic.rlauxe

import kotlin.test.Test
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
    fun testWelfordRandom() {
        val b = Bernoulli(.45)
        val sample = DoubleArray(10) { b.get() }
        w2(sample)
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

    val last = x.size - 1
    assertEquals(x.average(), mj[last])
    assertEquals(x.variance(), sdj2[last])
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
