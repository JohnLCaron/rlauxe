package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.AdaptiveComparison
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.ErrorRates
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.unittest.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class TestCorla {

    @Test
    fun testOne() {
        val p1 = .01
        val p2 = .01

        val N = 20000
        val margin = .05
        val ntrials = 100
        val riskLimit = .05

        val theta = margin2mean(margin)
        val cvrs = makeCvrsByExactMean(N, theta)
        val compareAssorter = makeStandardComparisonAssorter(theta, N)
        val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2 = p2, p1 = p1, withoutReplacement = false)

        val corla = Corla(N = N, riskLimit=riskLimit, reportedMargin=compareAssorter.assorter.reportedMargin(), noerror=compareAssorter.noerror,
            p1 = p1, p2 = p2, p3 = 0.0, p4 = 0.0)

        val corlaResult = runTestRepeated(
            drawSample = sampler,
            ntrials = ntrials,
            testFn = corla,
            testParameters = mapOf("p1" to p1, "p2" to p2),
            margin = margin,
            Nc=N,
            )
        println("  corla = ${corlaResult}")
    }


    @Test
    fun testCorlaVsAdaptiveBets() {
        val p1oracle = listOf(.001, .01)
        val p2oracle = listOf(.0001, .001, .01)
        val p1priors = listOf(.001, .01)
        val p2priors = listOf(.0001, .001)
        val d1 = 100
        val eps = .00001

        val N = 20000
        val margin = .05
        val ntrials = 100
        val riskLimit = .05

        val ratios = mutableListOf<Double>()
        val ratioSuccesses = mutableListOf<Double>()
        for (p2o in p2oracle) {
            for (p1o in p1oracle) {
                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAssorter = makeStandardComparisonAssorter(theta, N)
                for (p1prior in p1priors) {
                    for (p2prior in p2priors) {
                        // generate with the oracle, or true rates
                        val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2 = p2o, p1 = p1o, withoutReplacement = false)
                        val upperBound = compareAssorter.upperBound
                        println("testCorlaVsAdaptiveBets: p1=${p1o}  p2=${p2o} p1prior=${p1prior}  p2prior=${p2prior}")

                        // pass the prior rates to the betting function
                        val adaptive = AdaptiveComparison(
                            Nc = N,
                            withoutReplacement = false,
                            a = compareAssorter.noerror,
                            d = d1,
                            ErrorRates(p2prior, p1prior, 0.0, 0.0),
                            eps=eps,
                        )
                        val betting =
                            BettingMart(bettingFn = adaptive, Nc = N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = false)

                        val bettingResult = runTestRepeated(
                            drawSample = sampler,
                            ntrials = ntrials,
                            testFn = betting,
                            testParameters = mapOf("p1" to p1o, "p2" to p2o),
                            margin = margin,
                            Nc=N,
                        )
                        println(" bettingResult = ${bettingResult}")

                        val corla = Corla(N = N, riskLimit=riskLimit, reportedMargin=compareAssorter.assorter.reportedMargin(), noerror=compareAssorter.noerror,
                            p1 = p1prior, p2 = p2prior, p3 = 0.0, p4 = 0.0) // not using the priors

                        val corlaResult = runTestRepeated(
                            drawSample = sampler,
                            ntrials = ntrials,
                            testFn = corla,
                            testParameters = mapOf("p1" to p1o, "p2" to p2o),
                            margin = margin,
                            Nc=N,
                            )
                        val ratio = bettingResult.avgSamplesNeeded().toDouble() / corlaResult.avgSamplesNeeded()
                        val ratioSuccess = corlaResult.successPct() / bettingResult.successPct()
                        println(" corlaResult = ${corlaResult} ratio bet/corla=$ratio ratio success=$ratioSuccess")
                        ratios.add(ratio)
                        ratioSuccesses.add(ratioSuccess)

                        println() // "took ${stopwatch}")
                    }
                }
            }
        }
        println("geometricMean = ${geometricMean(ratios)} of ${ratios.size} sample ratios")
        println("geometricMean = ${geometricMean(ratioSuccesses)} of ${ratioSuccesses.size} success ratios")
    }
}