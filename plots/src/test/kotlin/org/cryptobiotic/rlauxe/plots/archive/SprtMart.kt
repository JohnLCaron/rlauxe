package org.cryptobiotic.rlauxe.plots.archive

import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.util.np_cumprod
import org.cryptobiotic.rlauxe.util.np_cumsum

// identical in ONEAUDIT oa-batch and oa-polling
// probably the same as SHANGRLA NonnegMean.py sprt_mart
// and core/NonnegMean SPRT (not ported)

// def sprt_mart(x : np.array, N : int, mu : float=1/2, eta: float=1-np.finfo(float).eps, u: float=1, random_order = True):
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

class SprtMart(val N: Int, val eta: Double, val mu: Double = .5, val upper: Double = 1.0, val withoutReplacement: Boolean = true) {

    fun testH0(x: DoubleArray): TestH0Result {

        x.forEach { require(it >= 0 && it <= upper) }
        //    if any((xx < 0 or xx > u) for xx in x):
        //        raise ValueError(f'Data out of range [0,{u}]')

        //        S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
        //        j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
        //        m = (N*mu-S)/(N-j+1)
        val cum_sum = np_cumsum(x)
        val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,
        val Sp = DoubleArray(x.size) { S[it] } // same length as the data. TODO not needed

        //    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
        val m = Sp.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            if (withoutReplacement) (N * mu - s) / (N - sampleNum + 1) else mu
        }

        // terms = np.cumprod((x*eta/m + (u-x)*(u-eta)/(u-m))/u) # generalization of Bernoulli SPRT
        val xp = x.mapIndexed { idx, xe ->
            (xe * eta / m[idx] + (upper - xe) * (upper - eta) / (upper - m[idx])) / upper
        }

        val terms = np_cumprod(xp.toDoubleArray()).toList() //  generalization of Bernoulli SPRT
        //    terms[m<0] = np.inf                        # the null is surely false

        // find first term that passes risk test
        val firstIdx = terms.indexOfFirst{ 1.0 / it < .05 }
        val status = if (firstIdx >= 0) TestH0Status.StatRejectNull else TestH0Status.LimitReached
        /* if (status == TestH0Status.RejectNull) {
            val start = max(0, firstIdx-10)
            val end = min(firstIdx+5, x.size)
            println("wrong m=${m.subList(start, end)}")
            println("      t=${xp.subList(start, end)}")
            println("      T=${terms.subList(start, end)}")
            println("  m=${m[firstIdx]} t=${xp[firstIdx]} T=${terms[firstIdx]}")
            print("")
        }
         */

        // data class TestH0Result(val status: TestH0Status, val sampleCount: Int, val sampleMean: Double, val pvalues: List<Double>)
        return TestH0Result(status, firstIdx + 1, 0.0, emptyList(), emptyList(), emptyList()) // one based index
    }
}