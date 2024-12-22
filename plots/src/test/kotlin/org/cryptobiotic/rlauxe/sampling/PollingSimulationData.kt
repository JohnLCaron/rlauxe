package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.rlaplots.dd
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.junit.jupiter.api.Test
import kotlin.math.ln

data class PollingSimulationData(
    val reportedMargin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val Nc: Int,
    val assortWithPhantoms: Double,
)

class TestPollingSimulationData {

    @Test
    fun getPollingSimulationData() {
        val results = mutableListOf<PollingSimulationData>()
        val underVotePct = .10
        val phantomPcts = listOf(0.0, 0.01, .02, .03, .04, .05)

        val Nc = 10000

        val margins =
            listOf(.04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            phantomPcts.forEach { phantomPct ->
                val sim = PollingSimulation2.make(margin, underVotePct, phantomPct, Nc)
                val contest = sim.contest
                val assorter = PluralityAssorter.makeWithVotes(contest, winner=0, loser=1)
                val cvrs = sim.makeCvrs()
                val assortWithPhantoms = cvrs.map { cvr -> assorter.assort(cvr, usePhantoms = true)}.average()
                results.add(PollingSimulationData(margin, underVotePct, phantomPct, Nc, assortWithPhantoms))
            }
        }

        val plotter = PlotPollingSimulationData("/home/stormy/temp/polling/", "pollingSimulation")
        plotter.plotData(results, Nc, underVotePct)
    }
}

class PlotPollingSimulationData(val dir: String, val filename: String) {

    fun plotData(data: List<PollingSimulationData>, Nc: Int, underVotePct: Double) {
        // val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0
        // val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        // val ntrials = srts[0].ntrials
        //val N = data[0].Nc
        //val p2prior = srts[0].p2prior
        // val useData = data.filter { it.error == wantError }

        psdPlot(
            "PollingSimulation",
            "Nc=$Nc underVotePct=$underVotePct",
            "$dir/$filename",
            data,
            "reportedMargin", "assortWithPhantoms", "phantomPct",
            xfld = { it.reportedMargin },
            yfld = { it.assortWithPhantoms },
            catfld = { it.phantomPct },
        )
    }
}

val risk = .05
val logRisk = -ln(.05)
fun sampleSize(payoff:Double) = logRisk / ln(payoff)

// generic multiple line plotter
fun <T> psdPlot(
    titleS: String, subtitleS: String, saveFile: String,
    data: List<T>,
    xname: String, yname: String, catName: String,
    xfld: (T) -> Double, yfld: (T) -> Double, catfld: (T) -> Double,
) {

    val groups = makeGroups(data, catfld)

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

    plot.save("${saveFile}.png")
    plot.save("${saveFile}.html")
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
