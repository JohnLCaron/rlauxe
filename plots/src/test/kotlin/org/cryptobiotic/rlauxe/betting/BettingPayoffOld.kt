package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

// CANDIDATE for removal

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
                    nphantoms=0, oaAssortRates = null, d = 100, maxLoss = .99)
                val dvalues = DoubleArray(10) { noerror }
                val sampler = SampleFromArray(dvalues)
                println(" margin=$margin, noerror=$noerror bet = ${betFn.bet(sampler)}")
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
                    maxLoss = .90,
                )
                val dvalues = DoubleArray(10) { noerror }
                val tracker = SampleFromArray(dvalues)

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

}