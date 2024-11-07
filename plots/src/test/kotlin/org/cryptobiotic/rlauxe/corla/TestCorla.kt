package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.AdaptiveComparison
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.util.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.*
import org.cryptobiotic.rlauxe.util.*
import org.junit.jupiter.api.Test

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
        val compareAssorter = makeStandardComparisonAssorter(theta)
        val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2 = p2, p1 = p1, withoutReplacement = false)

        val corla = Corla(N = N, riskLimit=riskLimit, reportedMargin=compareAssorter.margin, noerror=compareAssorter.noerror,
            p1 = p1, p2 = p2, p3 = 0.0, p4 = 0.0)

        val corlaResult = runTestRepeated(
            drawSample = sampler,
            maxSamples = N,
            ntrials = ntrials,
            testFn = corla,
            testParameters = mapOf("p1" to p1, "p2" to p2, "margin" to margin),
            showDetails = false,
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
                val compareAssorter = makeStandardComparisonAssorter(theta)
                for (p1prior in p1priors) {
                    for (p2prior in p2priors) {
                        val stopwatch = Stopwatch()

                        // generate with the oracle, or true rates
                        val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2 = p2o, p1 = p1o, withoutReplacement = false)
                        val upperBound = compareAssorter.upperBound
                        println("testCorlaVsAdaptiveBets: p1=${p1o}  p2=${p2o} p1prior=${p1prior}  p2prior=${p2prior}")

                        // pass the prior rates to the betting function
                        val adaptive = AdaptiveComparison(
                            N = N,
                            withoutReplacement = false,
                            upperBound = upperBound,
                            a = compareAssorter.noerror,
                            d1 = d1,
                            d2 = d1,
                            p1 = p1prior,
                            p2 = p2prior,
                            p3 = 0.0,
                            p4 = 0.0,
                            eps=eps,
                        )
                        val betting =
                            BettingMart(bettingFn = adaptive, N = N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = false)

                        val bettingResult = runTestRepeated(
                            drawSample = sampler,
                            maxSamples = N,
                            ntrials = ntrials,
                            testFn = betting,
                            testParameters = mapOf("p1" to p1o, "p2" to p2o, "margin" to margin),
                            showDetails = false,
                        )
                        println(" bettingResult = ${bettingResult}")

                        val corla = Corla(N = N, riskLimit=riskLimit, reportedMargin=compareAssorter.margin, noerror=compareAssorter.noerror,
                            p1 = p1prior, p2 = p2prior, p3 = 0.0, p4 = 0.0) // not using the priors

                        val corlaResult = runTestRepeated(
                            drawSample = sampler,
                            maxSamples = N,
                            ntrials = ntrials,
                            testFn = corla,
                            testParameters = mapOf("p1" to p1o, "p2" to p2o, "margin" to margin),
                            showDetails = false,
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

    @Test
    fun genCorlaPlot() {
        val stopwatch = Stopwatch()

        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val N = 10000
        val ntrials = 1000

        val tasks = mutableListOf<CorlaTask>()
        var taskCount = 0
        reportedMeans.forEach { mean ->
            p2s.forEach { p2 ->
                // the cvrs get generated with this exact margin.
                // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
                val cvrs = makeCvrsByExactMean(N, mean)
                tasks.add(
                    CorlaTask(
                        idx=taskCount++,
                        N=N,
                        cvrMean = mean,
                        cvrs = cvrs,
                        p1 = 0.0,
                        p2 = p2,
                    )
                )
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/corla/plotCorla${ntrials}.csv")

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "task")} of $ntrials trials each")
    }

}