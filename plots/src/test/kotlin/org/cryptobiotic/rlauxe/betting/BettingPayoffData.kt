package org.cryptobiotic.rlauxe.betting

import kotlin.math.log10
import kotlin.math.ln

import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df


// used in docs

data class BettingPayoffData(
    val Nc: Int,
    val margin: Double,
    val error: Double,
    val bet: Double,
    val payoff: Double,
    val assort: Double
)

class PlotBettingPayoffData(val dir: String, val filename: String) {

    fun plotOneErrorRate(data: List<BettingPayoffData>, wantError: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.error == wantError }

        genericPlotter(
            "BettingPayoff",
            "error = $wantError",
            "$dir/BettingPayoff${wantError}",
            useData,
            "margin", "payoff", "assort",
            xfld = { it.margin },
            yfld = { it.payoff },
            catfld = { df(it.assort) },
        )
    }

    fun plotOneAssortValue(data: List<BettingPayoffData>, wantAssort: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.assort == wantAssort }

        genericPlotter(
            "BettingPayoff",
            "assortValue = $wantAssort",
            "$dir/BettingPayoffAssort${wantAssort}",
            useData,
            "margin", "payoff", "error",
            xfld = { it.margin },
            yfld = { it.payoff },
            catfld = { df(it.error) },
        )
    }

    fun plotSampleSize(data: List<BettingPayoffData>, wantAssort: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.assort == wantAssort }

        genericPlotter(
            "BettingPayoff",
            "riskLimit=$risk, assortValue=$wantAssort",
            "$dir/BettingPayoffSampleSize",
            useData,
            "margin", "log10(sampleSize)", "error",
            xfld = { it.margin },
            yfld = { log10(sampleSize(it.payoff)) },
            catfld = { df(it.error) },
        )
    }
}

val risk = .05
val logRisk = -ln(.05)
fun sampleSize(payoff:Double) = logRisk / ln(payoff)