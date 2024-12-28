package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.plots.plotNTsamplesPct
import org.cryptobiotic.rlauxe.plots.plotNTsamples
import org.cryptobiotic.rlauxe.plots.plotNTsuccessPct
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.sampling.Sampler
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.random.Random
import kotlin.test.Test

// Test Alpha running BRAVO. Compare against UnifiedEvaluation tables (with replacement only)
// A Unified Evaluation of Two-Candidate Ballot-Polling Election Auditing Methods	Huang; 12 May 2021
class GenBravoResults  {

    @Test
    fun testBravo() {
        runBravoRepeat(eta0 = .55, trueMeans=listOf(.55), ntrials=1)
    }

    @Test
    fun testAgainstUnifiedWithReplacement() {
        val eta0 = listOf(.7, .55, .51)
        val trueMeans = listOf(.52, .55, .60, .64, .70)

        val results = mutableListOf<SRT> ()
        eta0.forEach { results.addAll( runBravoRepeat(it, trueMeans, false)) }

        plotNTsuccessPct(results, "BravoWith")
        plotNTsamples(results, "BravoWith")
        plotNTsamplesPct(results, "BravoWith")
    }

    @Test
    fun testAgainstUnifiedWithoutReplacement() {
        val eta0 = listOf(.7, .55, .51)
        val trueMeans = listOf(.52, .55, .60, .64, .70)
        println("testAgainstUnifiedWithoutReplacement")

        val results = mutableListOf<SRT> ()
        eta0.forEach { results.addAll(runBravoRepeat(it, trueMeans, true)) }

        plotNTsuccessPct(results, "BravoWithout")
        plotNTsamples(results, "BravoWithout")
        plotNTsamplesPct(results, "BravoWithout")
    }

    //     fun runBravo(N : Int, m: Int, eta0 : Double, trueMean: Double, withoutReplacement: Boolean, ntrials:Int = 1): RunTestRepeatedResult {
    fun runBravoRepeat(eta0: Double, trueMeans: List<Double>, withoutReplacement: Boolean = true, ntrials: Int = 100 ): List<SRT> {
        val N = 20_000
        val m = 2000  // maxSamples
        val results = mutableListOf<SRT>()
        trueMeans.forEach { trueMean ->
            val estimFn = FixedMean(eta0)
            val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = 1.0, withoutReplacement = withoutReplacement)
            val sampler = if (withoutReplacement) GenSampleMeanWithoutReplacement(m, trueMean) else GenSampleMeanWithReplacement(m, trueMean)

            val rr = runTestRepeated(
                drawSample = sampler,
                // maxSamples = m,
                testParameters = mapOf("eta0" to eta0),
                ntrials = ntrials,
                testFn=alpha,
                margin = mean2margin(eta0),
                Nc=N,
                )

            //val rr = runBravo(N, m, eta0, it, withoutReplacement, nrepeat)
            // N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0, rr: RunTestRepeatedResult
            results.add(rr.makeSRT(eta0, 0.0))
        }
        return results
    }

}

// – Set η0
//    For polling audits, η0 could be the reported mean value of the assorter.
//	    For instance, for the assertion corresponding to checking whether w got more votes than ℓ,
//	      η0 = (Nw + Nc /2)/N , where Nw is the number of votes reported for w , Nℓ is the
//	   number of votes reported for ℓ, and Nc = N − Nw − Nℓ is the number of ballot cards
//	   reported to have a vote for some other candidate or no valid vote in the contest.
//    For comparison audits, η0 can be based on assumed or historical rates of overstatement errors.
//
// – Define the function to update η based on the sample,
//	  e.g, η(i, X i−1 ) = ((d * η0 + S)/(d + i − 1) ∨ (eps(i) + µi )) ∧ u,    (2.5.2, eq 14, "truncated shrinkage")
//	    where S = Sum i−1 k=1 (Xk) is the sample sum of the first i − 1 draws
//	    and eps(i) = c/ sqrt(d + i − 1)
//	 set any free parameters in the function (e.g., d and c in this example). The only requirement is that
//	  η(i, X i−1 ) ∈ (µi , u), where µi := E(Xi |X i−1 ) is computed under the null.

// ηi = η0 := Nw /(Nw + Nℓ ), where Nw is the number of votes reported for candidate w and
// Nℓ is the number of votes reported for candidate ℓ: η is not updated as data are collected
class FixedMean(val eta0: Double): EstimFn {
    override fun eta(prevSampleTracker: SampleTracker): Double {
        return eta0
    }
}

// generate random values with given mean
class GenSampleMeanWithReplacement(val N: Int, ratio: Double): Sampler {
    val samples = generateSampleWithMean(N, ratio)
    override fun sample(): Double {
        val idx = Random.nextInt(N) // with Replacement
        return samples[idx]
    }
    override fun reset() {
        // noop
    }
    override fun maxSamples() = N
}

class GenSampleMeanWithoutReplacement(val N: Int, val ratio: Double): Sampler {
    var samples = generateSampleWithMean(N, ratio)
    var index = 0
    override fun sample(): Double {
        return samples[index++]
    }
    override fun reset() {
        samples = generateSampleWithMean(N, ratio)
        index = 0
    }
    override fun maxSamples() = N
}


// generate a sample thats approximately mean = theta
fun generateUniformSample(N: Int) : DoubleArray {
    return DoubleArray(N) {
        Random.nextDouble(1.0)
    }
}

// generate a sample thats approximately mean = theta
fun generateSampleWithMean(N: Int, ratio: Double) : DoubleArray {
    return DoubleArray(N) {
        val r = Random.nextDouble(1.0)
        if (r < ratio) 1.0 else 0.0
    }
}
