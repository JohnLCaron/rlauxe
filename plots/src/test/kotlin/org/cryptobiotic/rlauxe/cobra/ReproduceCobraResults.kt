package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.ClcaNoErrorSampler
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.sampling.ClcaAttackSampler
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertTrue

class ReproduceCobraResults {

    //Fig. 1. Deterministic sample sizes (y-axis; log10 scale) for a comparison audit of a
    //plurality contest with various diluted margins (x-axis) and risk limits (colors), with no
    //error in CVRs and a maximal bet of λ = 2 on every draw
    @Test
    fun testFigure1() {
        val alphas = listOf(.001, .01, .05, .10)
        val margins = listOf(.0001, .0002, .0025, .005, .0075, .01, .03, .03, .04, .05, )
        val N = 150000
        val ntrials = 100

        val ratios = mutableListOf<Double>()
        for (alpha in alphas.reversed()) {
            for (margin in margins.reversed()) {
                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAssorter = makeStandardComparisonAssorter(theta, N)
                val sampler = ClcaNoErrorSampler(compareAssorter.contest.id, cvrs, compareAssorter)
                val upperBound = compareAssorter.upperBound
                println("testFigure1: alpha=${alpha} margin=${margin} a=${compareAssorter.noerror}")

                val fixed = FixedBet(2.0)
                val betting =
                    BettingMart(
                        riskLimit = alpha, bettingFn = fixed, Nc = N, withoutReplacement = false,
                        noerror = compareAssorter.noerror, upperBound = upperBound
                    )

                val result = runTestRepeated(
                    drawSample = sampler,
                    // maxSamples = N,
                    ntrials = ntrials,
                    testFn = betting,
                    testParameters = mapOf("alpha" to alpha),
                    margin = margin,
                    Nc=N,
                    )
                println("  result = ${result.status} ${result.avgSamplesNeeded()}")

                val expected = ln(1 / alpha) / ln(2 * compareAssorter.noerror)
                val ratio = result.avgSamplesNeeded().toDouble() / expected
                ratios.add(ratio)
                println("  expected = ${expected}, ratio=$ratio")
                assertTrue(doubleIsClose(1.0, ratio, doublePrecision, 0.01)) // within 1 %
                println()
            }
        }
        val gmean = geometricMean(ratios)
        println("geometricMean = $gmean")
        assertTrue(doubleIsClose(1.0, gmean, 0.002)) // within .2 %
    }

    // sampling with replacement from a population of size N = 10000.
    // At each combination of diluted margin v ∈ {0.05, 0.10, 0.20} and
    // 2-vote overstatement rates p2 ∈ {1.5%, 1%, 0.5%, 0.1%, 0%} we ran 400 simulated
    // comparison audits. We set p1 = 0: no 1-vote overstatements.
    @Test
    fun testTable1() {
        val p2s = listOf(.015, .01, .005, .001, 0.0)
        val margins = listOf(.05, .10, .20)
        val N = 10000
        val ntrials = 400

        val ratios = mutableListOf<Double>()
        for (margin in margins) {
            for (p2 in p2s) {
                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAssorter = makeStandardComparisonAssorter(theta, N)
                val sampleWithErrors =
                    ClcaAttackSampler(cvrs, compareAssorter, p2 = p2, p1 = 0.0, withoutReplacement = false)
                val upperBound = compareAssorter.upperBound
                println("testTable1: margin=${margin} a=${compareAssorter.noerror} p2=${p2}")

                val oracle = OptimalComparisonNoP1(
                    N = N,
                    upperBound = upperBound,
                    // a = compareAssorter.noerror, // TODO how can you not need this?
                    p2 = p2,
                )
                val betting =
                    BettingMart(bettingFn = oracle, Nc = N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = false)

                val result = runTestRepeated(
                    drawSample = sampleWithErrors,
                    // maxSamples = N,
                    ntrials = ntrials,
                    testFn = betting,
                    testParameters = mapOf("p2" to p2),
                    margin = margin,
                    Nc=N,
                    )
                println("  result = ${result.avgSamplesNeeded()} ${result.percentHist}")

                val expected = findTable1Entry(margin, p2)
                if (expected != null) {
                    val ratio = result.avgSamplesNeeded().toDouble() / expected.meanSamples
                    ratios.add(ratio)
                    println("  expected = ${expected.meanSamples}, ${expected.samples90} $ratio")
                    assertTrue(doubleIsClose(1.0, ratio, doublePrecision, 0.12)) // within 12 %
                }
                println()
                // makeSRT(N, theta, 0.0, d, rr = result)
            }
        }
        val gmean = geometricMean(ratios)
        println("geometricMean = $gmean")
        assertTrue(doubleIsClose(1.0, gmean, 0.05))
    }

