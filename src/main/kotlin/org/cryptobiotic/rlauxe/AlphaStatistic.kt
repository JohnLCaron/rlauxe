package org.cryptobiotic.rlauxe

private val showDetail = false

// estimate the population mean for the jth sample from the previous j-1 samples
interface EstimFn {
    // return eta estimate
    // dont pass the empty list, start with j = 2 with prevSamples.size = 1
    fun eta(prevSamples: List<Double>): Double
}

enum class TestH0Status {
    RejectNull,
    SampleSum, // SampleSum > N * t
    LimitReached,
}

data class TestH0Result(val status: TestH0Status, val sampleCount: Int, val sampleMean: Double)

// ALPHA paper, "ALPHA: AUDIT THAT LEARNS FROM PREVIOUSLY HAND-AUDITED BALLOTS" 12 Aug 2022
class AlphaStatistic(
    val estimFn : EstimFn,
    val N: Int, // number of ballot cards in the population of cards from which the sample is drawn
    val withoutReplacement: Boolean = true,
    val risk_limit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
    val t: Double = 0.5,        // the hypothesized mean "under the null". TODO is it ever not 1/2 ?
    val isPolling: Boolean = false,
) {
    val Tthreshold = 1.0 / risk_limit

    init {
        // 3. Pseudo-algorithm for ballot-level comparison and ballot-polling audits
        // • Set audit parameters:
        //  – Select the risk limit α ∈ (0, 1)
        //  - decide whether to sample with or without replacement.
        //  – Set upper as appropriate for the assertion under audit.
        //  – Set N to the number of ballot cards in the population of cards from which the sample is drawn.
        // (the rest is all in the estimFn)
        //  – Set η0
        //    For polling audits, η0 could be the reported mean value of the assorter.
        //	    For instance, for the assertion corresponding to checking whether w got more votes than ℓ,
        //	      η0 = (Nw + Nc /2)/N , where Nw is the number of votes reported for w , Nℓ is the
        //	   number of votes reported for ℓ, and Nc = N − Nw − Nℓ is the number of ballot cards
        //	   reported to have a vote for some other candidate or no valid vote in the contest.
        //     For comparison audits, η0 can be based on assumed or historical rates of overstatement errors.
        //
        //  – Define the function to update η based on the sample,
        //	  e.g, η(i, X i−1 ) = ((d * η0 + S)/(d + i − 1) ∨ (eps(i) + µi )) ∧ u,    (2.5.2, eq 14, "truncated shrinkage")
        //	     where S = Sum i−1 k=1 (Xk) is the sample sum of the first i − 1 draws
        //	     and eps(i) = c/ sqrt(d + i − 1)
        //	  set any free parameters in the function (e.g., d and c in this example). The only requirement is that
        //	     η(i, X i−1 ) ∈ (µi , u), where µi := E(Xi |X i−1 ) is computed under the null.
    }

    // drawSample() returns the assorter value
    // return the number of ballots sampled. if equal to maxSample, then the rla failed and must do a hand recount.
    // for debugging, we want to know the sample mean.
    fun testH0(maxSample: Int, drawSample : () -> Double) : TestH0Result {

        // • Initialize variables
        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = 1.0     // – T ← 1: test statistic
        var sampleSum = 0.0        // – S ← 0: sample sum
        var populationMeanIfH0 = t // – m = µ_j = 1/2: population mean under the null hypothesis = H0

        var status = TestH0Status.RejectNull
        val sampleAssortValues = mutableListOf<Double>()

        // • While T < 1/α and not all ballot cards have been audited:
        while (sampleNumber < maxSample && testStatistic < Tthreshold ) {
            // – Draw a ballot at random
            // – Determine Xj by applying the assorter to the selected ballot card (and the CVR, for comparison audits)
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1

            // note using sample list not including current sample
            val etaj = estimFn.eta(sampleAssortValues)
            sampleAssortValues.add(xj)

            // – If the sample is drawn without replacement, m ← (N/2 − S)/(N − j + 1)
            // LOOK moved from the paper, where it was below S ← S + Xj.
            // sampleSum does not have current sample in it, so its = sampleSumMinusOne
            populationMeanIfH0 = this.populationMeanIfH0(sampleNumber, sampleSum)

            if (sampleSum > N * t) { // aka populationMeanIfNull < 0
                status = TestH0Status.SampleSum // true mean certainly greater than null
                break
            }

            // This is eq 4 of ALPHA, p.5 :
            //      T_j = T_j-1 / u * ((X_j * eta_j / µ_j) + (u - X_j) * (u - eta_j) / ( u - µ_j))
            //  T is the "ALPHA supermartingale"
            //  u = upperBound
            //  X_j = jth sample
            //  eta_j = ηj = estimate of population mean for H1 = estimFn.eta(X^j-1)
            //  µ_j = m = The mean of the population after each draw for H0.

            // – If m < 0, T ← ∞. Otherwise, T ← T / u * ( Xj * η(j,S)/m + (u - Xj) * (u−η(j,S))/(u-m))
            //    already tested for m < 0
            //    tj = ( Xj * η(j,S)/m + (u - Xj) * (u−η(j,S))/(u-m)) / u
            //    Tj ← Tj-1 * tj
            val tj = (xj * etaj / populationMeanIfH0 + (upperBound - xj) * (upperBound - etaj) / (upperBound - populationMeanIfH0)) / upperBound
            testStatistic *= tj // Tj ← Tj-1 & tj
            if (showDetail) println("    $sampleNumber = $xj etaj = $etaj tj=$tj, Tj = $testStatistic")

            // – S ← S + Xj
            sampleSum += xj

            // – If desired, break and conduct a full hand count instead of continuing to audit.
        }
        if (sampleNumber == maxSample) status = TestH0Status.LimitReached
        val sampleMean = sampleSum / sampleNumber
        return TestH0Result(status, sampleNumber, sampleMean)
    }

    // population mean under the null hypothesis that “the average of this list is not greater than 1/2”
    fun populationMeanIfH0(sampleNum: Int, sampleSumMinusOne: Double): Double {
        // LOOK detect if it goes negetive. sampleNum < N. Neg if (N * t < sampleSum)
        return if (withoutReplacement) (N * t - sampleSumMinusOne) / (N - sampleNum + 1) else t
    }
}
