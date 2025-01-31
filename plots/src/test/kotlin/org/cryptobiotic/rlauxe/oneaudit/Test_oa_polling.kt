import kotlin.math.*

// Generalized Wald SPRT
fun sprtMart(
    x: DoubleArray,
    N: Int,
    mu: Double = 0.5,
    eta: Double = 1 - Double.MIN_VALUE,
    u: Double = 1.0,
    randomOrder: Boolean = true,
    withoutReplacement: Boolean = true
): DoubleArray {
    /*
    Finds the p value for the hypothesis that the population
    mean is less than or equal to mu against the alternative that it is eta,
    for a population of size N of values in the interval [0, u].

    Generalizes Wald's SPRT for the Bernoulli to sampling without replacement and to bounded
    values rather than binary values.

    If N is finite, assumes the sample is drawn without replacement
    If N is infinite, assumes the sample is with replacement

    Data are assumed to be in random order. If not, the calculation for sampling without replacement is incorrect.

    Parameters:
    -----------
    x : binary list, one element per draw. A list element is 1 if the
        the corresponding trial was a success
    N : int
        population size for sampling without replacement, or np.infinity for
        sampling with replacement
    mu : float in (0,u)
        hypothesized population mean = 1/2
    eta : float in (0,u)
        alternative hypothesized population mean
    randomOrder : Boolean
        if the data are in random order, setting this to True can improve the power.
        If the data are not in random order, set to False
    */
    if (x.any { it < 0 || it > u }) throw IllegalArgumentException("Data out of range [0,$u]")
    val m: DoubleArray
    if (withoutReplacement) {
        if (!randomOrder) throw IllegalArgumentException("data must be in random order for samples without replacement")
        val S = doubleArrayOf(0.0) + x.runningFold(0.0) { acc, v -> acc + v }.dropLast(1).toDoubleArray()
        val j = IntArray(x.size+1) { it + 1 } // 1, 2, 3, ..., len(x)
        m = S.mapIndexed { i, value -> (N * mu - value) / (N - j[i] + 1) }.toDoubleArray()
    } else {
        m = DoubleArray(x.size) { mu }
    }
    val terms = DoubleArray(x.size)
    var product = 1.0
    for (i in x.indices) {
        val divisor = m[i]
        if (divisor < 0) {
            terms[i] = Double.POSITIVE_INFINITY
        } else {
            product *= ((x[i] * eta / divisor + (u - x[i]) * (u - eta) / (u - divisor)) / u)
            terms[i] = product
        }
    }
    return terms
}

fun shrinkTrunc(
    x: DoubleArray,
    N: Int,
    mu: Double = 0.5,
    nu: Double = 1 - Double.MIN_VALUE,
    u: Double = 1.0,
    c: Double = 0.5,
    d: Int = 100,
    withoutReplacement: Boolean = true
): DoubleArray {
    /*
    apply the shrinkage and truncation estimator to an array

    sample mean is shrunk towards nu, with relative weight d times the weight of a single observation.
    estimate is truncated above at u-u*eps and below at mu_j+e_j(c,j)

    S_1 = 0
    S_j = \sum_{i=1}^{j-1} x_i, j > 1
    m_j = (N*mu-S_j)/(N-j+1) if np.isfinite(N) else mu
    e_j = c/sqrt(d+j-1)
    eta_j =  ( (d*nu + S_j)/(d+j-1) \vee (m_j+e_j) ) \wedge u*(1-eps)

    Parameters
    ----------
    x : np.array
        input data
    mu : float in (0, 1)
        hypothesized population mean
    eta : float in (t, 1)
        initial alternative hypothethesized value for the population mean
    c : positive float
        scale factor for allowing the estimated mean to approach t from above
    d : positive float
        relative weight of nu compared to an observation, in updating the alternative for each term
    */
    val S = doubleArrayOf(0.0) + x.runningFold(0.0) { acc, v -> acc + v }.dropLast(1).toDoubleArray()
    val j = IntArray(x.size) { it + 1 }
    val m = if (withoutReplacement) S.mapIndexed { i, value -> (N * mu - value) / (N - j[i] + 1) }.toDoubleArray()
    else DoubleArray(x.size) { mu }
    return DoubleArray(x.size) { i ->
        min(
            u * (1 - Double.MIN_VALUE),
            max((d * nu + S[i]) / (d + j[i] - 1), m[i] + c / sqrt(d.toDouble() + j[i] - 1))
        )
    }
}

