package org.cryptobiotic.rlauxe.rlaplots

import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.math.log10
import kotlin.math.ln

class BettingPayoff(val dir: String, val filename: String) {

    fun plotOneErrorRate(data: List<BettingPayoffData>, wantError: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.error == wantError }

        bpdPlot(
            "BettingPayoff",
            "error = $wantError",
            "$dir/BettingPayoff${wantError}.html",
            useData,
            "margin", "payoff", "assort",
            xfld = { it.margin },
            yfld = { it.payoff },
            catfld = { it.assort },
        )
    }

    fun plotOneAssortValue(data: List<BettingPayoffData>, wantAssort: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.assort == wantAssort }

        bpdPlot(
            "BettingPayoff",
            "assortValue = $wantAssort",
            "$dir/BettingPayoffAssort${wantAssort}.html",
            useData,
            "margin", "payoff", "error",
            xfld = { it.margin },
            yfld = { it.payoff },
            catfld = { it.error },
        )
    }

    fun plotSampleSize(data: List<BettingPayoffData>, wantAssort: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        val useData = data.filter { it.assort == wantAssort }

        bpdPlot(
            "BettingPayoff",
            "riskLimit=$risk, assortValue=$wantAssort",
            "$dir/BettingPayoffSampleSize.html",
            useData,
            "margin", "log10(sampleSize)", "error",
            xfld = { it.margin },
            yfld = { log10(sampleSize(it.payoff)) },
            catfld = { it.error },
        )
    }
}

val risk = .05
val logRisk = -ln(.05)
fun sampleSize(payoff:Double) = logRisk / ln(payoff)

data class BettingPayoffData(
    val Nc: Int,
    val margin: Double,
    val error: Double,
    val bet: Double,
    val payoff: Double,
    val assort: Double
)

// generic multiple line plotter
fun <T> bpdPlot(
    titleS: String, subtitleS: String, saveFile: String,
    srts: List<T>,
    xname: String, yname: String, catName: String,
    xfld: (T) -> Double, yfld: (T) -> Double, catfld: (T) -> Double,
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

// make a map of all Ts for each catFld
fun <T> makeGroups(srs: List<T>, catfld: (T) -> Double): Map<Double, List<T>> {
    val result = mutableMapOf<Double, MutableList<T>>()
    srs.forEach {
        val imap: MutableList<T> = result.getOrPut(catfld(it)) { mutableListOf() }
        imap.add(it)
    }
    return result.toSortedMap()
}
