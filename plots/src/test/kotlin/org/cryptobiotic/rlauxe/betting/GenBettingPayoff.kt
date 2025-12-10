package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.core.AdaptiveBetting
import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
import org.cryptobiotic.rlauxe.core.ClcaErrorTracker
import org.cryptobiotic.rlauxe.core.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

class GenBettingPayoff {

    @Test
    fun showAdaptiveComparisonBet() {
        val N = 10000
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        for (error in listOf(0.0, 0.0001, .001, .01)) {
            println("errors = $error")
            for (margin in margins) {
                val noerror = 1 / (2 - margin)

                //    val Nc: Int, // max number of cards for this contest
                //    val withoutReplacement: Boolean = true,
                //    val a: Double, // compareAssorter.noerror
                //    val d1: Int,  // weight p1, p3 // TODO derive from p1-p4 ??
                //    val d2: Int, // weight p2, p4
                //    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; set to 0 to remove consideration
                //    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
                //    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; set to 0 to remove consideration
                //    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; set to 0 to remove consideration
                //    val eps: Double = .00001
                val optimal = AdaptiveBetting(
                    N = N,
                    a = noerror,
                    d = 10000,
                    errorRates = PluralityErrorRates(error, error, error, error),
                )
                val samples = PluralityErrorTracker(noerror)
                repeat(100) { samples.addSample(noerror) }
                println(" margin=$margin, noerror=$noerror bet = ${optimal.bet(samples)}")
            }
        }
    }

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
                val betFn = GeneralAdaptiveBetting(N, oaErrorRates = null, d = 100, maxRisk=.99)
                val samples = ClcaErrorTracker(noerror, 1.0)
                repeat(100) { samples.addSample(noerror) }
                println(" margin=$margin, noerror=$noerror bet = ${betFn.bet(samples)}")
            }
        }
    }

    @Test
    fun showBettingPayoff() {
        val N = 10000
        val margins = listOf(.01)

        for (error in listOf(0.0, 0.0001, .001, .01)) {
            println("errors = $error")
            for (margin in margins) {
                val noerror = 1 / (2 - margin)

                val bettingFn = AdaptiveBetting(
                    N = N,
                    a = noerror,
                    d = 10000,
                    errorRates = PluralityErrorRates(error, error, error, error),
                )
                val samples = PluralityErrorTracker(noerror)
                repeat(100) { samples.addSample(noerror) }
                val bet = bettingFn.bet(samples)
                println("margin=$margin, noerror=$noerror bet = $bet}")

                println("2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder")
                //     X_i = {0, .5, 1, 1.5, 2} * noerror for {2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder} respectively.
                val payoff = listOf(0.0, 0.5, 1.0, 1.5, 2.0).map { x ->
                    // 1 + λ_i (X_i − µ_i)
                    1.0 + bet * (noerror * x - .5)
                }
                payoff.forEach { print("${dfn(it, 6)}, ") }
                println()
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

                val bettingFn = AdaptiveBetting(
                    N = N,
                    a = noerror,
                    d = 10000,
                    errorRates = PluralityErrorRates(error, error, error, error),
                )
                val tracker = PluralityErrorTracker(noerror)

                repeat(10) { tracker.addSample(noerror) }
                val bet = bettingFn.bet(tracker)
                println("margin=$margin, noerror=$noerror bet = $bet}")

                println("2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder")
                //     X_i = {0, .5, 1, 1.5, 2} * noerror for {2voteOver, 1voteOver, equal, 1voteUnder, 2voteUnder} respectively.
                val payoffs = assortValue.map { x ->
                    // 1 + λ_i (X_i − µ_i)
                    val payoff = 1.0 + bet * (noerror * x - .5)
                    results.add(BettingPayoffData(N, margin, error, bet, payoff, x))
                    payoff
                }
                payoffs.forEach { print("${dfn(it, 6)}, ") }
                println()
            }
        }

        val plotter = PlotBettingPayoffData("/home/stormy/rla/betting/", "bettingPayoff.csv")
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
            val sampleSize = org.cryptobiotic.rlauxe.core.sampleSize(risk, payoff)
            println("margin=$margin, noerror=$noerror bet = $bet payoff=$payoff sampleSize=$sampleSize")
        }
    }

}