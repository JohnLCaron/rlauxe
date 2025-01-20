package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color

// generic multiple line plotter
fun wrsPlot(
    titleS: String,
    subtitleS: String,
    wrs: List<WorkflowResult>,
    writeFile: String, // no suffix
    xname: String, yname: String, catName: String,
    xfld: (WorkflowResult) -> Double,
    yfld: (WorkflowResult) -> Double,
    catfld: (WorkflowResult) -> String,
) {
    val useWrs = wrs.filter { it.status != TestH0Status.AllFailPct }
    val groups = makeGroups(useWrs, catfld)

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
            category.add(cat)
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

    plot.save("${writeFile}.png")
    plot.save("${writeFile}.html")
    println("saved to $writeFile")
}

////////////////////////////////////////////////////////////////////////////
/*
fun readAndFilter(
    filename: String,
    thetaRange: ClosedRange<Double>? = null,
    Nc: Int? = null,
    reportedMeanDiff: Double? = null,
    eta0Factor: Double? = null,
    d: Int? = null,
): List<WorkflowResult> {
    val reader = WorkflowResultsIO(filename)
    val wrs = reader.readResults()
    return wrs.filter {
        (thetaRange == null || thetaRange.contains(it.theta))
                && (Nc == null || it.Nc == Nc)
                && (reportedMeanDiff == null || it.reportedMeanDiff == reportedMeanDiff)
                && (d == null || it.d == d)
                && (eta0Factor == null || it.eta0Factor == eta0Factor)
    }
}

 */

/////////////////////////////////////////////////////////////////////////////////

// make a map of all WorkflowResult for each catFld
fun makeGroups(srs: List<WorkflowResult>, catfld: (WorkflowResult) -> String): Map<String, List<WorkflowResult>> {
    val result = mutableMapOf<String, MutableList<WorkflowResult>>()
    srs.forEach {
        val imap: MutableList<WorkflowResult> = result.getOrPut(catfld(it)) { mutableListOf() }
        imap.add(it)
    }
    return result.toSortedMap()
}