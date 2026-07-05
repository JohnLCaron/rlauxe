package org.cryptobiotic.rlauxe.rlaplots

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.util.context.invoke
import org.jetbrains.kotlinx.statistics.binning.BinsAlign
import org.jetbrains.kotlinx.statistics.binning.BinsOption
import org.jetbrains.kotlinx.statistics.kandy.layers.histogram
import org.jetbrains.kotlinx.statistics.plotting.bin.statBin
import org.jetbrains.kotlinx.statistics.stats.mean

// not generic
fun plotHistogram(
    titleS: String,
    subtitleS: String,
    writeFile: String,
    xname: String,
    yname: String,
    xvalues: List<Number>,
) {
    val weight = 100.0 / xvalues.size
    val weights = xvalues.map { weight }
    val mean = xvalues.mean()

    val df = dataFrameOf(xname to xvalues, yname to weights)
    df.statBin(xname, yname, binsOption = BinsOption.byWidth(2.0), binsAlign = BinsAlign.boundary(mean))

    val plot = df.plot {
        histogram(xname) {
            y(Stat.countWeighted)
            y.axis.name = yname
            fillColor = org.jetbrains.kotlinx.kandy.util.color.Color.RED
            width = 0.8
            borderLine {
                color = org.jetbrains.kotlinx.kandy.util.color.Color.BLACK
                width = 0.5
            }
        }
        layout {
            title = titleS
            subtitle = subtitleS
        }
    }

    plot.save("${writeFile}.png")
    plot.save("${writeFile}.html")
    println("saved to $writeFile")
}
