package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.co.corla.pValueApproximation
import org.cryptobiotic.rlauxe.corlaInput.Co2020G
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.Locations.home
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertNotNull

class CompareCorlaRisk {
    val root = "$home/rla/plots/corlaRisk"

    @Test
    fun corla26Risk() {
        val name = "corla26Risk"
        val fileName = "$root/$name"
        validateOutputDir(Path(root))

        val input = Statewide2026General()
        val mcontests = input.mergedInfo().mergedContestInfo
        val risks = mcontests.filter { it.choices.size > it.voteForN }.map { mcontest ->
            var pe = mcontest.errors
            if (pe.isEmpty()) pe = listOf(0, 0, 0, 0)
            val margin = mcontest.marginInVotes / mcontest.corlaPop.toDouble()

            pValueApproximation(
                n = mcontest.strata!!.nmvrs, // n = auditedBallots the number of ballots audited for this strata
                dilutedMargin = margin, // probably minimum margin of the contest's assertions,
                gamma = 1.03905,  // “inflator” γ > 1; same purpose as MaxRisk I think. Corla uses 1.03905.
                pe[0], pe[1], pe[2], pe[3],
            )
        }
        println("ncontests = ${risks.size}")

        val percentiles = mutableListOf<PercentileData>()
        repeat(101) { pct ->
            val yval: Double = percentiles().index(pct).compute(*risks.toDoubleArray())
            // println("percent=$pct = $yval")
            percentiles.add(PercentileData(pct.toDouble(), yval, "CoRLA"))
        }

        val title = "Corla 2026 General Percentiles of Corla measured risk (smaller is better)"
        val subtitle = "Corla nsamples=5586, ncontests = 678 targeted = 71"

        plotCumulRisk(
            writeFile = fileName,
            title = title,
            subtitle = subtitle,
            percentiles,
            ScaleType.Linear
        )
    }

    @Test
    fun corla20Risk() {
        val name = "corla20Risk"
        val fileName = "$root/$name"
        validateOutputDir(Path(root))

        val input = Co2020G()
        val mcontests = input.mergedInfo().mergedContestInfo
        val risks = mcontests.filter { it.choices.size > it.voteForN }.map { mcontest ->
            var pe = mcontest.errors
            if (pe.isEmpty()) pe = listOf(0, 0, 0, 0)
            val margin = mcontest.marginInVotes / mcontest.corlaPop.toDouble()

            pValueApproximation(
                n = mcontest.strata!!.nmvrs, // n = auditedBallots the number of ballots audited for this strata
                dilutedMargin = margin, // probably minimum margin of the contest's assertions,
                gamma = 1.03905,  // “inflator” γ > 1; same purpose as MaxRisk I think. Corla uses 1.03905.
                pe[0], pe[1], pe[2], pe[3],
            )
        }
        println("ncontests = ${risks.size}")

        val percentiles = mutableListOf<PercentileData>()
        repeat(101) { pct ->
            val yval: Double = percentiles().index(pct).compute(*risks.toDoubleArray())
            // println("percent=$pct = $yval")
            percentiles.add(PercentileData(pct.toDouble(), yval, "CoRLA"))
        }

        val title = "Corla 2020 General Percentiles of Corla measured risk (smaller is better)"
        val subtitle = "Corla nsamples=8161, ncontests = 579 targeted = 66}"

        plotCumulRisk(
            writeFile = fileName,
            title = title,
            subtitle = subtitle,
            percentiles,
            ScaleType.Linear
        )
    }
}

// data:  xvalue, yvalue, category name,
fun plotCumulRisk(writeFile: String, title: String, subtitle: String, data: List<PercentileData>, scaleType: ScaleType) {
    genericPlotter(
        titleS = title,
        subtitleS = subtitle,
        writeFile = writeFile,
        scaleType=scaleType,
        data = data,
        xname = "percentile", xfld = { it.x },
        yname = "measured risk %", yfld = { it.y },
        catName = "sampling", catfld = { it.cat },
        addPoints = false,
        addHLineAt= .03,
    )
}

data class RiskPlotData(val contestId: Int, val margin: Double, val estRisk: Double, val cat: String)

fun makeRiskPlot(name: String, dirName: String, data: List<RiskPlotData>) {

    // add fake one to show the plot better
    // catPoints.add(NmvrPoint("OneAudit", 12, .001))

    val title = "$name estimated maximum risk"
    val subtitle = "consistent target contests only"
    val scaleType = ScaleType.Linear

    genericScatter(
        titleS = title,
        subtitleS = subtitle,
        writeFile="$dirName/${name}${scaleType.name}",
        data = data,
        xname = "noerror",
        yname = "risk (%)",
        catName = "sampling",
        xfld = { it.margin },
        yfld = { 100 * it.estRisk },
        catfld = { it.cat },
        scaleType=scaleType,
        symbols=mapOf(
            "consistent" to Symbol.CIRCLE_SMALL,
            "uniform" to Symbol.CIRCLE_SMALL,
        ),
        colors=mapOf(
            "consistent" to Color.RED,
            "uniform" to Color.LIGHT_BLUE,
        ),
    )
}