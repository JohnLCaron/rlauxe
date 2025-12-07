package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.PrevSamples
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.util.Stopwatch

import kotlin.test.Test
import kotlin.test.assertEquals

// match output of python OneAudit oa-batch.ipynb
class TestOnePollingAudit {

    // Numerical example
    // Suppose there were 20,000 cards cast in the Alice v. Bob contest.
    // Of the 20,000 ballot cards, 10,000 were cast by mail and have linked CVRs.
    // Of those 10,000 CVRs, 5,000 report votes for Alice, 4,000 report votes for Bob, and 1,000 report undervotes.
    // The remaining 10,000 cards were cast in 10 precincts numbered 1 to 10, with 1,000 cards in each.
    // The batch subtotals for 5 of those precincts show 900 votes for Alice and 100 for Bob; the other 5 show 900 votes for Bob and 100 for Alice.
    // The reported results are thus 10,000 votes for Alice, 9,000 for Bob, and 1,000 undervotes.
    // The margin is 1,000 votes; the \emph{diluted margin} (margin in votes, divided by cards cast) is $1000/20000 = 5\%$.

    @Test
    fun TestSyntheticAvB_deterministic() {
        val (resultsInf, resultsD) = testSyntheticAvB(1)

        // expect from python
        // aa=0.5641025641025642 ba=0.05128205128205127 ab=0.9743589743589743 bb=0.46153846153846156 wtf=4500 19999 0.5128435908974937
        //results_inf={'rej_N': np.float64(117.0), 'not_rej_N': np.float64(0.0)}
        //results_d={
        //  10: {'rej_N': np.float64(2268.0), 'not_rej_N': np.float64(0.0)},
        //  100: {'rej_N': np.float64(214.0), 'not_rej_N': np.float64(0.0)},
        //  1000: {'rej_N': np.float64(124.0), 'not_rej_N': np.float64(0.0)},
        //  10000: {'rej_N': np.float64(118.0), 'not_rej_N': np.float64(0.0)}}
        println("resultsInf = $resultsInf")
        println("resultsD")
        resultsD.forEach {
            println("  ${it.key} = ${it.value}")
        }
        // actual
        // aa=0.5641025641025642 ba=0.05128205128205127 ab=0.9743589743589743 bb=0.46153846153846156 x.size=19999 x.mean=0.5128435908975763
        // resultsInf = avgReject = 117.0 avgNotReject = 0.0
        // resultsD
        //  10 = avgReject = 2268.0 avgNotReject = 0.0
        //  100 = avgReject = 214.0 avgNotReject = 0.0
        //  1000 = avgReject = 124.0 avgNotReject = 0.0
        //  10000 = avgReject = 118.0 avgNotReject = 0.0

        assertEquals(117.0, resultsInf.avgReject())
        assertEquals(3013.0, resultsD[10]!!.avgReject())
        assertEquals(3391.0, resultsD[100]!!.avgReject())
        assertEquals(4517.0, resultsD[1000]!!.avgReject())
        assertEquals(6949.0, resultsD[10000]!!.avgReject())
    }

    @Test
    fun TestSyntheticAvB_average() {
        val (resultsInf, resultsD) = testSyntheticAvB(1, listOf(100, 200, 300, 400, 500))

        println("resultsInf = $resultsInf")
        println("resultsD")
        resultsD.forEach {
            println("  ${it.key} = ${it.value}")
        }
        // python
        //   results_inf={'rej_N': np.float64(8303.462), 'not_rej_N': np.float64(0.0)}
        //  results_d={
        //    100: {'rej_N': np.float64(866.371), 'not_rej_N': np.float64(0.0)},
        //    200: {'rej_N': np.float64(726.888), 'not_rej_N': np.float64(0.0)},
        //    300: {'rej_N': np.float64(715.247), 'not_rej_N': np.float64(0.0)},
        //    400: {'rej_N': np.float64(734.901), 'not_rej_N': np.float64(0.0)},
        //    500: {'rej_N': np.float64(760.689), 'not_rej_N': np.float64(0.0)}}
        //
        // kotlin
        // resultsInf = avgReject = 8246.687}
        // resultsD
        //  100 = avgReject = 885.551}
        //  200 = avgReject = 744.606}
        //  300 = avgReject = 729.812}
        //  400 = avgReject = 746.278}
        //  500 = avgReject = 773.893}

        //val results_inf, results_d = ballot_polling(x, alpha, Abar, dl, c, reps, verbose=False)
        //val results_inf, results_d

        val (pollingInf, pollingD) = testSyntheticAvB(1, listOf(100, 200, 300, 400, 500), true)
        println("pollingInf = $pollingInf")
        println("pollingD")
        pollingD.forEach {
            println("  ${it.key} = ${it.value}")
        }
        // python
        //        #  results_inf={'rej_N': np.float64(2211.849), 'not_rej_N': np.float64(0.0)}
        //        #  results_d={
        //        #    100: {'rej_N': np.float64(3148.79), 'not_rej_N': np.float64(0.0)},
        //        #    200: {'rej_N': np.float64(3209.07), 'not_rej_N': np.float64(0.0)},
        //        #    300: {'rej_N': np.float64(3209.597), 'not_rej_N': np.float64(0.0)},
        //        #    400: {'rej_N': np.float64(3173.312), 'not_rej_N': np.float64(0.0)},
        //        #    500: {'rej_N': np.float64(3113.102), 'not_rej_N': np.float64(0.0)}}
        //
        // kotlin
        // pollingInf = avgReject = 2217.993}
        // pollingD
        //  100 = avgReject = 3144.519}
        //  200 = avgReject = 3201.077}
        //  300 = avgReject = 3202.541}
        //  400 = avgReject = 3168.242}
        //  500 = avgReject = 3109.274}
    }