fun alphaMart(
    x: DoubleArray,
    N: Int,
    mu: Double = 0.5,
    eta: Double = 1 - Double.MIN_VALUE,
    u: Double = 1.0,
    estim: (DoubleArray, Int, Double, Double, Double) -> DoubleArray,
    withoutReplacement: Boolean = true
): DoubleArray {
    /*
    Finds the ALPHA martingale for the hypothesis that the population
    mean is less than or equal to t using a martingale method,
    for a population of size N, based on a series of draws x.

    The draws must be in random order, or the sequence is not a martingale under the null

    If N is finite, assumes the sample is drawn without replacement
    If N is infinite, assumes the sample is with replacement

    Parameters
    ----------
    x : list corresponding to the data
    N : int
        population size for sampling without replacement, or np.infinity for sampling with replacement
    mu : float in (0,1)
        hypothesized fraction of ones in the population
    eta : float in (t,1)
        alternative hypothesized population mean
    estim : callable
        estim(x, N, mu, eta, u) -> np.array of length len(x), the sequence of values of eta_j for ALPHA

    Returns
    -------
    terms : array
        sequence of terms that would be a nonnegative supermartingale under the null
    */
    val S = doubleArrayOf(0.0) + x.runningFold(0.0) { acc, v -> acc + v }.dropLast(1).toDoubleArray()
    val j = IntArray(x.size+1) { it + 1 }
    val m = if (withoutReplacement) S.mapIndexed { i, value -> (N * mu - value) / (N - j[i] + 1) }.toDoubleArray()
    else DoubleArray(x.size) { mu }
    val etaj = estim(x, N, mu, eta, u)
    val terms = DoubleArray(x.size)
    var product = 1.0
    for (i in x.indices) {
        val divisor = m[i]
        if (divisor < 0) {
            terms[i] = Double.POSITIVE_INFINITY
        } else {
            product *= ((x[i] * etaj[i] / divisor + (u - x[i]) * (u - etaj[i]) / (u - divisor)) / u)
            terms[i] = product
        }
    }
    return terms
}

fun overstatement(x: Double? = null, eta: Double? = null, u: Double = 1.0): Double {
    return (1 - (eta!! - x!!) / u) / (2 - (2 * eta - 1) / u)
}

fun upperBound(eta: Double, u: Double = 1.0): Double {
    return 2 / (2 - (2 * eta - 1) / u)
}

fun assorterMean(eta: Double, u: Double = 1.0): Double {
    return u / (1 - 2 * eta + 2 * u)
}

data class TestPollingResult(
    // resultsApaB[theta][N][b][eta]
    val theta: Double,
    val N: Int,
    val b: Double,  // %undervotes ??
    val eta: Double,
    val d: Int,
    val rejectNull: Boolean,
    val estSampleSize: Int,
)

