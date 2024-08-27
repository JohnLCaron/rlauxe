package org.cryptobiotic.rlauxe

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

fun makeEstimator() {
    fun truncShrinkage(c: Double, d: Double, eta0: Double, sumkm1: Double, sampleNum: Int) : Double {
        // – Define the function to update eta based on the sample,
        //	  e.g, eta(i, X^i−1 ) = ((d * η0 + S)/(d + i − 1) ∨ (eps(i) + µi )) ∧ u,    (2.5.2, eq 14, "truncated shrinkage")
        //	    where S = Sum(Xk) k=1..i-1  is the sample sum of the first i-1 draws
        //	    and eps(i) = c / sqrt(d + i − 1)

        // 2.5.2 Choosing ǫi . To allow the estimated winner’s share ηi to approach
        //√ µi as the sample grows
        //(if the sample mean approaches µi or less), we shall take ǫi := c/ d + i − 1 for a nonnega-
        //tive constant c, for instance c = (η0 − µ)/2. The estimate ηi is thus the sample mean, shrunk

        var result = ((d * eta0 + sumkm1) / (d + sampleNum - 1)) //  ∨ (eps(i) + µi )) ∧ u

        // The only requirement is that eta(i, X^i−1 ) ∈ (µi .. u), where µi := E(Xi |X i−1 ) is computed under the null.
        // result = min( max(epsi + populationMean, result), upperBound)
        return result
    }
}

// Compare AlphaAlgorithm with output from SHANGRLA TestNonnegMean
class TestAlphaShrinkTrunc {
    val eps = 0.0001  // Generic small value

    @Test
    fun testTruncShrinkageLeadingZeroes() {
        val eta0 = .51
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, )
        val expected = listOf(0.51, 0.5570631122784444, 0.626443375672974, 0.7156724647762773, 0.8346696395428955, 0.9999, 0.9999, 0.9999, 0.9999, 0.9999)

        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, t=t, minsd=minsd, d=d, eta0=eta0, f=f, c=c, eps=eps)
        val result = mutableListOf<Double>()
        repeat(x.size) {
            val sublist = x.subList(0, it+1)
            val estim = estimFn.eta(sublist)
            println("estim = $estim")
            result.add(estim)
        }
        doublesAreEqual(expected, result)
    }

    @Test
    fun testTruncShrinkageLeadingOnes() {
        val eta0 = .51
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val expected = listOf(0.51, 0.5545454545454546, 0.5916666666666667, 0.6230769230769231, 0.65, 0.6733333333333333, 0.63125, 0.5941176470588235, 0.5611111111111111, 0.531578947368421)
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2

        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, t=t, minsd=minsd, d=d, eta0=eta0, f=f, c=c, eps=eps)
        val result = mutableListOf<Double>()
        repeat(x.size) {
            val sublist = x.subList(0, it+1)
            val estim = estimFn.eta(sublist)
            // println(" $it sublist = ${sublist} estim = $estim")
            result.add(estim)
        }
        doublesAreEqual(expected, result)
    }

    @Test
    fun compareAlphaWithShrinkTrunc() {
        val bernoulliDist = Bernoulli(.5)
        val bernoulliList = DoubleArray(20) { bernoulliDist.get() }.toList()

        val v = listOf(
            listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0),
            listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0),
            bernoulliList
        )

        val etas = listOf(.51, .55, .6) // alternative means
        for (eta in etas) {
            for (x: List<Double> in v) {
                compareAlphaWithShrinkTrunc(
                    eta,
                    x)
            }
        }
    }

    fun compareAlphaWithShrinkTrunc(eta0: Double, x: List<Double>) {
        val algoValues = testAlphaWithShrinkTrunc(
            eta0,
            x
        )

        val expectedPhistory = testAlphaMartWithAlphaMart(
            eta0,
            x
        )

        /*
        algoValues.phistory.forEachIndexed { idx, it ->
            assertEquals(expectedPhistory[idx], it, "$idx: ${expectedPhistory[idx]} != ${it}")
        }

         */
    }

    fun testAlphaWithShrinkTrunc(eta0: Double, x: List<Double>): TestH0Result {
        println("testAlphaWithShrinkTrunc $eta0 x=$x")
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, t=t, minsd=minsd, d=d, eta0=eta0, f=f, c=c, eps=eps)
        val alpha = AlphaStatistic(estimFn=estimFn, N=N, upperBound=u, t=t)

        val sampler = SampleFromList(x.toDoubleArray())
        return alpha.testH0(x.size) { sampler.sample() }
    }

    // see start/NonnegMean
    fun testAlphaMartWithAlphaMart(eta0: Double, x: List<Double>): DoubleArray {
        println("testAlphaMartWithAlphaMart $eta0 x=$x")
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val c = (eta0 - t) / 2

        val minsd = 1.0e-6
        val N = x.size

        return DoubleArray(0)
        //val estimFn = ShrinkTrunc(N = N, withReplacement = false, t = t, u = u, minsd=minsd, d = d, eta=eta0, f=f, c=c, eps=eps)
        //val alphamart = AlphaMart(N = N, withReplacement = false, t = t, u = u, estimFnType = EstimFnType.SHRINK_TRUNC, estimFn)

        //return alphamart.test(x.toDoubleArray()).second
    }

    fun epsj(c: Double, d: Int, j:Int): Double =  c/ sqrt(d+j-1.0)
    fun Sj(x: List<Double>, j:Int): Double = if (j == 1) 0.0 else x.subList(0,j-1).sum()
    fun tj(N:Int, t: Double, x: List<Double>, j:Int) =  (N*t-Sj(x, j))/(N-j+1)

    @Test
    fun testAlphaWithShrinkTruncProblem() {
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val eta0 = .51
        val algoValues = testAlphaWithShrinkTrunc(
            eta0,
            x
        )

        println("testAlphaWithShrinkTruncProblem = $algoValues")
    }

    @Test
    fun testAlphaMartWithShrinkTruncProblem() {
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val eta0 = .51

        val expectedPhistory = testAlphaMartWithAlphaMart(
            eta0,
            x
        )

        println("testAlphaMartWithShrinkTruncProblem = ${expectedPhistory.contentToString()}")
    }
}