    // test_oabatch_ipynb
    fun testSyntheticAvB(reps: Int, dl: List<Int> = listOf(10, 100, 1000, 10000), polling: Boolean = false): Pair<Results, Map<Int, Results>> {
        val N = 20000
        val Abar = (10000 * 1 + 1000 * .5 + 9000 * 0) / N
        val v = 2 * Abar - 1
        val u = 1.0
        val eta = u / (2 * u - v)
        val u_over = 2 * eta
        // eta=0.5128205128205129 v=0.050000000000000044 u_over=1.0256410256410258
        println(" reps=$reps polling=$polling")
        // eta = 0.5128205128205129  v=0.050000000000000044 u_over=1.0256410256410258

        val faa = 0.9 // fraction of votes for Alice in an Alice-majority precinct
        val fab = 0.1 // fraction of votes for Alice in an Bob-majority precinct
        val N_per_pct = 1000
        val pct = 10.0
        // vote for Alice in a precinct with a majority for Alice:
        val aa = B(faa, 1.0, v, u)
        // vote for Bob in a precinct with a majority for Alice:
        val ba = B(faa, 0.0, v, u)
        // vote for Alice in a precinct with a majority for Bob:
        val ab = B(fab, 1.0, v, u)
        // vote for Bob in a precinct with a majority for Bob:
        val bb = B(fab, 0.0, v, u)
        // CVRs
        val cc = B(1.0, 1.0, v, u)

        // make the overstatement data
        N_per_pct * pct / 2.0
        val x1 = DoubleArray(10000) { cc }
        val x2 = DoubleArray((faa * N_per_pct * pct / 2.0).toInt()) { aa }
        val x3 = DoubleArray(((1 - faa) * N_per_pct * pct / 2.0).toInt()) { ba }
        val x4 = DoubleArray((fab * N_per_pct * pct / 2.0).toInt()) { ab }
        val x5 = DoubleArray(((1 - fab) * N_per_pct * pct / 2.0).toInt()) { bb }
        val x = x1 + x2 + x3 + x4 + x5

        // aa=0.5641025641025642 ba=0.05128205128205127 ab=0.9743589743589743 bb=0.46153846153846156 19999 0.5128435908974937
        println("aa=${aa} ba=${ba} ab=${ab} bb=${bb} x.size=${x.size} x.mean=${x.toList().average()}")
        // aa=0.5641025641025642 ba=0.05128205128205127 ab=0.9743589743589743 bb=0.46153846153846156 x.size=19999 x.mean=0.5128435908975763

        val c = .5
        val alpha = 0.05

        //     x: DoubleArray,
        //    alpha: Double,
        //    u_over: Double,
        //    eta: Double,
        //    dl: List<Int>,
        //    c: Double = .5,
        //    reps: Int = 1000,
        return if (polling) oneaudit(x, alpha=alpha, u_over=1.0, eta=Abar,      dl=dl, c=c, reps=reps)
                       else oneaudit(x, alpha=alpha, u_over=u_over, eta=u_over, dl=dl, c=c, reps=reps)
    }

}

interface EstimArrayFn {
    fun eta(prevSamples: DoubleArray): DoubleArray
}

