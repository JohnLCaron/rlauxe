package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.math.ln

class PlotErrorCompensation {
    val dirName = "$testdataDir/plots/betting/errorComp"
    val margins = listOf(.005, .006, .008, .01, .0125, .015, .02, .03, .04, .05, .06, .07, .08, .10)

    @Test
    fun plotCompByLamda() {
        val nsteps = 50
        val upper = 1.0
        val taus = Taus(upper)

        val results = mutableListOf<BettingPayoffRatio>()
        // margins.forEach { margin ->
            val margin = .01
            val noerror = 1 / (2 - margin / upper)
            repeat(nsteps) { step ->
                val lamda = (step + 1) * 2.0 / nsteps   // 1.9 is the arbitrary upper limit for plotting lamda

                val payoffNoerror = 1.0 + lamda * (noerror - 0.5)

                // how many noerror do we need to offset one error?
                // payoffNoerror^n = payoffError
                // n = ln(payoffError) / ln(payoffNoerror)

                taus.namesNoErrors().forEach { tauName ->
                    val tauValue = taus.valueOf(tauName)
                    val payoffErr = 1.0 + lamda * (noerror * tauValue - 0.5)
                    val samplesToCompensate = -ln(payoffErr) / ln(payoffNoerror)
                    results.add(BettingPayoffRatio(cat=tauName, payoffRatio=samplesToCompensate, lamda=lamda))  }
                }
        // }

        plotByLamda(results, "byLamda", "upper=$upper margin=$margin")
    }

    fun plotByLamda(data: List<BettingPayoffRatio>, name: String, subtitle: String) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "samples needed to compensate for one error",
            subtitle,
            "$dirName/$name",
            data,
            xname="lamda", xfld = { it.lamda },
            yname="samplesToCompensate", yfld = { it.payoffRatio },
            catName="tau", catfld = { it.cat },
        )
    }

    @Test
    fun plotCompByMargin() {
        val lamda = 1.8
        val upper = 1.0
        val taus = Taus(upper)

        val results = mutableListOf<BettingPayoffRatio>()
        margins.forEach { margin ->
            val noerror = 1 / (2 - margin / upper)
            val payoffNoerror = 1.0 + lamda * (noerror - 0.5)

            taus.namesNoErrors().forEach { tauName ->
                val tauValue = taus.valueOf(tauName)
                val payoffErr = 1.0 + lamda * (noerror * tauValue - 0.5)
                val samplesToCompensate = -ln(payoffErr) / ln(payoffNoerror)
                results.add(BettingPayoffRatio(cat=tauName, payoffRatio=samplesToCompensate, lamda=lamda, margin=margin))  }
        }

        plotByMargin(results, "byMargin", "upper=$upper lamda=$lamda", scale = ScaleType.Linear)
    }

    @Test
    fun plotCompByUpper10() {
        val lamda = 1.8
        val upper = 10.0
        val taus = Taus(upper)

        val results = mutableListOf<BettingPayoffRatio>()
        margins.forEach { margin ->
            val noerror = 1 / (2 - margin / upper)
            val payoffNoerror = 1.0 + lamda * (noerror - 0.5)

            taus.namesNoErrors().forEach { tauName ->
                val tauValue = taus.valueOf(tauName)
                val payoffErr = 1.0 + lamda * (noerror * tauValue - 0.5)
                val samplesToCompensate = -ln(payoffErr) / ln(payoffNoerror)
                results.add(BettingPayoffRatio(cat=tauName, payoffRatio=samplesToCompensate, lamda=lamda, margin=margin))  }
        }

        plotByMargin(results, "byUpper10", "upper=$upper (Above 5% Threshold) lamda=$lamda", scale = ScaleType.Linear)
    }

    @Test
    fun plotCompByUpper2() {
        val lamda = 1.8
        val upper = 2.0
        val taus = Taus(upper)

        val results = mutableListOf<BettingPayoffRatio>()
        margins.forEach { margin ->
            val noerror = 1 / (2 - margin / upper)
            val payoffNoerror = 1.0 + lamda * (noerror - 0.5)

            taus.namesNoErrors().forEach { tauName ->
                val tauValue = taus.valueOf(tauName)
                val payoffErr = 1.0 + lamda * (noerror * tauValue - 0.5)
                val samplesToCompensate = -ln(payoffErr) / ln(payoffNoerror)
                results.add(BettingPayoffRatio(cat=tauName, payoffRatio=samplesToCompensate, lamda=lamda, margin=margin))  }
        }

        plotByMargin(results, "byUpper2", "upper=$upper lamda=$lamda", scale = ScaleType.Linear)
    }

    @Test
    fun plotCompByUpperUnder1() {
        val lamda = 1.8
        val upper = .526
        val taus = Taus(upper)

        val results = mutableListOf<BettingPayoffRatio>()
        margins.forEach { margin ->
            val noerror = 1 / (2 - margin / upper)
            val payoffNoerror = 1.0 + lamda * (noerror - 0.5)

            taus.namesNoErrors().forEach { tauName ->
                val tauValue = taus.valueOf(tauName)
                val payoffErr = 1.0 + lamda * (noerror * tauValue - 0.5)
                val samplesToCompensate = -ln(payoffErr) / ln(payoffNoerror)
                results.add(BettingPayoffRatio(cat=tauName, payoffRatio=samplesToCompensate, lamda=lamda, margin=margin))  }
        }

        plotByMargin(results, "byUpperUnder1", "upper=$upper (Below 5% Threshold) lamda=$lamda", scale = ScaleType.Linear)
    }

    fun plotByMargin(data: List<BettingPayoffRatio>, name: String, subtitle: String, scale: ScaleType = ScaleType.Linear) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "samples needed to compensate for one error",
            subtitle,
            "$dirName/$name",
            data,
            xname="margin", xfld = { it.margin },
            yname="samplesToCompensate", yfld = { it.payoffRatio },
            catName="tau", catfld = { it.cat },
            scaleType=scale,
        )
    }

}

