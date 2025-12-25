package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.testdataDir
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.errorBars
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.x
import kotlin.test.Test

class TestKandy {

    @Test
    fun testErrorBarExample() {
        val supp by columnOf("OJ", "OJ", "OJ", "VC", "VC", "VC")
        val dose by columnOf(0.5, 1.0, 2.0, 0.5, 1.0, 2.0)
        val length by columnOf(13.23, 22.70, 26.06, 7.98, 16.77, 26.14)
        val len_min by columnOf(11.83, 21.2, 24.50, 4.24, 15.26, 23.35)
        val len_max by columnOf(15.63, 24.9, 27.11, 10.72, 19.28, 28.93)
        val dataset = dataFrameOf(supp, dose, length, len_min, len_max)

        val plot = plot(dataset) {
            x(dose)
            errorBars {
                yMin(len_min)
                yMax(len_max)
                borderLine.color(supp)

                width = .1
            }
            line {
                y(length)
                color(supp)
            }
            points {
                y(length)
                color(supp)
            }
        }

        val writeFile = "$testdataDir/plots/testKandy/testErrorBarExample"

        plot.save("${writeFile}.html")
        println("saved to $writeFile")
    }
}