package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.math.ln

data class BettingPayoff(
    val cat: String,
    val lamda: Double,
    val t: Double,
)

// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)


class PlotBettingPayoff {

    @Test
    fun plotBettingPayoff2() {
        val nsteps = 50
        // data class PluralityErrorRates(val p2o: Double, val p1o: Double, val p1u: Double, val p2u: Double) {
        val taus = mapOf("p2o" to 0.0, "p1o" to 0.5, "p1u" to 1.5, "p2u" to 2.0)
        val margin = .01
        val noerror = 1 / (2 - margin)

        val p = .001
        val p0 = 1.0 - taus.size * p

        val results = mutableListOf<BettingPayoff>()
            repeat(nsteps) { step ->
                val lamda = (step + 1) * 1.9 / nsteps

                val t0 = ln(1.0 + lamda * (noerror - 0.5)) * p0
                results.add(BettingPayoff("noerror", lamda, t0))

                var sum = t0
                taus.forEach { (cat, tau) ->
                    val t = ln(1.0 + lamda * (noerror * tau - 0.5)) * p
                    sum += t
                    results.add(BettingPayoff(cat, lamda, t))
                }

                results.add(BettingPayoff("sum", lamda, sum))
            }

        val plotter = PlotBettingPayoffData("$testdataDir/plots/betting/optimallamda", "payoff")
        plotter.plotData(results, margin, noerror, p)
    }

    class PlotBettingPayoffData(val dirName: String, val filename: String) {

        fun plotData(data: List<BettingPayoff>, margin: Double, noerror: Double, p2:Double) {
            validateOutputDir(Path(dirName))

            genericPlotter(
                "BettingPayoff",
                "margin=$margin noerror=${df(noerror)} errRate=${df(p2)}",
                "$dirName/$filename",
                data,
                "lamda", "ln(payoff)*rate", "cat",
                xfld = { it.lamda },
                yfld = { it.t },
                // catfld = { df(it.tau) },
                catfld = { it.cat },
            )
        }
    }
}


