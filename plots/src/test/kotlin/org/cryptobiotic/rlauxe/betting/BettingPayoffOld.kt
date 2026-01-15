package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.core.AdaptiveBetting
import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
import org.cryptobiotic.rlauxe.core.sampleSize
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

class BettingPayoffOld {

    @Test
    fun showGeneralAdaptiveComparisonBet() {
        val N = 10000
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        for (error in listOf(0.0, 0.0001, .001, .01)) {
            println("errors = $error")
            for (margin in margins) {
                val noerror = 1 / (2 - margin)
                // ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
                //val errorCounts = ClcaErrorCounts(emptyMap(), 0, noerror, 1.0)
                //val optimal = GeneralAdaptiveBettingOld(N = N, errorCounts, d = 100)
                val betFn = GeneralAdaptiveBetting(N,
                    startingErrors = ClcaErrorCounts.empty(noerror, 1.0),
                    nphantoms=0, oaAssortRates = null, d = 100, maxRisk = .99)
                val samples = ClcaErrorTracker(noerror, 1.0)
                repeat(100) { samples.addSample(noerror) }
                println(" margin=$margin, noerror=$noerror bet = ${betFn.bet(samples)}")
            }
        }
    }

    // used in docs
    @Test
    fun genBettingPayoffPlot() {
        val results = mutableListOf<BettingPayoffData>()
        val assortValue = listOf(0.0, 0.5, 1.0, 1.5, 2.0)
        val errorRates = listOf(0.0, 0.0005, .001, .005, .01)

        val N = 100000
        val margins = listOf(.002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        errorRates.forEach { error ->
            println("errors = $error")
            for (margin in margins) {
                val noerror = 1 / (2 - margin)

                // class GeneralAdaptiveBetting(
                //    val Npop: Int, // population size for this contest
                //    val oaErrorRates: OneAuditAssortValueRates?, // only for OneAudit
                //    val d: Int = 100,  // trunc weight
                //    val maxRisk: Double, // this bounds how close lam gets to 2.0; TODO study effects of this
                //    val withoutReplacement: Boolean = true,
                //    val debug: Boolean = false,
                //) : BettingFn {
                val bettingFn = GeneralAdaptiveBetting(
                    Npop = N,
                    startingErrors = ClcaErrorCounts.empty(noerror, 1.0),
                    nphantoms=0,
                    oaAssortRates = null,
                    d = 0,
                    maxRisk = .90,
                )
                val tracker = ClcaErrorTracker(noerror, upper = 1.0)

                repeat(10) { tracker.addSample(noerror) }
                val bet = bettingFn.bet(tracker)
                println("margin=$margin, noerror=$noerror bet = $bet}")

                val payoffs = assortValue.map { x ->
                    // 1 + λ_i (X_i − µ_i)
                    val payoff = 1.0 + bet * (noerror * x - .5)
                    results.add(BettingPayoffData(N, margin, error, bet, payoff, noerror * x))
                    payoff
                }
                payoffs.forEach { print("${dfn(it, 6)}, ") }
                println()
            }
        }

        val plotter = PlotBettingPayoffData("$testdataDir/plots/betting/payoff/", "bettingPayoff.csv")
        errorRates.forEach { error ->
            plotter.plotOneErrorRate(results, error)
        }
        plotter.plotOneAssortValue(results, 1.0)
        plotter.plotSampleSize(results, 1.0)
    }

    @Test
    fun showPayoffs() {
        val N = 100000
        val error = 0.0
        val risk = .03
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        for (margin in margins) {
            val noerror = 1 / (2 - margin)
            val bettingFn = AdaptiveBetting(
                N = N,
                a = noerror,
                d = 10000,
                errorRates = PluralityErrorRates(error, error, error, error),
            )
            val samples = PluralityErrorTracker(noerror)
            repeat(10) { samples.addSample(noerror) }
            val bet = bettingFn.bet(samples)
            val payoff = 1.0 + bet * (noerror - .5)
            val sampleSize = sampleSize(risk, payoff)
            println("margin=$margin, noerror=$noerror bet = $bet payoff=$payoff sampleSize=$sampleSize")
        }
    }

}