    // N = 20000 ballots, a diluted margin of 5%, sampling with replacement.
    // 1-vote overstatement rates p1 ∈ {0.1%, 1%},
    // and 2-vote overstatement rates p2 ∈ {0.01%, 0.1%, 1%}.
    // Oracle bets were set using the true values of p1 and p2 in each scenario.
    @Test
    fun testOracleBets() {
        val p1oracle = listOf(.01, .001)
        val p2oracle = listOf(.01, .001, .0001)
        val p1priors = listOf(.01, .001)
        val p2priors = listOf(.001, .0001)

        val N = 20000
        val margin = .05
        val ntrials = 400
        // println("testOracle N=$N margin=$margin theta=$theta ntrials=$ntrials")

        val ratios = mutableListOf<Double>()
        for (p1 in p1oracle) {
            for (p2 in p2oracle) {
                for (p1m in p1priors) {
                    for (p2m in p2priors) {
                        val theta = margin2mean(margin)
                        val cvrs = makeCvrsByExactMean(N, theta)
                        val compareAssorter = makeStandardComparisonAssorter(theta, N)
                        val sampleWithErrors =
                            ClcaAttackSampler(
                                cvrs,
                                compareAssorter,
                                p2 = p2,
                                p1 = p1,
                                withoutReplacement = false
                            )
                        val upperBound = compareAssorter.upperBound
                        println("testTable2OracleBets: p1=${p1}  p2=${p2}")

                        val oracle = OracleComparison(
                            a = compareAssorter.noerror,
                            ClcaErrorRates(p2, p1, 0.0, 0.0)
                        )
                        val betting =
                            BettingMart(bettingFn = oracle, Nc = N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = false)

                        val result = runTestRepeated(
                            drawSample = sampleWithErrors,
                            // maxSamples = N,
                            ntrials = ntrials,
                            testFn = betting,
                            testParameters = mapOf("p1" to p1, "p2" to p2),
                            margin = margin,
                            Nc=N,
                            )
                        println("  result = ${result.avgSamplesNeeded()} ${result.percentHist}")
                        val expected = findTable2Entry(p2 = p2, p1 = p1, p2prior = p2m, p1prior = p1m)
                        if (expected != null) {
                            val ratio = result.avgSamplesNeeded().toDouble() / expected.oracleMean
                            ratios.add(ratio)
                            println("  expected = ${expected.oracleMean}, ${expected.oracle90} $ratio")
                            assertTrue(doubleIsClose(1.0, ratio, doublePrecision, 0.15)) // within 15 %
                        }
                        println()
                    }
                }
            }
        }
        val gmean = geometricMean(ratios)
        println("geometricMean = $gmean")
        assertTrue(doubleIsClose(1.0, gmean, 0.05))
    }

