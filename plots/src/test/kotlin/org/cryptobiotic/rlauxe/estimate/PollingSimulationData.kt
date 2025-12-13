package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.df

import org.junit.jupiter.api.Test

// TODO candidate for removal

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
                val sim = ContestSimulation.make2wayTestContest(Nc = Nc, margin, underVotePct, phantomPct)
                val contest = sim.contest
                val assorter = PluralityAssorter.makeWithVotes(contest, winner=0, loser=1)
                val cvrs = sim.makeCvrs()
                val assortWithPhantoms = cvrs.map { cvr -> assorter.assort(cvr, usePhantoms = true)}.average()
                results.add(PollingSimulationData(margin, underVotePct, phantomPct, Nc, assortWithPhantoms))
            }
        }

        val plotter = PlotPollingSimulationData("$testdataDir/polling/", "pollingSimulation")
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

        genericPlotter(
            "PollingSimulation",
            "Nc=$Nc underVotePct=$underVotePct",
            "$dir/$filename",
            data,
            "reportedMargin", "assortWithPhantoms", "phantomPct",
            xfld = { it.reportedMargin },
            yfld = { it.assortWithPhantoms },
            catfld = { df(it.phantomPct) },
        )
    }
}


