package org.cryptobiotic.rlauxe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// initial estimate of the population mean for FixedAlternativeMean
fun setEta0(t: Double = 0.5, upperBound: Double = 1.0) : Double {
    //    For polling audits, eta0 could be the reported mean value of the assorter.
    //	    For instance, for the assertion corresponding to checking whether w got more votes than ℓ,
    //	      η0 = (Nw + Nc/2)/N , where Nw is the number of votes reported for w , Nℓ is the
    //	   number of votes reported for ℓ, and Nc = N − Nw − Nℓ is the number of ballot cards
    //	   reported to have a vote for some other candidate or no valid vote in the contest.

    //    For comparison audits, eta0 can be based on assumed or historical rates of overstatement errors.
    return (t + (upperBound - t) / 2)
}

// Compute the alternative mean just before the jth draw, for a fixed alternative that the original population mean is eta.
class FixedAlternativeMean(val N: Int, val eta0:Double): EstimFn {

    //         val m = DoubleArray(x.size) {
    //            val m1 = (N * t - Sp[it])
    //            val m2 = (N - j[it] + 1)
    //            val m3 = m1 / m2
    //            if (isFinite) (N * t - Sp[it]) / (N - j[it] + 1) else t
    //        }

    override fun eta(prevSamples: List<Double>): Double {
        val j = prevSamples.size + 1
        val sampleSum = prevSamples.sum()
        val m1 = (N * eta0 - sampleSum)
        val m2 = (N - j + 1)
        val m3 = m1 / m2
        val result = (N * eta0 - sampleSum) / (N - j + 1)
        return result
    }

}

// Compare AlphaAlgorithm with output from start/TestNonnegMean.testAlphaMartAllHalf
class TestAlphaAlternativeFixedMean  {