    // We evaluated oracle betting, fixed a priori betting, adaptive betting, and di-
    // versified betting in simulated comparison audits with N = 20000 ballots, a
    // diluted margin of 5%, 1-vote overstatement rates p1 ∈ {0.1%, 1%}, and 2-vote
    // overstatement rates p2 ∈ {0.01%, 0.1%, 1%}.
    // The other methods used prior guesses p̃1 ∈ {0.1%, 1%} and p̃2 ∈ {0.01%, 0.1%}
    // as tuning parameters in different ways. The adaptive method anchored the
    // shrink-trunc estimate p̃ki displayed in equation (4) to p̃k , but updated using
    // past data in the sample. The tuning parameters were d1 := 100, d2 := 1000,
    // eps := 0.001%. The larger value for d2 reflects the fact that very low rates
    // (expected for 2-vote overstatements) are harder to estimate empirically, so the
    // prior should play a larger role.
    @Test
    fun testAdaptiveBets() {
        val p1oracle = listOf(.001, .01)
        val p2oracle = listOf(.0001, .001, .01)
        val p1priors = listOf(.001, .01)
        val p2priors = listOf(.0001, .001)
        val d = 100
        val eps = .00001

        val N = 20000
        val margin = .05
        val ntrials = 400

        val ratios = mutableListOf<Double>()
        for (p2o in p2oracle) {
            for (p1o in p1oracle) {
                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAssorter = makeStandardComparisonAssorter(theta, N)
                for (p1prior in p1priors) {
                    for (p2prior in p2priors) {
                        val stopwatch = Stopwatch()

                        // generate with the oracle, or true rates
                        val sampler = ClcaAttackSampler(
                            cvrs,
                            compareAssorter,
                            p2 = p2o,
                            p1 = p1o,
                            withoutReplacement = false
                        )
                        val upperBound = compareAssorter.upperBound
                        println("testAdaptiveBets: p1=${p1o}  p2=${p2o} p1prior=${p1prior}  p2prior=${p2prior}")

                        // pass the prior rates to the betting function
                        val adaptive = AdaptiveComparison(
                            Nc = N,
                            withoutReplacement = false,
                            a = compareAssorter.noerror,
                            d = d,
                            ClcaErrorRates(p2prior, p1prior, 0.0, 0.0),
                            eps=eps,
                        )
                        val betting =
                            BettingMart(bettingFn = adaptive, Nc = N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = false)

                        val result = runTestRepeated(
                            drawSample = sampler,
                            // maxSamples = N,
                            ntrials = ntrials,
                            testFn = betting,
                            testParameters = mapOf("p1" to p1o, "p2" to p2o),
                            margin = margin,
                            Nc=N,
                            )
                        println("  result = ${result.avgSamplesNeeded()} hist:${result.percentHist}")
                        val expected = findTable2Entry(p2 = p2o, p1 = p1o, p2prior = p2prior, p1prior = p1prior)
                        if (expected != null) {
                            val ratio = result.avgSamplesNeeded().toDouble() / expected.adaptiveMean
                            ratios.add(ratio)
                            println("  expected = ${expected.adaptiveMean}, ${expected.adaptive90} ratio=$ratio")
                            assertTrue(doubleIsClose(1.0, ratio, doublePrecision, 0.15)) // within 15 %
                        }
                        println() // "took ${stopwatch}")
                    }
                }
            }
        }
        // for some reason we do better than for
        val gmean = geometricMean(ratios)
        println("geometricMean = $gmean of ${ratios.size} ratios")
        assertTrue(doubleIsClose(1.0, gmean, 0.05))
    }
}

val table2 = listOf(
    CobraTable2Result(.0001, .001, .0001, .001, 124, 127, 124, 127),
    CobraTable2Result(.0001, .001, .0001, .01, 124, 127, 125, 127),
    CobraTable2Result(.0001, .001, .001, .001, 127, 127, 131, 151),
    CobraTable2Result(.0001, .001, .001, .01, 124, 127, 130, 152),

    CobraTable2Result(.0001, .01, .0001, .001, 174, 229, 166, 229),
    CobraTable2Result(.0001, .01, .0001, .01, 168, 229, 167, 229),
    CobraTable2Result(.0001, .01, .001, .001, 176, 229, 175, 262),
    CobraTable2Result(.0001, .01, .001, .01, 159, 205, 180, 265),

    CobraTable2Result(.001, .001, .0001, .001, 146, 256, 159, 350),
    CobraTable2Result(.001, .001, .0001, .01, 151, 256, 150, 147),
    CobraTable2Result(.001, .001, .001, .001, 147, 256, 146, 182),
    CobraTable2Result(.001, .001, .001, .01, 149, 256, 147, 256),

    CobraTable2Result(.001, .01, .0001, .001, 209, 351, 225, 460),
    CobraTable2Result(.001, .01, .0001, .01, 200, 324, 232, 500),
    CobraTable2Result(.001, .01, .001, .001, 204, 351, 210, 358),
    CobraTable2Result(.001, .01, .001, .01, 208, 324, 205, 341),

    CobraTable2Result(.01, .001, .0001, .001, 526, 996, 1581, 3517),
    CobraTable2Result(.01, .001, .0001, .01, 525, 984, 1585, 3731),
    CobraTable2Result(.01, .001, .001, .001, 528, 1032, 1112, 2710),
    CobraTable2Result(.01, .001, .001, .01, 534, 985, 915, 2294),

    CobraTable2Result(.01, .01, .0001, .001, 999, 1908, 3855, 7811),
    CobraTable2Result(.01, .01, .0001, .01, 1110, 2002, 3477, 7529),
    CobraTable2Result(.01, .01, .001, .001, 1030, 1868, 2795, 5996),
    CobraTable2Result(.01, .01, .001, .01, 1127, 2256, 2437, 5452),
)

