package org.cryptobiotic.rlauxe.betting
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.math.ln

data class MaxLoss(
    val margin: Double,
    val maxLoss: Double,
    val noerrors: Double,
    val needed: Double,
    val nloss: Int = 0,
)

class PlotMaxLoss {
    val dirName = "$testdataDir/plots/betting/maxLoss"
    val filename = "maxLoss"

    @Test
    fun plotMaxLoss() {

        val margins = listOf(.005, .0075, .01, .015, .025, .05, .10)
        val maxLosses = listOf(.50, .60, .70, .80, .90, .95, .99)

        val results = mutableListOf<MaxLoss>()
        margins.forEach { v ->
            maxLosses.forEach { maxLoss ->

                // tj = 1 + 2 * maxLoss * (noerror − 0.5)
                // tj = 1 + maxLoss * (v/(2-v))
                // n = -ln(1 - maxLoss) / ln(1 + maxLoss * (v/(2-v)))
                // v = diluted margin

                val loss = 1 / (1 - maxLoss)
                val tj = (1.0 + maxLoss * (v / (2 - v)))
                val noerrors = ln(loss) / ln(tj)
                val needed = ln(1/.05) / ln(tj)

                results.add(MaxLoss(v, maxLoss, noerrors, needed))
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
            yname = "nsamples", yfld = { it.noerrors },
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
            yname = "needed", yfld = { it.needed },
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
            yname = "nsamples", yfld = { it.needed + nloss*it.noerrors },
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
            yname = "nsamples", yfld = { (it.needed + nloss*it.noerrors) / it.needed },
            catName = "margin", catfld = { df(it.margin) },
            scaleType = scaleType
        )
    }

    @Test
    fun plotAgainstLosses() {

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
                val noerrors = ln(loss) / ln(tj)
                val needed = ln(1/.05) / ln(tj)

                repeat(6) {
                    val nloss = it
                    val nsamples = needed + nloss*noerrors
                    results.add(MaxLoss(v, maxLoss, noerrors, nsamples, nloss))
                }
            }
        }

        plotByNloss(results, ScaleType.Linear)
        plotByNloss(results, ScaleType.LogLinear)
        plotByNloss(results, ScaleType.LogLog)
    }

    fun plotByNloss(data: List<MaxLoss>, scaleType: ScaleType) {
        validateOutputDir(Path(dirName))

        genericPlotter(
            "noerror samples needed by number of p2o errors",
            "margins .01 and .05",
            "$dirName/$filename.nloss.$scaleType",
            data,
            xname = "maxLoss", xfld = { it.maxLoss },
            yname = "nsamples", yfld = { it.needed },
            catName = "nloss&margin", catfld = { "${nfn(it.nloss, 1)}&${it.margin}" },
            scaleType = scaleType
        )
    }
}


