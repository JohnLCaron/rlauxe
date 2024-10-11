package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doubleIsClose

/**
 * Finds the betting martingale for the hypothesis that the population
 * mean is less than or equal to t using a martingale method,
 * for a population of size N, based on a series of draws x.
 */
class BettingMart(
    val bettingFn : BettingFn,
    val N: Int,             // number of ballot cards
    val withoutReplacement: Boolean = true,
    val noerror: Double, // for comparison assorters who need rate counting
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
) {
    private val showDetail = false
    val welford = Welford()

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

/* original python
    def betting_mart(self, x: np.array, **kwargs) -> tuple[float, np.array]:
        """
        Finds the betting martingale for the hypothesis that the population
        mean is less than or equal to t using a martingale method,
        for a population of size N, based on a series of draws x.

        **The draws must be in random order**, or the sequence is not a supermartingale under the null

        If N is finite, assumes the sample is drawn without replacement
        If N is infinite, assumes the sample is with replacement

        Parameters
        ----------
        x: list corresponding to the data
        attributes used:
            keyword arguments for bet() and for this function
            u: float > 0 (default 1)
                upper bound on the population
            eta: float in (t,u] (default u*(1-eps))
                value parametrizing the bet. Use alternative hypothesized population mean for polling audit
                or a value nearer the upper bound for comparison audits


        Returns
        -------
        p: float
            sequentially valid p-value of the hypothesis that the population mean is less than or equal to t
        p_history: numpy array
            sample by sample history of p-values. Not meaningful unless the sample is in random order.
        """
        N = self.N
        t = self.t
        u = self.u
        atol = kwargs.get("atol", 2 * np.finfo(float).eps)
        rtol = kwargs.get("rtol", 10**-6)
        _S, Stot, _j, m = self.sjm(N, t, x)
        x = np.array(x)
        with np.errstate(divide="ignore", invalid="ignore", over="ignore"):
            lam = self.bet(x)
            terms = np.cumprod(1 + lam * (x - m))
        terms[m > u] = 0  # true mean is certainly less than hypothesized
        terms[np.isclose(0, m, atol=atol)] = 1  # ignore
        terms[np.isclose(u, m, atol=atol, rtol=rtol)] = 1  # ignore
        terms[np.isclose(0, terms, atol=atol)] = (
            1  # martingale effectively vanishes; p-value 1
        )
        terms[m < 0] = np.inf  # true mean certainly greater than hypothesized
        terms[-1] = (
            np.inf if Stot > N * t else terms[-1]
        )  # final sample makes the total greater than the null
        return min(1, 1 / np.max(terms)), np.minimum(1, 1 / terms)
 */

/* ai convert from python
fun betting_mart(
    x: ArrayList<Double>,
    atol: Double = 2 * Double.MIN_VALUE,
    rtol: Double = 1e-6,
): Pair<Double, ArrayList<Double>> {

    val N = this.N
    val t = this.t
    val u = this.u
    val values = this.sjm(N, t, x)
    val _S = values._S
    val Stot = values.Stot
    val _j = values._j
    var m = values.m
    val terms = ArrayList<Double>()

    for (i in x.indices) {
        try {
            val lam = this.bet(x[i])
            terms.add(1 + lam * (x[i] - m[i]))
        } catch (e: Exception) {
            terms.add(1.0)
        }
    }

    for (i in terms.indices) {
        when {
            m[i] > u -> terms[i] = 0.0
            isCloseToZero(m[i], atol) -> terms[i] = 1.0
            isCloseToU(m[i], u, atol, rtol) -> terms[i] = 1.0
            isCloseToZero(terms[i], atol) -> terms[i] = 1.0
            m[i] < 0 -> terms[i] = Double.POSITIVE_INFINITY
            else -> terms[i] = if (Stot > N * t) Double.POSITIVE_INFINITY else terms[i]
        }
    }

    return Pair(minOf(1.0, 1 / terms.maxOrNull()!!), terms.map { minOf(1.0, 1 / it) } as ArrayList<Double>)
}
*/

    // TODO merge with alpha_mart?
    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    fun testH0(maxSample: Int, terminateOnNullReject: Boolean, showDetails: Boolean = false, drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = 1.0     // – T ← 1: test statistic
        var mj = 0.5                // – m = µ_j = 1/2: population mean under the null hypothesis = H0
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum

        // keep series for debugging, remove for production
        val xs = mutableListOf<Double>()
        val mjs = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()
        val bets = mutableListOf<Double>()
        val tjs = mutableListOf<Double>()
        val testStatistics = mutableListOf<Double>()

        while (sampleNumber < maxSample) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1
            xs.add(xj)
            require(xj >= 0.0)
            require(xj <= upperBound)

            // AlphaMart val etaj = estimFn.eta(prevSamples)
            // EstimFn could be converted to BettingFn
            val lamj = bettingFn.bet(prevSamples)
            bets.add(lamj)

            // population mean under the null hypothesis
            mj = populationMeanIfH0(N, withoutReplacement, prevSamples)
            mjs.add(mj)

            // TODO
            //     for (i in terms.indices) {
            //        when {
            // 1           m[i] > u -> terms[i] = 0.0   # true mean is certainly less than 1/2
            // 2           isCloseToZero(m[i], atol) -> terms[i] = 1.0
            // 3           isCloseToU(m[i], u, atol, rtol) -> terms[i] = 1.0
            // 4           isCloseToZero(terms[i], atol) -> terms[i] = 1.0
            // 5           m[i] < 0 -> terms[i] = Double.POSITIVE_INFINITY # true mean certainly greater than 1/2
            // 6           else -> terms[i] = if (Stot > N * t) Double.POSITIVE_INFINITY else terms[i]
            //        }
            //    }

            if (mj > upperBound || mj < 0.0) { // 1, 5
                break
            }

            val tj = if (doubleIsClose(0.0, mj) || doubleIsClose(upperBound, mj)) { // 2, 3
                1.0 // TODO look at this
            } else {
                // AlphaMart
                // val ttj = (xj * etaj / mj + (upperBound - xj) * (upperBound - etaj) / (upperBound - mj)) / upperBound // ALPHA eq 4

                // terms[i] = (1 + λi (Xi − µi )) ALPHA eq 10
                val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
                if (doubleIsClose(ttj, 0.0)) 1.0 else ttj // TODO look at this // 4
            }
            tjs.add(tj)
            testStatistic *= tj // Tj ← Tj-1 & tj
            testStatistics.add(testStatistic)

            if (showDetail) println("    $sampleNumber = $xj m=$mj lamj = $lamj tj=$tj, Tj = $testStatistic")

            // – S ← S + Xj
            prevSamples.addSample(xj)

            val pvalue = 1.0 / testStatistic
            pvalues.add(pvalue)

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }

        if (showDetails) {
            println("xs = ${xs}")
            println("mujs = ${mjs}")
            println("bets = ${bets}")
            println("tjs = ${tjs}")
            println("Tjs = ${testStatistics}")
        }

        val status = when {
            (sampleNumber == maxSample) -> {
                TestH0Status.LimitReached
            }
            (mj < 0.0) -> {
                TestH0Status.SampleSum
            }
            (mj > upperBound) -> {
                TestH0Status.AcceptNull
            }
            else -> {
                TestH0Status.StatRejectNull
            }
        }

        return TestH0Result(status, sampleNumber, prevSamples.mean(), pvalues, bets, mjs)
    }
}