data class CobraTable2Result(
    val p2: Double, val p1: Double, val p2prior: Double, val p1prior: Double,
    val oracleMean: Int, val oracle90: Int,
    val adaptiveMean: Int, val adaptive90: Int,
)

fun findTable2Entry(p2: Double, p1: Double, p1prior: Double, p2prior: Double): CobraTable2Result? {
    return table2.find { it.p2 == p2 && it.p1 == p1 && it.p2prior == p2prior && it.p1prior == p1prior }
        ?.let { return it }
}

val table1 = listOf(
    CobraTable1Result(.05, .015, 1283, 2398),
    CobraTable1Result(.05, .01, 482, 813),
    CobraTable1Result(.05, .005, 242, 389),
    CobraTable1Result(.05, .001, 146, 257),
    CobraTable1Result(.05, 0.0, 119, 119),

    CobraTable1Result(.10, .015, 177, 323),
    CobraTable1Result(.10, .01, 131, 233),
    CobraTable1Result(.10, .005, 83, 116),
    CobraTable1Result(.10, .001, 65, 60),
    CobraTable1Result(.10, 0.0, 59, 59),

    CobraTable1Result(.20, .015, 52, 78),
    CobraTable1Result(.20, .01, 42, 57),
    CobraTable1Result(.20, .005, 35, 61),
    CobraTable1Result(.20, .001, 30, 29),
    CobraTable1Result(.20, 0.0, 29, 29),
)

data class CobraTable1Result(
    val margin: Double, val p2: Double, val meanSamples: Int, val samples90: Int,
)

fun findTable1Entry(margin: Double, p2: Double): CobraTable1Result? {
    return table1.find { it.p2 == p2 && it.margin == margin }
        ?.let { return it }
}

// this is optimal_comparison_noP1, a bet, not a sample estimate.
// see cobra p 5
fun optimal_comparison(alpha: Double, u: Double, rate_error_2: Double = 1e-4): Double {
    /*
    The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
    for which overstatement assorters take a small number of possible values and are concentrated
    on a single value when the CVRs have no errors.

    Let p0 be the rate of error-free CVRs, p1=0 the rate of 1-vote overstatements,
    and p2= 1-p0-p1 = 1-p0 the rate of 2-vote overstatements. Then

    eta = (1-u*p0)/(2-2*u) + u*p0 - 1/2, where p0 is the rate of error-free CVRs.

    Translating to p2=1-p0 gives:

    eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.

    Parameters
    ----------
    x: input data
    rate_error_2: hypothesized rate of two-vote overstatements

    Returns
    -------
    eta: estimated alternative mean to use in alpha
    */

    // python doesnt check (2 - 2 * self.u) != 0; self.u = 1
    if (u == 1.0)
        throw RuntimeException("optimal_comparison: u ${u} must != 1")

    val p2 = rate_error_2 // getattr(self, "rate_error_2", 1e-4)  // rate of 2-vote overstatement errors
    val bet = (1 - u * (1 - p2)) / (2 - 2 * u) + u * (1 - p2) - .5
    // 1 / alpha = bet ^ size
    val term1 = -ln(alpha)
    val term2 = ln(bet)
    val size = -ln(alpha) / ln(bet)
    return size
}
