package org.cryptobiotic.rlauxe.rlaplots

import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.continuousPos
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.scale.Scale
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.hLine
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.scales.Transformation
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color

// generic multiple line plotter; dont need WorkflowResult
fun <T> genericPlotter(
        titleS: String,
        subtitleS: String,
        writeFile: String,
        data: List<T>,
        xname: String,
        yname: String,
        catName: String,
        xfld: (T) -> Double,
        yfld: (T) -> Double,
        catfld: (T) -> String,
        addPoints: Boolean = true,
        scaleType: ScaleType = ScaleType.Linear,
    ) {

    val groups = makeGGroups(data, catfld)

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

    val xScale = if (scaleType == ScaleType.LogLog) Scale.continuousPos<Int>(transform = Transformation.LOG10) else Scale.continuousPos<Int>()
    val yScale = if (scaleType == ScaleType.Linear) Scale.continuousPos<Int>() else Scale.continuousPos<Int>(transform = Transformation.LOG10)

    val plot = multipleDataset.plot {
        groupBy(catName) {
            line {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                color(catName)
            }

            if (addPoints) {
                points {
                    x(xname) { scale = xScale }
                    y(yname) { scale = yScale }
                    size = 1.0
                    symbol = Symbol.CIRCLE
                    color = Color.BLACK

                    // tooltips(variables, formats, title, anchor, minWidth, hide)
                    tooltips(xname, yname, catName)
                    //    formats = mapOf("margin" to "f8.3"))
                }
            }

            hLine {
                yIntercept.constant(0) // Sets the line position
                color = Color.BLACK       // Customizes the line color
                width = .3             // Customizes the line thickness
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

// make a map of all Ts for each catFld
fun <T> makeGGroups(data: List<T>, catfld: (T) -> String): Map<String, List<T>> {
    val result: MutableMap<String, MutableList<T>> = mutableMapOf()

    data.forEach { it: T ->
        val imap: MutableList<T> = result.getOrPut(catfld(it)) { mutableListOf() }
        imap.add(it)
    }
    return result // .toSortedMap()
}

fun <T> genericScatter(
    titleS: String,
    subtitleS: String,
    data: List<T>,
    writeFile: String, // no suffix
    xname: String, yname: String, catName: String,
    xfld: (T) -> Double,
    yfld: (T) -> Double,
    catfld: (T) -> String,
    scaleType: ScaleType = ScaleType.Linear,
) {
    val groups = makeGGroups(data, catfld)

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

    val xScale = if (scaleType == ScaleType.LogLog) Scale.continuousPos<Int>(transform = Transformation.LOG10) else Scale.continuousPos<Int>()
    val yScale = if (scaleType == ScaleType.Linear) Scale.continuousPos<Int>() else Scale.continuousPos<Int>(transform = Transformation.LOG10)

    val plot = multipleDataset.plot {
        groupBy(catName) {
            /* if (catName == "CLCA") {
                line {
                    x(xname) { scale = xScale }
                    y(yname) { scale = yScale }
                    color(catName)
                }
            } */

            points {
                x(xname) { scale = xScale }
                y(yname) { scale = yScale }
                size = 0.667
                // symbol = Symbol.CIRCLE_SMALL
                symbol(catName) {
                    scale = categorical(
                        "CLCA" to Symbol.CIRCLE_FILLED,
                        "OneAudit" to Symbol.CIRCLE_SMALL,
                        "OneAuditNS" to Symbol.CIRCLE_SMALL
                    )
                }
                color(catName) {
                    scale = categorical(
                        "CLCA" to Color.RED,
                        "OneAudit" to Color.LIGHT_BLUE,
                        "OneAuditNS" to Color.PURPLE
                    )
                }

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