    /*
    @Test
    fun testAlphaAllHalf() {
        println("testAlphaAllHalf")
        //        # When all the items are 1/2, estimated p for a mean of 1/2 should be 1.
        //        s = np.ones(5)/2
        //        test = NonnegMean(N=int(10**6))
        //        np.testing.assert_almost_equal(test.alpha_mart(s)[0],1.0)
        val s1 = DoubleArray(5) { .5 }
        val u = 1.0
        val N = 1_000_000

        val estimFn = FixedAlternativeMean(N, setEta0())
        val alpha = AlphaStatistic(
            estimFn = estimFn,
            N = N,
            upperBound = u,
            withoutReplacement = true
        )

        val sampler = SampleFromList(s1)
        val algoValues = alpha.testH0(s1.size) { sampler.sample() }

        // run 0.75, 0.75000025000025, 0.750000500001, 0.75000075000225, 0.750001000004
        doublesAreClose(listOf(0.75, 0.75000025000025, 0.750000500001, 0.75000075000225, 0.750001000004), algoValues.etaj)
        algoValues.phistory.forEach { assertEquals(1.0, it)}
    }

    @Test
    fun testAlphaMartEps() {
        println("testAlphaMartEps")
        val N = 1_000_000
        val t = .0001
        val x = DoubleArray(5) { .5 }

        val estimFn = FixedAlternativeMean(N, setEta0(t))
        val alpha = AlphaAlgorithm(estimFn=estimFn, N=N, t=t)
        val sampler = SampleFromList(x)
        val algoValues = alpha.run(x.size) { sampler.sample() }

        doublesAreEqual(listOf(0.50005, 0.50005000005, 0.5000500001000002, 0.5000500001500005, 0.5000500002000008), algoValues.etaj)
        doublesAreEqual(listOf(1.0E-4, 9.95000995000995E-5, 9.900019800039601E-5, 9.85002955008865E-5, 9.8000392001568E-5), algoValues.populationMeanValues)
        doublesAreEqual(listOf(2500.5, 2513.0615576639316, 2525.749999749975, 2538.5672585082107, 2551.515305622398), algoValues.tjs)
        doublesAreEqual(listOf(2500.5, 6283910.424938661, 1.5871586754217688E10, 4.0291090474829625E13, 1.028033340267446E17), algoValues.tstat)
        doublesAreEqual(listOf(3.999200159968006E-4, 1.591365777639584E-7, 6.300567268324711E-11, 2.4819382851519325E-14, 9.727310981371936E-18), algoValues.phistory)
    }

    // etaj = [1.00005, 1.0000504000504002, 1.0000506001012004, 1.0000506001518006, 1.0000504002016009]
    //m = [1.0E-4, 9.94000994000994E-5, 9.86001972003944E-5, 9.760029280087839E-5, 9.640038560154241E-5]
    //tj = [3000.5, 4024.643661761821, 5071.490364786546, 6148.03360619736, 7261.897717512307]
    //T = [3000.5, 1.2075943307116345E7, 6.1243030127749115E10, 3.76524207370759E14, 2.7342802820938455E18]
    @Test
    fun testAlphaMartU2() {
        println("testAlphaMartU2")
        val N = 1_000_000
        val u = 2.0
        val t = .0001
        val x = doubleArrayOf(0.6, 0.8, 1.0, 1.2, 1.4)

        val estimFn = FixedAlternativeMean(N, setEta0(t, u))
        val alpha = AlphaAlgorithm(estimFn=estimFn, N=N, upperBound=u, t=t)

        val sampler = SampleFromList(x)
        val algoValues = alpha.run(x.size) { sampler.sample() }

        doublesAreEqual(listOf(1.00005, 1.0000504000504002, 1.0000506001012004, 1.0000506001518006, 1.0000504002016009), algoValues.etaj)
        doublesAreEqual(listOf(1.0E-4, 9.94000994000994E-5, 9.86001972003944E-5, 9.760029280087839E-5, 9.640038560154241E-5), algoValues.populationMeanValues)
        doublesAreEqual(listOf(3000.5, 4024.643661761821, 5071.490364786546, 6148.03360619736, 7261.897717512307), algoValues.tjs)
        doublesAreEqual(listOf(3000.5, 1.2075943307116345E7, 6.1243030127749115E10, 3.76524207370759E14, 2.7342802820938455E18), algoValues.tstat)
        doublesAreEqual(listOf(3.332777870354941E-4, 8.280926587413679E-8, 1.6328388682174326E-11, 2.655871735267506E-15, 3.6572695438311987E-19), algoValues.phistory)
    }

    @Test
    fun testLastObs() {
        println("testLastObs")
        //         s1 = [1, 0, 1, 1, 0, 0, 1]
        //        test = NonnegMean(N=7, u=1, t = 3/7)
        val N = 7
        val u = 1.0
        val t = 3.0 / 7.0
        val x1 = doubleArrayOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)

        val estimFn = FixedAlternativeMean(N, setEta0(t))
        val alpha = AlphaAlgorithm(estimFn=estimFn, N=N, upperBound=u, t=t)

        val sampler = SampleFromList(x1)
        val algoValues = alpha.run(x1.size) { sampler.sample() }

        // test_lastObs()
        // etaj = [0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991]
        //m = [0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, 0.0, 0.0]
        //tj = [1.6666666666666665, 0.5000000000000001, 1.9999999999999996, 2.999999999999999, NaN, NaN, Infinity]
        //T = [1.6666666666666665, 0.8333333333333335, 1.6666666666666665, 4.999999999999998, NaN, NaN, NaN]
        //test_lastObs alpha_mart1=[0.6000000000000001, 1.0, 0.6000000000000001, 0.20000000000000007, 1.0, 1.0, 0.0]

        // actual
        //  etajs [0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991]
        // populationMeanValues [0.5, 0.4, 0.5, 0.3333333333333333, 0.0, 0.0, NaN]
        // tjs [1.4285714285714284, 0.5555555555555558, 1.5999999999999996, 2.2499999999999996, NaN, NaN, NaN]
        // tstat [1.4285714285714284, 0.7936507936507939, 1.26984126984127, 2.8571428571428568, NaN, NaN, NaN]
        // phistory [0.7000000000000001, 1.0, 0.7874999999999999, 0.35000000000000003, NaN, NaN, NaN]

        doublesAreEqual(listOf(0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991), algoValues.etaj)
        doublesAreEqual(listOf(0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, 0.0, 0.0), algoValues.populationMeanValues)
        doublesAreEqual(listOf(1.6666666666666665, 0.5000000000000001, 1.9999999999999996, 2.999999999999999, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY), algoValues.tjs)
        doublesAreEqual(listOf(1.6666666666666665, 0.8333333333333335, 1.6666666666666665, 4.999999999999998, 1.0, 1.0, Double.POSITIVE_INFINITY), algoValues.tstat)
        doublesAreEqual(listOf(0.6000000000000001, 1.0, 0.6000000000000001, 0.20000000000000007, 1.0, 1.0, 0.0), algoValues.phistory)

        val x2 = doubleArrayOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
        val sampler2 = SampleFromList(x2)
        val algoValues2 = alpha.run(x2.size) { sampler2.sample() }

        doublesAreEqual(listOf(0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991), algoValues2.etaj)
        doublesAreEqual(listOf(0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, 0.0, 0.0), algoValues2.populationMeanValues)
        doublesAreEqual(listOf(1.6666666666666665, 0.5000000000000001, 1.9999999999999996, 2.999999999999999, Double.NaN, Double.NaN, Double.NaN), algoValues2.tjs)
        doublesAreEqual(listOf(1.6666666666666665, 0.8333333333333335, 1.6666666666666665, 4.999999999999998, 1.0, 1.0, 1.0), algoValues2.tstat)
        doublesAreEqual(listOf(0.6000000000000001, 1.0, 0.6000000000000001, 0.20000000000000007, 1.0, 1.0, 1.0), algoValues2.phistory)
    }

     */
}