class Results(val reps: Int) {
    var reject = 0
    var notReject = 0

    fun avgReject(): Double {
        return reject / reps.toDouble()
    }

    fun avgNotReject(): Double {
        return notReject / reps.toDouble()
    }

    override fun toString(): String {
        return "avgReject = ${avgReject()}}"
    }
}

////////////////////////////////////
// identical in oa-batch and oa-polling
// probably similar to as SHANGRLA NonnegMean.py shrink_trunc
//   and start/NonnegMean alphamart.shrink_trunc
// Note theres no f parameter

class TruncShrinkageProxy(
    val N: Int, val mu: Double = 0.5, val nu: Double, val u: Double = 1.0, val c: Double = 0.5,
    val d: Int, val withReplacement: Boolean = false
) : EstimArrayFn {
    val proxy = TruncShrinkage(N = N, upperBound = u, eta0 = mu, c = c, d = d)

    override fun eta(prevSamples: DoubleArray): DoubleArray {
        val prevSample = PrevSamples()
        val result: List<Double> = prevSamples.map {
            val eta = proxy.eta(prevSample)
            prevSample.addSample(it)
            eta
        }
        return result.toDoubleArray()
    }
}

////////////////////////////////////
// identical in oa-batch and oa-polling
// simpler than SHANGRLA NonnegMean.py alpha_mart and core/NonnegMean alpha_mart
// because those have factored part of algo into sjm()

fun alpha_mart(
    x: DoubleArray, N: Int, mu: Double = .5, eta: Double = 1.0, u: Double = 1.0, estim: EstimArrayFn,
    withReplacement: Boolean = false
): DoubleArray {
    val cum_sum = np_cumsum(x)
    val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,
    val Sp = DoubleArray(x.size) { S[it] } // same length as the data. LOOK

    //    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
    val M = Sp.mapIndexed { idx, s ->
        // if (withReplacement) mu else (N * mu - s) / (N - j[idx] - 1)
        if (withReplacement) mu else (N * mu - s) / (N - idx)
    }

    //    S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
    //val cum_sum = numpy_cumsum(x)
    //val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,

    //    j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
    //val j = IntArray(x.size) { it + 1 } // 1, 2, 3, ..., len(x) LOOK unneeded?

    //    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
    //val M = DoubleArray(x.size) {
    //    if (withReplacement) mu else (N * mu - S[it]) / (N - j[it] + 1)
    //}

    val etaj = estim.eta(x)
    //    with np.errstate(divide='ignore',invalid='ignore'):
    //        terms = np.cumprod((x*etaj/m + (u-x)*(u-etaj)/(u-m))/u)
    //    terms[m<0] = np.inf
    //    return terms

    val term = x.mapIndexed { idx, it -> (it * etaj[idx] / M[idx] + (u - it) * (u - etaj[idx]) / (u - M[idx])) / u }
        .toDoubleArray()
    val cum_prod = np_cumprod(term)
    return cum_prod
}

////////////////////////////////////
// identical in oa-batch and oa-polling
// probably the same as SHANGRLA NonnegMEan.py sprt_mart
//   and core/NonnegMean SPRT (not ported)
fun sprt_mart(
    x: DoubleArray,
    N: Int,
    mu: Double = .5,
    eta: Double,
    upper: Double,
    withoutReplacement: Boolean = true,
    randomOrder: Boolean = true
): DoubleArray {

    // val eta: Double = 1 - np.finfo(float).eps


// def sprt_mart(x : np.array, N : int, mu : float=1/2, eta: float=1-np.finfo(float).eps, \
//              u: float=1, random_order = True):
//    Finds the p value for the hypothesis that the population
//    mean is less than or equal to mu against the alternative that it is eta,
//    for a population of size N of values in the interval [0, u].
//
//    Generalizes Wald's SPRT for the Bernoulli to sampling without replacement and to bounded
//    values rather than binary values.
//
//    If N is finite, assumes the sample is drawn without replacement
//    If N is infinite, assumes the sample is with replacement
//
//    Data are assumed to be in random order. If not, the calculation for sampling without replacement is incorrect.
//
//    Parameters:
//    -----------
//    x : binary list, one element per draw. A list element is 1 if the the corresponding trial was a success
//    N : population size for sampling without replacement, or np.infinity for sampling with replacement
//    theta : float in (0,u) hypothesized population mean
//    eta : float in (0,u) alternative hypothesized population mean
//    random_order : Boolean
//        if the data are in random order, setting this to True can improve the power.
//        If the data are not in random order, set to False

    x.forEach { require(it >= 0 && it <= upper) }
    //    if any((xx < 0 or xx > u) for xx in x):
    //        raise ValueError(f'Data out of range [0,{u}]')

    //        S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
    //        j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
    //        m = (N*mu-S)/(N-j+1)
    val cum_sum = np_cumsum(x)
    val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,
    val Sp = DoubleArray(x.size) { S[it] } // same length as the data. LOOK

    //    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
    val M = Sp.mapIndexed { idx, s ->
        // if (withReplacement) mu else (N * mu - s) / (N - j[idx] - 1)
        if (withoutReplacement) (N * mu - s) / (N - idx) else mu
    }

    // terms = np.cumprod((x*eta/m + (u-x)*(u-eta)/(u-m))/u) # generalization of Bernoulli SPRT
    val xp = x.mapIndexed { idx, xe ->
        (xe * eta / M[idx] + (upper - xe) * (upper - eta) / (upper - M[idx])) / upper
    }.toDoubleArray()

    val terms = np_cumprod(xp) //  generalization of Bernoulli SPRT

    //    terms[m<0] = np.inf                        # the null is surely false
    return terms
}

