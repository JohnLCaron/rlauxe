package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.continuousPos
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.scale.Scale
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.scales.Transformation
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color

enum class ScaleType { Linear, LogLinear, LogLog }

// generic multiple line plotter for WorkflowResult
fun wrsPlot(
    titleS: String,
    subtitleS: String,
    wrs: List<WorkflowResult>,
    writeFile: String, // no suffix
    xname: String, yname: String, catName: String,
    xfld: (WorkflowResult) -> Double,
    yfld: (WorkflowResult) -> Double,
    catfld: (WorkflowResult) -> String,
    scaleType: ScaleType = ScaleType.Linear,
    colorChoices: ((Set<String>) -> Array<Pair<String, Color>>)? = null
) {
    // val useWrs = wrs.filter { it.status != TestH0Status.FailSimulationPct } // TODO
    val groups = makeWrGroups(wrs, catfld)

    val xvalues = mutableListOf<Double>()
    val yvalues = mutableListOf<Double>()
    val category = mutableListOf<String>()
    groups.forEach { (cat, wrs) ->
        val ssrtList = wrs.sortedBy { xfld(it) }
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

    val xScale = if (scaleType == ScaleType.LogLog) Scale.continuousPos<Int>(transform = Transformation.LOG10) else Scale.continuousPos<Int>()
    val yScale = if (scaleType == ScaleType.Linear) Scale.continuousPos<Int>() else Scale.continuousPos<Int>(transform = Transformation.LOG10)

    val plot = multipleDataset.plot {
        groupBy(catName) {
            line {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                if (colorChoices != null) {
                    color(catName) {
                        scale = categorical(*colorChoices(groups.keys))
                    }
                } else {
                    color(catName)
                }
            }

            points {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                size = 1.0
                symbol = Symbol.CIRCLE_OPEN
                color = Color.BLUE

                // tooltips(variables, formats, title, anchor, minWidth, hide)
                tooltips(xname, yname, catName,
                    formats = mapOf("margin" to "f8.3"))
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

// make a map of all WorkflowResult for each catFld
fun makeWrGroups(wrs: List<WorkflowResult>, catfld: (WorkflowResult) -> String): Map<String, List<WorkflowResult>> {
    val result = mutableMapOf<String, MutableList<WorkflowResult>>()
    wrs.forEach {
        val imap: MutableList<WorkflowResult> = result.getOrPut(catfld(it)) { mutableListOf() }
        imap.add(it)
    }
    return result.toSortedMap()
}

/////////////////////////////////////////////////////////////////////////////////
// this allows us to put multiple fields from the same WorkflowResult on the plot

fun wrsPlotMultipleFields(
    titleS: String,
    subtitleS: String,
    wrs: List<WorkflowResult>,
    writeFile: String, // no suffix
    xname: String, yname: String, catName: String,
    xfld: (WorkflowResult) -> Double,
    yfld: (String, WorkflowResult) -> Double,
    catflds: List<String>,
    scaleType: ScaleType = ScaleType.Linear
) {
    // val useWrs = wrs.filter { it.status != TestH0Status.FailSimulationPct } // TODO
    val groups = makeWrGroups(wrs, catflds)

    val xvalues = mutableListOf<Double>()
    val yvalues = mutableListOf<Double>()
    val category = mutableListOf<String>()
    groups.forEach { (cat, wrs) ->
        val ssrtList = wrs.sortedBy { xfld(it) }
        val xvalue = ssrtList.map { xfld(it) }
        xvalues.addAll(xvalue)

        val yvalue = ssrtList.map { yfld(cat, it) }
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

    val xScale = if (scaleType == ScaleType.LogLog) Scale.continuousPos<Int>(transform = Transformation.LOG10) else Scale.continuousPos<Int>()
    val yScale = if (scaleType == ScaleType.Linear) Scale.continuousPos<Int>() else Scale.continuousPos<Int>(transform = Transformation.LOG10)

    val plot = multipleDataset.plot {
        groupBy(catName) {
            line {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                color(catName)
            }

            points {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
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

// use multiple fields from same results
fun makeWrGroups(wrs: List<WorkflowResult>, catfld: List<String>): Map<String, List<WorkflowResult>> {
    val result = mutableMapOf<String, List<WorkflowResult>>()
    catfld.forEach {
        result[it] = wrs
    }
    return result.toSortedMap()
}

/////////////////////////////////////////////////////////////////////

// scatter plots  for WorkflowResult
fun wrsScatterPlot(
    titleS: String,
    subtitleS: String,
    wrs: List<WorkflowResult>,
    writeFile: String, // no suffix
    xname: String, yname: String, runName: String,
    xfld: (WorkflowResult) -> Double,
    yfld: (WorkflowResult) -> Double,
    runFld: (WorkflowResult) -> String,
    scaleType: ScaleType = ScaleType.Linear,
    colorChoices: ((Set<String>) -> Array<Pair<String, Color>>)? = null
) {
    val useWrs = wrs.filter { it.status == TestH0Status.StatRejectNull && it.margin > .005 }
    // val groups = makeWrGroups(wrs, catfld)
    val groups = mapOf("all" to useWrs)

    val xvalues = mutableListOf<Double>()
    val yvalues = mutableListOf<Double>()
    val runNames = mutableListOf<String>()
    groups.forEach { (cat, wrs) ->
        val ssrtList = wrs.sortedBy { xfld(it) }
        val xvalue = ssrtList.map { xfld(it) }
        xvalues.addAll(xvalue)

        val yvalue = ssrtList.map { yfld(it) }
        yvalues.addAll(yvalue)

        val rns = ssrtList.map { runFld(it) }
        runNames.addAll(rns)
    }

    // names are used as labels
    val multipleDataset = mapOf(
        xname to xvalues,
        yname to yvalues,
        runName to runNames,
    )

    val xScale = if (scaleType == ScaleType.LogLog) Scale.continuousPos<Int>(transform = Transformation.LOG10) else Scale.continuousPos<Int>()
    val yScale = if (scaleType == ScaleType.Linear) Scale.continuousPos<Int>() else Scale.continuousPos<Int>(transform = Transformation.LOG10)

    val plot = multipleDataset.plot {
        // groupBy(catName) {
        /*      line {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                if (colorChoices != null) {
                    color(catName) {
                        scale = categorical(*colorChoices(groups.keys))
                    }
                } else {
                    color(catName)
                }
            } */

            points {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                size = 0.50
                symbol = Symbol.CIRCLE_SMALL
                color = Color.LIGHT_PURPLE

                // tooltips(variables, formats, title, anchor, minWidth, hide)
                tooltips(xname, yname, runName)
            }

            layout {
                title = titleS
                subtitle = subtitleS
            }
        // }
    }

    plot.save("${writeFile}.png")
    plot.save("${writeFile}.html")
    println("saved to $writeFile")
}