// testAlphaAllHalf
//    1 = 0.5 etaj = 0.75 tj=1.0, T = 1.0
//    2 = 0.5 etaj = 0.75000025000025 tj=1.0, T = 1.0
//    3 = 0.5 etaj = 0.750000500001 tj=1.0, T = 1.0
//    4 = 0.5 etaj = 0.75000075000225 tj=1.0, T = 1.0
//    5 = 0.5 etaj = 0.750001000004 tj=1.0, T = 1.0
//   etajs [0.75, 0.75000025000025, 0.750000500001, 0.75000075000225, 0.750001000004]
//   populationMeanValues [0.5, 0.5, 0.5, 0.5, 0.5]
//   tjs [1.0, 1.0, 1.0, 1.0, 1.0]
//   tstat [1.0, 1.0, 1.0, 1.0, 1.0]
//   phistory [1.0, 1.0, 1.0, 1.0, 1.0]
//testLastObs
//    1 = 1.0 etaj = 0.7142857142857142 tj=1.6666666666666665, T = 1.6666666666666665
//    2 = 0.0 etaj = 0.6666666666666665 tj=0.5000000000000001, T = 0.8333333333333335
//    3 = 1.0 etaj = 0.7999999999999998 tj=1.9999999999999996, T = 1.6666666666666665
//    4 = 1.0 etaj = 0.7499999999999998 tj=2.999999999999999, T = 4.999999999999998
//    5 = 0.0 etaj = 0.6666666666666664 tj=NaN, T = NaN
//    6 = 0.0 etaj = 0.9999999999999996 tj=NaN, T = NaN
//    7 = 1.0 etaj = 1.9999999999999991 tj=Infinity, T = NaN
//   etajs [0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991]
//   populationMeanValues [0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, 0.0, 0.0]
//   tjs [1.6666666666666665, 0.5000000000000001, 1.9999999999999996, 2.999999999999999, NaN, NaN, Infinity]
//   tstat [1.6666666666666665, 0.8333333333333335, 1.6666666666666665, 4.999999999999998, 1.0, 1.0, Infinity]
//   phistory [0.6000000000000001, 1.0, 0.6000000000000001, 0.20000000000000007, 1.0, 1.0, 0.0]
//    1 = 1.0 etaj = 0.7142857142857142 tj=1.6666666666666665, T = 1.6666666666666665
//    2 = 0.0 etaj = 0.6666666666666665 tj=0.5000000000000001, T = 0.8333333333333335
//    3 = 1.0 etaj = 0.7999999999999998 tj=1.9999999999999996, T = 1.6666666666666665
//    4 = 1.0 etaj = 0.7499999999999998 tj=2.999999999999999, T = 4.999999999999998
//    5 = 0.0 etaj = 0.6666666666666664 tj=NaN, T = NaN
//    6 = 0.0 etaj = 0.9999999999999996 tj=NaN, T = NaN
//    7 = 0.0 etaj = 1.9999999999999991 tj=NaN, T = NaN
//   etajs [0.7142857142857142, 0.6666666666666665, 0.7999999999999998, 0.7499999999999998, 0.6666666666666664, 0.9999999999999996, 1.9999999999999991]
//   populationMeanValues [0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, 0.0, 0.0]
//   tjs [1.6666666666666665, 0.5000000000000001, 1.9999999999999996, 2.999999999999999, NaN, NaN, NaN]
//   tstat [1.6666666666666665, 0.8333333333333335, 1.6666666666666665, 4.999999999999998, 1.0, 1.0, 1.0]
//   phistory [0.6000000000000001, 1.0, 0.6000000000000001, 0.20000000000000007, 1.0, 1.0, 1.0]
//testAlphaMartU2
//    1 = 0.6 etaj = 1.00005 tj=3000.5, T = 3000.5
//    2 = 0.8 etaj = 1.0000504000504002 tj=4024.643661761821, T = 1.2075943307116345E7
//    3 = 1.0 etaj = 1.0000506001012004 tj=5071.490364786546, T = 6.1243030127749115E10
//    4 = 1.2 etaj = 1.0000506001518006 tj=6148.03360619736, T = 3.76524207370759E14
//    5 = 1.4 etaj = 1.0000504002016009 tj=7261.897717512307, T = 2.7342802820938455E18
//   etajs [1.00005, 1.0000504000504002, 1.0000506001012004, 1.0000506001518006, 1.0000504002016009]
//   populationMeanValues [1.0E-4, 9.94000994000994E-5, 9.86001972003944E-5, 9.760029280087839E-5, 9.640038560154241E-5]
//   tjs [3000.5, 4024.643661761821, 5071.490364786546, 6148.03360619736, 7261.897717512307]
//   tstat [3000.5, 1.2075943307116345E7, 6.1243030127749115E10, 3.76524207370759E14, 2.7342802820938455E18]
//   phistory [3.332777870354941E-4, 8.280926587413679E-8, 1.6328388682174326E-11, 2.655871735267506E-15, 3.6572695438311987E-19]
//testAlphaMartEps
//    1 = 0.5 etaj = 0.50005 tj=2500.5, T = 2500.5
//    2 = 0.5 etaj = 0.50005000005 tj=2513.0615576639316, T = 6283910.424938661
//    3 = 0.5 etaj = 0.5000500001000002 tj=2525.749999749975, T = 1.5871586754217688E10
//    4 = 0.5 etaj = 0.5000500001500005 tj=2538.5672585082107, T = 4.0291090474829625E13
//    5 = 0.5 etaj = 0.5000500002000008 tj=2551.515305622398, T = 1.028033340267446E17
//   etajs [0.50005, 0.50005000005, 0.5000500001000002, 0.5000500001500005, 0.5000500002000008]
//   populationMeanValues [1.0E-4, 9.95000995000995E-5, 9.900019800039601E-5, 9.85002955008865E-5, 9.8000392001568E-5]
//   tjs [2500.5, 2513.0615576639316, 2525.749999749975, 2538.5672585082107, 2551.515305622398]
//   tstat [2500.5, 6283910.424938661, 1.5871586754217688E10, 4.0291090474829625E13, 1.028033340267446E17]
//   phistory [3.999200159968006E-4, 1.591365777639584E-7, 6.300567268324711E-11, 2.4819382851519325E-14, 9.727310981371936E-18]