////////////////////////////////////
// only appears in oa-batch and not oa-polling
// def oneaudit(x, alpha: float, u_over: float, eta: float, dl: list, c: float=1/2, reps=10**3, verbose=True):
//    '''
//    test whether the assorter mean is < 1/2 using ballot polling, with ALPHA mart and the SPRT
//
//    Parameters
//    ----------
//    x: the data
//    alpha: risk limit
//    u_over: a priori upper bound on the population of scaled overstatements
//    eta: starting alternative value
//    '''
//    resl = ['rej_N','not_rej_N']
//

fun oneaudit(
    x: DoubleArray,
    alpha: Double,
    u_over: Double,
    eta: Double,
    dl: List<Int>,
    c: Double = .5,
    reps: Int = 1000,
): Pair<Results, Map<Int, Results>> {

    val resultsInf = Results(reps)
    val resultsD = mutableMapOf<Int, Results>()
    dl.forEach {
        resultsD[it] = Results(reps)
    }

    val N = x.size

    var randx = x
    repeat(reps) {
        if (reps > 1) {
            randx.shuffle()
        }

        //        np.random.shuffle(x)
        //        # OneAudit a priori
        //        mart = sprt_mart(x, N, mu=1/2, eta=eta, u=u_over, random_order=True)
        val mart = sprt_mart(randx, N, eta = eta, upper = u_over) //  # OneAudit a priori

        // find first accepted, its index = number rejected
        var firstIdx = findFirstIndex(mart) { it >= 1.0 / alpha }
        resultsInf.reject += if (firstIdx < 0) N else firstIdx   // the number rejected

        //        # OneAudit ALPHA
        //        for d in dl:
        //            mart = alpha_mart(x, N, mu=1/2, eta=eta, u=u_over, \
        //                              estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,u,c=c,d=d))

        dl.forEach { d ->
            val stopwatch = Stopwatch()

            // val N: Int, val mu: Double = 0.5, val nu: Double, val u: Double = 1.0, val c: Double = 0.5,
            //                  val d: Double = 100.0, val withReplacement: Boolean = false
            // val estimFn = ShrinkTrunc(N, mu = 0.5, eta, c = c, u=u_over, d = d)
            val estimFn = TruncShrinkageProxy(N, mu = 0.5, eta, c = c, u=u_over, d = d)
            val alpha_mart = alpha_mart(randx, N, mu = 0.5, eta = eta, u = u_over, estim = estimFn)

            var firstIdx = findFirstIndex(alpha_mart) { it >= 1.0 / alpha }
            val result = resultsD[d]!!
            result.reject += if (firstIdx < 0) N else firstIdx   // the number rejected
            println("run OneAudit alpha_mart dl=$d ${stopwatch.took()}")
        }


    }
    return Pair(resultsInf, resultsD)
}

fun B(c: Double, b: Double, v: Double, u: Double = 1.0): Double {
    /*
    overstatement assorter

            Parameters
    ----------
    c: assorter applied to the CVR
    b: assorter applied to the physical card
    v: reported assorter margin, 2*(assorter mean)-1
    u: a priori upper bound on the assorter

    Returns
    -------
    overstatement assorter value
    */
    return (u + b - c) / (2 * u - v)
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

