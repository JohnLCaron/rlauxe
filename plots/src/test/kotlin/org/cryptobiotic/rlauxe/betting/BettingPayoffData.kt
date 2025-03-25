package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.rlaplots.ScaleType
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
            "SampleSize",
            "N=100000, riskLimit=$risk",
            "$dir/BettingPayoffSampleSize",
            useData,
            xname = "margin", xfld = { it.margin },
            yname="sampleSize", yfld = { org.cryptobiotic.rlauxe.core.sampleSize(risk, it.payoff) },
            catName="error", catfld = { df(it.error) },
            scaleType = ScaleType.LogLog,
        )
    }
}

val risk = .05