class TestOAPolling(
    withoutReplacement: Boolean = true
) {

    // Table 3: Column 6: expected sample size for CLCA using ONE CVRs based on batch subtotals, for the ALPHA
    // risk-measuring function with the truncated shrinkage estimator with parameters c = 1/2, d = 10, estimated from
    // 100 Monte Carlo replications. Column 7: expected sample size for BPA using Wald’s SPRT.
    // able 4 summarizes the contest and audit results;
    // auditors found no errors in the 8 CVRs, each of which yields the overstatement assorter value u/(2u−v).

    // Sampling without replacement with some non-votes
    fun testTables3And4() {
        // Tables 3 and 4

        val reps = 10.0.pow(3).toInt()

        val thetal = listOf(0.505, 0.51, 0.52, 0.55, 0.6)
        val blanks = listOf(0.1, 0.25, 0.5, 0.75)  // %undervotes ??
        val Nl = listOf(1000) // listOf(10000, 100000, 500000)
        val etal = thetal
        val alpha = 0.05

        // for ALPHA
        val c_base = 0.5
        val dl = listOf(10, 100, 1000)

        // for RiLACs
        val D = 10
        val beta = 1

        // for ONEAudit
        val one_frac = 0.99  // fraction of the upper bound to use for null mean

        val resl = listOf("rej_N", "not_rej_N")

        val resultsAB = mutableListOf<TestPollingResult>()
        val resultsApaB = mutableListOf<TestPollingResult>()
        val resultsCompB = mutableListOf<TestPollingResult>()
        val resultsCompAB = mutableListOf<TestPollingResult>()

        for (theta in thetal) {
            println("theta=$theta")
            for (N in Nl) {
                println("  N=$N")
                for (b in blanks) {
                    println("    blanks=$b")
                    val nonBlank = (N * (1 - b)).toInt()
                    val n_A = (nonBlank * theta).toInt()
                    val n_B = nonBlank - n_A
                    val x = DoubleArray(n_A) { 1.0 } + DoubleArray(n_B) { 0.0 } + DoubleArray(N - nonBlank) { 0.5 }

                    repeat(reps) {
                        x.shuffle()

                        // a priori Kelly and a priori SPRT
                        for (eta in etal) {
                            val n_eta_A = (nonBlank * eta).toInt()
                            val n_eta_B = nonBlank - n_eta_A
                            val eta_shangrla = (nonBlank * eta + (N - nonBlank) / 2.0) / N
                            val eta_oneaudit = assorterMean(eta_shangrla)
                            val c = c_base * (eta - 1.0 / 2)

                            // a priori SPRT
                            val mart = sprtMart(x, N, mu = 1.0 / 2, eta = eta_shangrla, u = 1.0, randomOrder = true)
                            val found = mart.indexOfFirst { it >= 1.0 / alpha }
                            resultsApaB.add( TestPollingResult(theta,N,b,eta,0, found >= 0,found))

                            // ALPHA
                            for (d in dl) {
                                val martAlpha = alphaMart(
                                    x,
                                    N,
                                    mu = 1.0 / 2,
                                    eta = eta_shangrla,
                                    u = 1.0,
                                    estim = { x, N, mu, eta, u -> shrinkTrunc(x, N, mu, eta, 1.0, c = c, d = d) }
                                )
                                val foundAlpha = martAlpha.indexOfFirst { it >= 1.0 / alpha }
                                resultsAB.add( TestPollingResult(theta,N,b,eta, d,found >= 0,found))
                            }

                            // OneAudit a priori
                            val martOneAudit = sprtMart(x, N, mu = 1.0 / 2, eta = eta_oneaudit, u = upperBound(eta), randomOrder = true)
                            // rejections by N
                            val foundOneAudit = martOneAudit.indexOfFirst { it >= 1.0 / alpha }
                            resultsCompB.add( TestPollingResult(theta,N,b,eta, 0,found >= 0,found))

                            // OneAudit ALPHA
                            for (d in dl) {
                                val martOneAuditAlpha = alphaMart(
                                    x,
                                    N,
                                    mu = 1.0 / 2,
                                    eta = eta_oneaudit,
                                    u = upperBound(eta),
                                    estim = { x, N, mu, eta0, u -> shrinkTrunc(x, N, mu, eta0, 1.0, c = c, d = d) }
                                )
                                // rejections by N
                                val foundOneAuditAlpha = martOneAuditAlpha.indexOfFirst { it >= 1.0 / alpha }
                                resultsCompAB.add( TestPollingResult(theta,N,b,eta, d,found >= 0,found))
                            }
                        }
                    }
                }
            }
        }

        /* check for failures I guess
        for (theta in thetal) {
            for (N in Nl) {
                for (b in blanks) {
                    for (eta in etal) {
                        resultsCompB[theta]!![N]!![b]!![eta]!!["rej_N"] =
                            resultsCompB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble() /
                                    (reps - resultsCompB[theta]!![N]!![b]!![eta]!!["not_rej_N"]!!.toInt()) + 1
                        if (resultsCompB[theta]!![N]!![b]!![eta]!!["not_rej_N"]!! > 0) {
                            println("a 1Audit comp_b did not reject for theta=$theta, N=$N, b=$b, eta=$eta")
                        }

                        resultsApaB[theta]!![N]!![b]!![eta]!!["rej_N"] =
                            resultsApaB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble() /
                                    (reps - resultsApaB[theta]!![N]!![b]!![eta]!!["not_rej_N"]!!.toInt()) + 1
                        if (resultsApaB[theta]!![N]!![b]!![eta]!!["not_rej_N"]!! > 0) {
                            println("a priori ALPHA did not reject for theta=$theta, N=$N, b=$b, eta=$eta")
                        }

                        for (d in dl) {
                            resultsAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"] =
                                resultsAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble() /
                                        (reps - resultsAB[theta]!![N]!![b]!![eta]!![d]!!["not_rej_N"]!!.toInt()) + 1
                            resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"] =
                                resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble() /
                                        (reps - resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["not_rej_N"]!!.toInt()) + 1
                            if (resultsAB[theta]!![N]!![b]!![eta]!![d]!!["not_rej_N"]!! > 0) {
                                println("ALPHA did not reject for theta=$theta, N=$N, b=$b, eta=$eta, d=$d")
                            }
                            if (resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["not_rej_N"]!! > 0) {
                                println("1Audit comp_a_b did not reject for theta=$theta, N=$N, b=$b, eta=$eta, d=$d")
                            }
                        }
                    }
                }
            }
        } */

        /* best
        val best = mutableMapOf<Double, MutableMap<Int, MutableMap<Double, Double>>>()
        for (theta in thetal) {
            best[theta] = mutableMapOf()
            for (N in Nl) {
                best[theta]!![N] = mutableMapOf()
                for (b in blanks) {
                    for (eta in etal) {
                        best[theta]!![N]!![b] = listOf(
                            best[theta]!![N]!![b],
                            resultsApkB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble(),
                            resultsApaB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble(),
                            resultsCompB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble()
                        ).min()
                        for (d in dl) {
                            best[theta]!![N]!![b] = listOf(
                                best[theta]!![N]!![b],
                                resultsAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble(),
                                resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble()
                            ).min()
                        }
                    }
                }
            }
        }
        println("best=$best") */

        // ipnbk has:
        // 0.505: {1000: {0.1: 1.0, 0.25: 1.0, 0.5: 1.0, 0.75: 1.0}},
        // 0.51: {1000: {0.1: 1.0, 0.25: 1.0, 0.5: 1.0, 0.75: 1.0}},
        // 0.52: {1000: {0.1: 1.0, 0.25: 1.0, 0.5: 1.0, 0.75: 1.0}},
        // 0.55: {1000: {0.1: 1.0, 0.25: 1.0, 0.5: 1.0, 0.75: 1.0}},
        // 0.6: {1000: {0.1: 1.0, 0.25: 1.0, 0.5: 1.0, 0.75: 1.0}}

        /* square
        var sqkR = 1.0
        val apkR = mutableMapOf<Double, Double>()
        val aR = mutableMapOf<Double, MutableMap<Double, Double>>()
        val apaR = mutableMapOf<Double, Double>()
        val oneaR = mutableMapOf<Double, MutableMap<Double, Double>>()
        val oneapR = mutableMapOf<Double, Double>()

        for (eta in etal) {
            apkR[eta] = 1.0
            apaR[eta] = 1.0
            oneapR[eta] = 1.0
            aR[eta] = mutableMapOf()
            oneaR[eta] = mutableMapOf()
            for (d in dl) {
                aR[eta]!![d] = 1.0
                oneaR[eta]!![d] = 1.0
            }
        }

        var items = 0
        for (theta in thetal) {
            for (N in Nl) {
                for (b in blanks) {
                    items += 1
                    for (eta in etal) {
                        apkR[eta] =
                            apkR[eta]!! * (resultsApkB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble() / best[theta]!![N]!![b]!!)
                        apaR[eta] =
                            apaR[eta]!! * (resultsApaB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble() / best[theta]!![N]!![b]!!)
                        oneapR[eta] =
                            oneapR[eta]!! * (resultsCompB[theta]!![N]!![b]!![eta]!!["rej_N"]!!.toDouble() / best[theta]!![N]!![b]!!)
                        for (d in dl) {
                            aR[eta]!![d] =
                                aR[eta]!![d]!! * (resultsAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble() / best[theta]!![N]!![b]!!)
                            oneaR[eta]!![d] =
                                oneaR[eta]!![d]!! * (resultsCompAB[theta]!![N]!![b]!![eta]!![d]!!["rej_N"]!!.toDouble() / best[theta]!![N]!![b]!!)
                        }
                    }
                }
            }
        }

        println("items=$items")

         */
    }
}