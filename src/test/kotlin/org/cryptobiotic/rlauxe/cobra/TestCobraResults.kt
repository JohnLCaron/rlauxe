package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.core.OptimalComparisonNoP1
import org.cryptobiotic.rlauxe.core.doubleIsClose
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.margin2theta
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.sim.runBettingMartRepeated
import org.cryptobiotic.rlauxe.util.OptimalComparison
import kotlin.test.Test
import kotlin.test.assertTrue

// Try to reproduce COBRA table 2 results
class TestCobraResults {

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
                val theta = margin2theta(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAssorter = makeStandardComparisonAssorter(theta)
                val sampleWithErrors = ComparisonWithErrorRates(cvrs, compareAssorter, p2=p2, p1=0.0, withoutReplacement=false)
                val upperBound = compareAssorter.upperBound
                println("testTable1: margin=${margin} a=${compareAssorter.noerror} p2=${p2}")

                val oracle = OptimalComparisonNoP1(
                    N = N,
                    upperBound = upperBound,
                    // a = compareAssorter.noerror, // TODO how can you not need this?
                    p2 = p2,
                )
                val betting = BettingMart(bettingFn = oracle, N = N, upperBound = upperBound, withoutReplacement = false)

                // fun runBettingMartRepeated(
                //    drawSample: SampleFn,
                //    maxSamples: Int,
                //    ntrials: Int,
                //    bettingMart: BettingMart,
                //    testParameters: Map<String, Double>,
                //    terminateOnNullReject: Boolean = true,
                //    showDetail: Boolean = false,
                val result = runBettingMartRepeated(
                    drawSample = sampleWithErrors,
                    maxSamples = N,
                    ntrials = ntrials,
                    bettingMart = betting,
                    testParameters = mapOf("p2" to p2, "margin" to margin)
                )
                println("  result = ${result.avgSamplesNeeded()} ${result.percentHist}")

                val expected = findTable1Entry(margin, p2)
                if (expected != null) {
                    val ratio = result.avgSamplesNeeded().toDouble() / expected.meanSamples
                    ratios.add(ratio)
                    println("  expected = ${expected.meanSamples}, ${expected.samples90} $ratio")
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
    fun testTable2OracleBets() {
        val p1s = listOf(.01, .001)
        val p2s = listOf(.01, .001, .0001)
        val p1smeans = listOf(.01, .001)
        val p2smeans = listOf(.001, .0001)

        val N = 20000
        val margin = .05
        val ntrials = 400
        // println("testOracle N=$N margin=$margin theta=$theta ntrials=$ntrials")

        val ratios = mutableListOf<Double>()
        for (p1 in p1s) {
            for (p2 in p2s) {
                for (p1m in p1smeans) {
                    for (p2m in p2smeans) {
                        val theta = margin2theta(margin)
                        val cvrs = makeCvrsByExactMean(N, theta)
                        val compareAssorter = makeStandardComparisonAssorter(theta)
                        val sampleWithErrors = ComparisonWithErrorRates(cvrs, compareAssorter, p2=p2, p1=p1, withoutReplacement = true)
                        val upperBound = compareAssorter.upperBound
                        println("testTable2OracleBets: p1=${p1}  p2=${p2}")

                        val oracle = OptimalComparison(
                            N = N,
                            upperBound = upperBound,
                            a = compareAssorter.noerror,
                            p1 = p1,
                            p2 = p2,
                        )
                        val betting = BettingMart(bettingFn = oracle, N = N, upperBound = upperBound, withoutReplacement = true)

                        val result = runBettingMartRepeated(
                            drawSample = sampleWithErrors,
                            maxSamples = N,
                            ntrials = ntrials,
                            bettingMart = betting,
                            testParameters = mapOf("p1" to p1, "p2" to p2, "margin" to margin)
                        )
                        println("  result = ${result.avgSamplesNeeded()} ${result.percentHist}")
                        val expected = findTable2Entry(p2=p2, p1=p1, sampleP2=p2m, sampleP1=p1m)
                        if (expected != null) {
                            val ratio = result.avgSamplesNeeded().toDouble() / expected.meanSamples
                            ratios.add(ratio)
                            println("  expected = ${expected.meanSamples}, ${expected.samples90} $ratio")
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
}

val table2 = listOf(
    CobraTable2Result(.0001, .001, .0001, .001, 124, 127),
    CobraTable2Result(.0001, .001, .0001, .01, 124, 127),
    CobraTable2Result(.0001, .001, .001, .001, 127, 127),
    CobraTable2Result(.0001, .001, .001, .01, 124, 127),

    CobraTable2Result(.0001, .01, .0001, .001, 174, 229),
    CobraTable2Result(.0001, .01, .0001, .01, 168, 229),
    CobraTable2Result(.0001, .01, .001, .001, 176, 229),
    CobraTable2Result(.0001, .01, .001, .01, 159, 205),

    CobraTable2Result(.001, .001, .0001, .001, 146, 256),
    CobraTable2Result(.001, .001, .0001, .01, 151, 256),
    CobraTable2Result(.001, .001, .001, .001, 147, 256),
    CobraTable2Result(.001, .001, .001, .01, 149, 256),

    CobraTable2Result(.001, .01, .0001, .001, 209, 351),
    CobraTable2Result(.001, .01, .0001, .01, 200, 324),
    CobraTable2Result(.001, .01, .001, .001, 204, 351),
    CobraTable2Result(.001, .01, .001, .01, 208, 324),

    CobraTable2Result(.01, .001, .0001, .001, 526, 996),
    CobraTable2Result(.01, .001, .0001, .01, 525, 984),
    CobraTable2Result(.01, .001, .001, .001, 528, 1032),
    CobraTable2Result(.01, .001, .001, .01, 534, 985),

    CobraTable2Result(.01, .01, .0001, .001, 999, 1908),
    CobraTable2Result(.01, .01, .0001, .01, 1110, 2002),
    CobraTable2Result(.01, .01, .001, .001, 1030, 1868),
    CobraTable2Result(.01, .01, .001, .01, 1127, 2256),
)

data class CobraTable2Result(
    val p2: Double, val p1: Double, val sampleP2: Double, val sampleP1: Double,
    val meanSamples: Int, val samples90: Int,
)

fun findTable2Entry(p2: Double, p1: Double, sampleP2: Double, sampleP1: Double): CobraTable2Result? {
    return table2.find { it.p2 == p2 && it.p1 == p1 && it.sampleP2 == sampleP2 && it.sampleP1 == sampleP1 }
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
