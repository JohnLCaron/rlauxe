package org.cryptobiotic.rlauxe.rlaplots

import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color

// generic multiple line plotter
fun srtPlot(
    titleS: String, subtitleS: String, srts: List<SRT>, saveFile: String,
    xname: String, yname: String, catName: String,
    xfld: (SRT) -> Double, yfld: (SRT) -> Double, catfld: (SRT) -> Double,
    bravoGroup: List<SRT>? = null, // optional
) {

    val groups = makeGroups(srts, catfld)

    val xvalues = mutableListOf<Double>()
    val yvalues = mutableListOf<Double>()
    val category = mutableListOf<String>()
    groups.forEach { (cat, srts) ->
        val ssrtList = srts.sortedBy { xfld(it) }
        val xvalue = ssrtList.map { xfld(it) }
        xvalues.addAll(xvalue)

        val yvalue = ssrtList.map { yfld(it) }
        yvalues.addAll(yvalue)

        repeat(ssrtList.size) {
            category.add(dd(cat))
        }
    }

    if (bravoGroup != null) {
        val reportedMeanDiff = bravoGroup.map { it.reportedMeanDiff }
        xvalues.addAll(reportedMeanDiff)

        val yvalue = bravoGroup.map { it.pctSamples }
        yvalues.addAll(yvalue)

        repeat(bravoGroup.size) {
            category.add("bravo")
        }
    }

    // names are used as labels
    val multipleDataset = mapOf(
        xname to xvalues,
        yname to yvalues,
        catName to category,
    )

    val plot = multipleDataset.plot {
        groupBy(catName) {
            line {
                x(xname)
                y(yname)
                color(catName)
            }

            points {
                x(xname)
                y(yname)
                size = 1.0
                symbol = Symbol.CIRCLE_OPEN
                color = Color.BLUE

                // tooltips(variables, formats, title, anchor, minWidth, hide)
                tooltips(xname, yname, catName)
            }

            layout {
                title = titleS
                subtitle = subtitleS
            }
        }
    }

    plot.save(saveFile)
    println("saved to $saveFile")
}

////////////////////////////////////////////////////////////////////////////
fun readAndFilter(
    filename: String,
    thetaRange: ClosedRange<Double>? = null,
    N: Int? = null,
    reportedMeanDiff: Double? = null,
    eta0Factor: Double? = null,
    d: Int? = null,
): List<SRT> {
    val reader = SRTcsvReader(filename)
    val srts = reader.readCalculations()
    return srts.filter {
        (thetaRange == null || thetaRange.contains(it.theta))
                && (N == null || it.N == N)
                && (reportedMeanDiff == null || it.reportedMeanDiff == reportedMeanDiff)
                && (d == null || it.d == d)
                && (eta0Factor == null || it.eta0Factor == eta0Factor)
    }
}

fun readFilterTN(filename: String, theta: Double, N: Int): List<SRT> {
    val reader = SRTcsvReader(filename)
    val srts = reader.readCalculations()
    return srts.filter { it.theta == theta && it.N == N }
}

/////////////////////////////////////////////////////////////////////////////////

// make a map of all SRTS for each catFld
fun makeGroups(srs: List<SRT>, catfld: (SRT) -> Double): Map<Double, List<SRT>> {
    val result = mutableMapOf<Double, MutableList<SRT>>()
    srs.forEach {
        val imap: MutableList<SRT> = result.getOrPut(catfld(it)) { mutableListOf() }
        imap.add(it)
    }
    return result.toSortedMap()
}

fun dd(d: Double) = "%5.3f".format(d)

fun extractDecile(srt: SRT, sampleMaxPct: Int) =
    if (srt.percentHist == null || srt.percentHist!!.cumul(sampleMaxPct) == 0.0) 0.0 else {
        srt.percentHist!!.cumul(sampleMaxPct)
    }
