package org.cryptobiotic.rlauxe.betting
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.math.ln

data class MaxLoss(
    val margin: Double,
    val maxLoss: Double,
    val samplesNeededNoErrors: Double, // samples needed no errors
    val samplesNeededToCompensate: Double, // sampels needed to compensate
    val totalNeeded: Double = 0.0, // sampels needed to compensate
    val nloss: Int = 0,
)

class PlotMaxLoss {
    val dirName = "$testdataDir/plots/betting/maxLoss"
    val filename = "maxLoss2"

    @Test
    fun plotMaxLoss() {

        val margins = listOf(.005, .0075, .01, .015, .025, .05, .10)
        val maxLosses = listOf(.50, .60, .70, .80, .90, .95, .99)

        val results = mutableListOf<MaxLoss>()
        margins.forEach { v ->
            maxLosses.forEach { maxLoss ->
                val noerror = 1/(2-v) // upper = 1

                // tj = 1 + 2 * maxLoss * (noerror − 0.5)
                // tj = 1 + maxLoss * (v/(2-v))
                // n = -ln(1 - maxLoss) / ln(1 + maxLoss * (v/(2-v)))
                // v = diluted margin

                val loss = 1 / (1 - maxLoss)
                val tj = (1.0 + maxLoss * (v / (2 - v)))
                val samplesNeededToCompensate = ln(loss) / ln(tj)
                val samplesNeededNoErrors = ln(1/.05) / ln(tj) // samples need no errors
                results.add(MaxLoss(v, maxLoss, samplesNeededNoErrors=samplesNeededNoErrors, samplesNeededToCompensate=samplesNeededToCompensate))

                // t is the payoff

                // t_noerror = 1 + λc * (noerror − 0.5)
                // t_noerror = 1 + 2 * maxLoss * (noerror − 0.5)

                val t_noerror = 1 + 2 * maxLoss * (noerror - 0.5)
                require(doubleIsClose(tj, t_noerror, doublePrecision))

                // t_p2o = 1 - maxLoss

                // To compensate for one p2o sample, we need n_p2o noerror samples, such that
                //    t_noerror^n_p2o = 1 / (1 - maxLoss)
                //    n_p2o = -ln(1 - maxLoss) / ln(t_noerror)
                val samplesNeededToCompensate2 = ln(loss) / ln(t_noerror)
                require(doubleIsClose(samplesNeededToCompensate, samplesNeededToCompensate2, doublePrecision))

                // if there are no errors we need
                //   t_noerror^n = 1 / alpha
                //   n = -ln(alpha) / ln(t_noerror)
                val samplesNeededNoErrors2 = -ln(.05) / ln(t_noerror)
                require(doubleIsClose(samplesNeededNoErrors, samplesNeededNoErrors2, doublePrecision))
                println()
            }
        }

        plotOffsetMaxLoss(results, ScaleType.LogLog)
        plotNeeded(results, ScaleType.LogLog)
        plotNeededForLoss(results, 1, ScaleType.LogLog)
        plotNeededForLoss(results, 2, ScaleType.LogLog)
        plotNeededForLoss(results, 3, ScaleType.LogLog)
        plotRatio(results, 1, ScaleType.LogLog)
        plotRatio(results, 2, ScaleType.LogLog)
        plotRatio(results, 3, ScaleType.LogLog)
    }

