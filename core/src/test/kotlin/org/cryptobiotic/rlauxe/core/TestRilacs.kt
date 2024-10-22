package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.util.GenSampleFn
import org.cryptobiotic.rlauxe.util.SampleFromArrayWithoutReplacement
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.test.assertTrue

class TestRilacs {

    // ALPHA p 7
    //   Mj =  = Prod(1 + λi * (Xi - µi)), i=1..j;
    // where
    //    λi ≡ (ηi/µi - 1) / (u −µi)
    //    µi ≡ E(Xi |Xi−1 ), computed on the assumption that the null hypothesis is true.
    // Choosing λi is equivalent to choosing ηi :
    //   ηi = µi (1 + λi (u − µi )) (12)
    //
    // The value Mj is the gambler’s wealth after the jth wager:
    //    they bet a fraction λi of their current wealth on the outcome of the ith wager
    //    λi cannot exceed 1/µi (cannot go into debt: Mj cannot go negetive)
    //
    // As ηi ranges from µi to u, λi ranges continuously from 0 to 1/µi.
    // selecting λi is equivalent to selecting a method for estimating ni

    @Test
    fun testRilacs() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val eta0 = .51

        println("test_shrink_trunc_problem $eta0 x=$x")
        val d = 100
        val N = x.size

        val compareAssorter = makeStandardComparisonAssorter(eta0)
        val upperBound = compareAssorter.upperBound

        val sampler = SampleFromArrayWithoutReplacement(x.toDoubleArray())
        val assorterMean = sampler.sampleMean()

        println("N=$N cvrMean=$eta0 assorterMean=$assorterMean eta0=$eta0, d=$d u=${upperBound}")

        val result = doOne(sampler, N, eta0=eta0, d=d, u=upperBound)
        println(result)

        result.etajs.forEachIndexed { idx, etaj ->
            //    λi ≡ (ηi/µi - 1) / (u −µi)
            val muj = result.mujs[idx]
            if (muj > 0.0) {
                val lambda = (etaj / muj - 1.0) / (upperBound - muj)
                val maxLambda = 1.0 / muj
                println(" lambda $lambda < $maxLambda ")
                if (lambda >= maxLambda) {
                    println("wtf")
                }
                assertTrue(lambda < maxLambda)
            }
        }
    }

    fun doOne(
        drawSample: GenSampleFn,
        maxSamples: Int,
        eta0: Double,
        d: Int = 100,
        withoutReplacement: Boolean = true,
        u: Double = 1.0,
    ): TestH0Result {
        val N = drawSample.N()
        val t = 0.5
        val upperBound = u
        val minsd = 1.0e-6
        val c = max(eps, ((eta0 - t) / 2))

        val estimFn = TruncShrinkage(N=N, withoutReplacement=true, upperBound = upperBound,
            minsd = minsd, eta0 = eta0, c = c, d = d)

        val alpha = AlphaMart(
            estimFn = estimFn,
            N = N,
            upperBound = upperBound,
            withoutReplacement = withoutReplacement,
        )

        return alpha.testH0(maxSamples, terminateOnNullReject=true, showDetails=true) { drawSample.sample() }
    }
}