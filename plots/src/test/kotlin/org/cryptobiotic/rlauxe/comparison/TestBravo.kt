package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.plots.plotNTsamplesPct
import org.cryptobiotic.rlauxe.plots.plotNTsamples
import org.cryptobiotic.rlauxe.plots.plotNTsuccessPct
import org.cryptobiotic.rlauxe.sim.runTestRepeated
import org.cryptobiotic.rlauxe.util.GenSampleMeanWithReplacement
import org.cryptobiotic.rlauxe.util.GenSampleMeanWithoutReplacement
import kotlin.test.Test

// Test Alpha running BRAVO. Compare against UnifiedEvaluation tables (with replacement only)
// A Unified Evaluation of Two-Candidate Ballot-Polling Election Auditing Methods	Huang; 12 May 2021
class TestBravo  {
    val df = "%5d"

    @Test
    fun testBravo() {
        val N = 20_000
        val m = 100
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
        val m = 2000
        val results = mutableListOf<SRT>()
        trueMeans.forEach { trueMean ->
            val estimFn = FixedMean(eta0)
            val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = 1.0, withoutReplacement = withoutReplacement)
            val sampler = if (withoutReplacement) GenSampleMeanWithoutReplacement(N, trueMean) else GenSampleMeanWithReplacement(N, trueMean)

            val rr = runTestRepeated(
                drawSample = sampler,
                maxSamples = m,
                testParameters = mapOf("eta0" to eta0),
                ntrials = ntrials,
                testFn=alpha,
            )

            //val rr = runBravo(N, m, eta0, it, withoutReplacement, nrepeat)
            // N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0, rr: RunTestRepeatedResult
            results.add(rr.makeSRT(N, eta0, 0.0))
        }
        return results
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
    fun testWithSampleMean() {
        val sampleMeans = listOf(.505, .51, .52, .53, .55, .60)
        val N = 20_000
        val m = 4000
        val nrepeat = 100

        sampleMeans.forEach { mean ->
            val result = runBravoRepeat(eta0 = 0.0, trueMeans = sampleMeans, withoutReplacement = false, ntrials = nrepeat )

                val sampler = SampleMeanWithReplacement(N, mean)
            val result = runBravo(sampler, m, mean, nrepeat, false)
            val voteDiff = N * (result.eta0 - mean)
            println(" testWithSampleMean ratio=${"%5.4f".format(mean)} "+
                    "eta0=${"%5.4f".format(result.eta0)} " +
                    "voteDiff=${"%4d".format(voteDiff.toInt())} " +
                    "sampleCount=${df.format(result.avgSamplesNeeded())} " +
                    // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                    "nrepeat=${result.percentHist!!.cumulPct()}" +
                    // "fail=${(result.failPct * nrepeat).toInt()} " +
                    "status=${result.status} "
            )
        }
    }

    // in this scenario, the ratio that the sample was generated by is the "true mean", and the generated samples reflect errors.
    // the diff between the two must have some spread, eg normal dist ??
    fun runBravo(drawSample : SampleFn, m: Int, trueMean: Double, ntrials:Int = 1, withoutReplacement: Boolean = true): RunTestRepeatedResult {
        var totalSamples = 0
        var sampleMeanSum = 0.0
        var fail = 0
        var nsuccess = 0

        val N = drawSample.N()
        val eta0 = drawSample.sampleMean()
        val estimFn = FixedMean(eta0)
        val alpha = AlphaMart(
            estimFn = estimFn,
            N = N,
            upperBound = 1.0,
            withoutReplacement = withoutReplacement
        )
        val hist = Deciles(N)
        val welford = Welford()

        repeat(ntrials) {
            drawSample.reset()
            val testH0Result = alpha.testH0(m, true) { drawSample.sample() }
            sampleMeanSum += testH0Result.sampleMean
            if (testH0Result.status.fail) {
                fail++
            } else {
                nsuccess++
                welford.update(testH0Result.sampleCount.toDouble())
                hist.add(testH0Result.sampleCount)
                totalSamples += testH0Result.sampleCount
            }
        }

        return RunTestRepeatedResult(
            eta0 = eta0,
            N = N,
            totalSamples,
            nsuccess,
            ntrials,
            welford.result().second,
            percentHist = hist
        )
    }

     */

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
    override fun eta(prevSamples: Samples): Double {
        return eta0
    }
}