    fun plotOffsetMaxLoss(data: List<MaxLoss>, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed to offset one maxLoss",
            "subtitle",
            "$dirName/$filename.offsetMaxLoss.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "nsamples", yfld = { it.samplesNeededToCompensate },
            catName = "margin", catfld = { df(it.margin) },
            scaleType = scaleType
        )
    }

    fun plotNeeded(data: List<MaxLoss>, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed to prove",
            "subtitle",
            "$dirName/$filename.needed.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "needed", yfld = { it.samplesNeededNoErrors },
            catName = "margin", catfld = { df(it.margin) },
            scaleType = scaleType
        )
    }

    fun plotNeededForLoss(data: List<MaxLoss>, nloss: Int, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed for $nloss maxLoss",
            "subtitle",
            "$dirName/$filename.loss$nloss.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "nsamples", yfld = { it.samplesNeededNoErrors + nloss*it.samplesNeededToCompensate },
            catName = "margin", catfld = { df(it.margin) },
            scaleType = scaleType
        )
    }

    fun plotRatio(data: List<MaxLoss>, nloss: Int, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed for $nloss maxLoss",
            "subtitle",
            "$dirName/$filename.ratio$nloss.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "nsamples", yfld = { (it.samplesNeededNoErrors + nloss*it.samplesNeededToCompensate) / it.samplesNeededNoErrors },
            catName = "margin", catfld = { df(it.margin) },
            scaleType = scaleType
        )
    }

    @Test
    fun p2oAgainstLosses() {

        val margins = listOf(.01, .05)
        val results = mutableListOf<MaxLoss>()
        margins.forEach { v ->
            repeat(50) { idx ->
                val maxLoss =.5 + .01 * idx

                // tj = 1 + 2 * maxLoss * (noerror − 0.5)
                // tj = 1 + maxLoss * (v/(2-v))
                // n = -ln(1 - maxLoss) / ln(1 + maxLoss * (v/(2-v)))
                // v = diluted margin
                val loss = 1 / (1 - maxLoss)
                val tj = (1.0 + maxLoss * (v / (2 - v)))
                val samplesNeededToCompensate = ln(loss) / ln(tj)
                val samplesNeededNoErrors = ln(1/.05) / ln(tj)

                repeat(6) {
                    val nloss = it
                    val totalNeeded = samplesNeededNoErrors + nloss*samplesNeededToCompensate
                    results.add(MaxLoss(v, maxLoss, samplesNeededNoErrors, samplesNeededToCompensate, totalNeeded = totalNeeded, nloss))
                }
            }
        }

        plotByNloss("p2o errors", results, ScaleType.Linear)
        plotByNloss("p2o errors", results, ScaleType.LogLinear)
        plotByNloss("p2o errors", results, ScaleType.LogLog)
    }

    @Test
    fun p1oAgainstLosses() {

        val margins = listOf(.01, .05)
        val results = mutableListOf<MaxLoss>()
        margins.forEach { v ->
            repeat(50) { idx ->
                val maxLoss =.5 + .01 * idx
                val noerror = 1/(2-v) // upper = 1

                // t is the payoff

                // t_noerror = 1 + λc * (noerror − 0.5)
                // t_noerror = 1 + 2 * maxLoss * (noerror − 0.5)

                // t_p2o = 1 - maxLoss

                // To compensate for one p2o sample, we need n_p2o noerror samples, such that
                //    t_noerror^n_p2o = 1 / (1 - maxLoss)
                //    n_p2o = -ln(1 - maxLoss) / ln(t_noerror)

                // if there are no errors we need
                //   t_noerror^n = 1 / alpha
                //   n = -ln(alpha) / ln(t_noerror)

                // Ignoring other types of errors, the number of samples needed when there are k p2o errors are:
                //
                //    ntotal = n + k * ncomp
                //    ntotal = -ln(alpha) / ln(t_noerror) + k * -ln(1 - maxLoss) / ln(t_noerror)
                //    ntotal = -(ln(alpha) + k * ln(1 - maxLoss)) / ln(t_noerror)

                // t_p1o = 1 + λc * (noerror/2 − 1/2)
                // t_p1o = 1 + λc/2 * (noerror-1)
                // t_p1o = 1 + maxLoss * (noerror-1)

                // To compensate for one p1o sample, we need n_p2o noerror samples, such that
                //    t_noerror^n_p1o = 1 / (maxLoss * (noerror-1))
                //    n_p1o = -ln(maxLoss * (noerror-1)) / ln(t_noerror)
                //    ntotal = -(ln(alpha) + k * ln(maxLoss * (noerror-1)) / ln(t_noerror)

                val t_noerror = 1 + 2 * maxLoss * (noerror - 0.5)
                val t_p1o = 1 + maxLoss * (noerror-1)

                val samplesNeededToCompensate = -ln(t_p1o) / ln(t_noerror)
                val samplesNeededNoErrors = ln(1/.05) / ln(t_noerror)

                repeat(6) {
                    val nloss = it * 3
                    val totalNeeded = samplesNeededNoErrors + nloss*samplesNeededToCompensate
                    results.add(MaxLoss(v, maxLoss, samplesNeededNoErrors, samplesNeededToCompensate, totalNeeded = totalNeeded, nloss))
                }
            }
        }

        plotByNloss("phantoms", results, ScaleType.Linear)
        plotByNloss("phantoms",results, ScaleType.LogLinear)
        plotByNloss("phantoms",results, ScaleType.LogLog)
    }

    fun plotByNloss(what: String, data: List<MaxLoss>, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed by number of $what",
            "margins .01 and .05",
            "$dirName/$filename.$what.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "nsamples", yfld = { it.totalNeeded },
            catName = "number&margin", catfld = { "${nfn(it.nloss, 1)}&${it.margin}" },
            scaleType = scaleType
        )
    }
}