data class BettingPayoffRatio(
    val cat: String,
    val payoffRatio: Double,
    val lamda: Double = 0.0,
    val margin: Double = 0.0,
    val noerror: Double = 0.0,
)

/*
// payoff vs margin, categories upper
class PayoffVsMargin {
    val dirName = "$testdataDir/plots/betting/payoffVsMargin"

    @Test
    fun payoffVsMargin() {
        val uppers = listOf(10.0, 2.5, 1.0, .7, .526)
        val margins = listOf(.002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val results = mutableListOf<BettingPayoff>()
        val lamda = 1.8

        margins.forEach { margin ->
            uppers.forEach { upper ->

                val noerror = 1 / (2 - margin / upper)
                val payoff = 1.0 + lamda * (noerror - 0.5)

                results.add(BettingPayoff(dfn(upper,3), t = payoff, margin = margin))
            }
        }

        plotPayoffVsMargin(results, "payoffVsMargin", lamda, "")
    }

    fun plotPayoffVsMargin(data: List<BettingPayoff>, name: String, lamda: Double, subtitle: String, scale: ScaleType = ScaleType.Linear) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            name,
            "lamda=$lamda $subtitle",
            "$dirName/$name",
            data,
            xname = "margin", xfld = { it.margin },
            yname = "payoff", yfld = { it.t },
            catName="tau", catfld = { it.cat },
            scaleType = scale
        )
    }


    @Test
    fun payoffVsMarginTaus() {
        val uppers = listOf(10.0, 2.5, 1.0, .7, .526)
        val margins = listOf(.002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val results = mutableListOf<BettingPayoff>()
        val lamda = 1.8

        val upper = 1.0
        val taus = Taus(upper)

        margins.forEach { margin ->
            uppers.forEach { upper ->
                taus.names().forEach { tauName ->

                    val tauValue = taus.valueOf(tauName)
                    val noerror = 1 / (2 - margin/upper)
                    val payoff = 1.0 + lamda * (tauValue * noerror - 0.5)

                    results.add(BettingPayoff(tauName, t = payoff, margin = margin, noerror = noerror))
                }
            }
        }

        val subtitle = "uppers=$uppers"

        payoffVsMarginTaus(results, "payoffVsMarginTaus", lamda, subtitle, ScaleType.Linear)
        plotPayoffVsNoerror(results, "payoffVsNoerrorTaus", lamda, subtitle, ScaleType.Linear)
    }

    fun payoffVsMarginTaus(data: List<BettingPayoff>, name: String, lamda: Double, subtitle: String, scale: ScaleType = ScaleType.Linear) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            name,
            "lamda=$lamda $subtitle",
            "$dirName/$name",
            data,
            xname = "margin", xfld = { it.margin },
            yname = "payoff", yfld = { it.t },
            catName="tau", catfld = { it.cat },
            scaleType = scale
        )
    }

    fun plotPayoffVsNoerror(data: List<BettingPayoff>, name: String, lamda: Double, subtitle: String, scale: ScaleType = ScaleType.Linear) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            name,
            "lamda=$lamda $subtitle",
            "$dirName/$name",
            data,
            xname = "noerror", xfld = { it.noerror },
            yname = "payoff", yfld = { it.t },
            catName="tau", catfld = { it.cat },
            scaleType = scale
        )
    }

}

*/