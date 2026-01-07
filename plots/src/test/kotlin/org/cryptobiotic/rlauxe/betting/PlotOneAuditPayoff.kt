package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.TausOA
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.junit.jupiter.api.Assertions.assertEquals

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.math.ln


// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class PlotOneAuditPayoff {
    val dirName = "$testdataDir/plots/betting/oapayoff"
    val filename = "oapayoff"

    @Test
    fun plotBettingPayoff() {
        val N = 10000
        val nsteps = 50
        val upper = 1.0
        val poolPct = .05
        val poolNcards = poolPct * N
        val poolMargin = .02
        val poolAvg = margin2mean(poolMargin)
        val taus = TausOA(upper, poolAvg)
        val poolWinners = poolAvg * poolNcards
        val poolLosers = poolNcards - poolWinners
        val poolOthers = 0 // TODO

        val margin = .02  // over entire ballots
        val noerror = 1 / (2 - margin)

        val p0 = 1.0 - poolPct
        val pw = poolWinners / N
        val pl = poolLosers / N
        val p00 = 1.0 - pw - pl
        assertEquals(p0, p00, doublePrecision)

        val results = mutableListOf<OneAuditPayoff>()
        repeat(nsteps) { step ->
            val lamda = (step + 1) * 1.9 / nsteps

            val t0 = 1.0 + lamda * (noerror - 0.5) * p0
            val t0ln = ln(1.0 + lamda * (noerror - 0.5)) * p0
            results.add(OneAuditPayoff("noerror", lamda, t0, t0ln))

            val loserTau = taus.tausOA[0].first
            val loserT = 1.0 + lamda * (noerror*loserTau - 0.5) * pl
            val loserTln = ln(1.0 + lamda * (noerror*loserTau - 0.5)) * pl
            results.add(OneAuditPayoff("loser", lamda, loserT, loserTln))

            val winnerTau = taus.tausOA[2].first
            val winnerT = 1.0 + lamda * (noerror*winnerTau - 0.5) * pw
            val winnerTln = ln(1.0 + lamda * (noerror*winnerTau - 0.5)) * pw
            results.add(OneAuditPayoff("winner", lamda, winnerT, winnerTln))

            val prod = t0 * loserT * winnerT
            val sum = t0ln + loserTln + winnerTln
            results.add(OneAuditPayoff("sum", lamda, prod, sum))
        }

        plotData(results, N, margin, noerror, poolPct, poolAvg)
    }


    fun plotData(data: List<OneAuditPayoff>, N:Int, margin: Double, noerror: Double, poolPct:Double, poolAvg: Double) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "OneAudit BettingPayoff",
            "N=$N margin=$margin poolPct=$poolPct poolAvg=${dfn(poolAvg, 3)}",
            "$dirName/$filename",
            data,
            "lamda", "ln(payoff)*rate", "cat",
            xfld = { it.lamda },
            yfld = { it.tln },
            // catfld = { df(it.tau) },
            catfld = { it.cat },
        )
    }

    data class OneAuditPayoff(
        val cat: String,
        val lamda: Double,
        val t: Double,
        val tln: Double,
    )
}

/* TODO
class PlotOneAuditSamples {
    val dirName = "$testdataDir/plots/betting/oasamples"
    val filename = "oasamples"

    @Test
    fun plotOneAuditSamples() {
        val N = 10000
        val upper = 1.0
        val poolPct = .05
        val poolNcards = poolPct * N
        val poolMargin = .02
        val poolAvg = margin2mean(poolMargin)
        val taus = TausOA(upper, poolAvg)
        val poolWinners = poolAvg * poolNcards
        val poolLosers = poolNcards - poolWinners
        val poolOthers = 0 // TODO

        // use for both pool and election
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val p0 = 1.0 - poolPct
        val pw = poolWinners / N
        val pl = poolLosers / N
        val p00 = 1.0 - pw - pl
        assertEquals(p0, p00, doublePrecision)

        val results = mutableListOf<OneAuditSamples>()
        margins.forEach { margin ->
            val noerror = 1 / (2 - margin)

            val t0 = ln(1.0 + lamda * (noerror - 0.5)) * p0
            results.add(OneAuditSamples("noerror", lamda, t0))

            val loserTau = taus.tausOA[0].first
            val loserT = ln(1.0 + lamda * (noerror*loserTau - 0.5)) * pl
            results.add(OneAuditSamples("loser", lamda, loserT))


            val winnerTau = taus.tausOA[2].first
            val winnerT = ln(1.0 + lamda * (noerror*winnerTau - 0.5)) * pw
            results.add(OneAuditSamples("winner", lamda, winnerT))

            val sum = t0 + loserT + winnerT
            results.add(OneAuditSamples("sum", lamda, sum))
        }

        plotData(results, N, margin, noerror, poolPct, poolAvg)
    }


    fun plotData(data: List<OneAuditSamples>, N:Int, margin: Double, noerror: Double, poolPct:Double, poolAvg: Double) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "OneAudit BettingPayoff",
            "N=$N margin=$margin poolPct=$poolPct poolAvg=${dfn(poolAvg, 3)}",
            "$dirName/$filename",
            data,
            "margin", "nsamples", "cat",
            xfld = { it.margin },
            yfld = { it.nsamples },
            // catfld = { df(it.tau) },
            catfld = { it.cat },
        )
    }

    data class OneAuditSamples(
        val cat: String,
        val margin: Double,
        val nsamples: Double,
    )
}

*/
