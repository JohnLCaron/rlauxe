package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.estRiskFromMargin
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertNotNull

class CompareCorlaSampling {

    @Test
    fun corla24Dist() {
        val name = "Corla24Dist"
        val dirName = "$testdataDir/plots/corla/$name"
        validateOutputDir(Path(dirName))

        val consistentData = readAuditRoundData("$testdataDir/cases/corla/consistent", "consistent")
        val uniformData = readAuditContestData("$testdataDir/cases/corla/uniform", "uniform")
        val uniformMap = uniformData.associateBy { it.contestId }

        val plotData = mutableListOf<PlotData>()
        consistentData.forEach { it ->
            val uniform = uniformMap[it.contestId]
            if (uniform != null) {
                plotData.add(it.copy( estRisk = (uniform.estRisk - it.estRisk) ))
            }
        }

        val percentiles = mutableListOf<PercentileData>()

        val urisks = uniformData.map { it.estRisk }
        repeat(101) { pct ->
            val wtf: Double = percentiles().index(pct).compute(*urisks.toDoubleArray())
            println("percent=$pct = $wtf")
            percentiles.add(PercentileData(pct.toDouble(), wtf, "uniform"))
        }

        val crisks = consistentData.map { it.estRisk }
        repeat(101) { pct ->
            val wtf: Double = percentiles().index(pct).compute(*crisks.toDoubleArray())
            println("percent=$pct = $wtf")
            percentiles.add(PercentileData(pct.toDouble(), wtf, "consistent"))
        }

        val title = "Corla24 Percentiles of contests' measured risk"
        val subtitle = "nmvrs: uniform=4897, consistent=15417; targeted and contests margin >.02"

        plotCumul(
            writeFile = "$dirName/$name-AllGt02",
            title=title,
            subtitle=subtitle,
            percentiles,
            ScaleType.Linear
        )
    }

    @Test
    fun corla24Sampling() {
        val name = "Corla24Sampling"
        val dirName = "$testdataDir/plots/corla/$name"
        validateOutputDir(Path(dirName))

        val consistentData = readAuditRoundData("$testdataDir/cases/corla/consistent", "consistent")
        val uniformData = readAuditContestData("$testdataDir/cases/corla/uniform", "uniform")
        val uniformMap = uniformData.associateBy { it.contestId }

        val plotData = mutableListOf<PlotData>()
        consistentData.forEach { it ->
            val uniform = uniformMap[it.contestId]
            if (uniform != null) {
                plotData.add(it.copy( estRisk = (uniform.estRisk - it.estRisk) ))
            }
        }

        makePlot(name, dirName, plotData)
    }

    fun readAuditRoundData(location: String, cat: String): List<PlotData> {
        val countyAudit = AuditRecord.read(location)
        assertNotNull(countyAudit)
        val auditRound = countyAudit.rounds.last()

        val plotData = mutableListOf<PlotData>()
        auditRound.contestRounds.forEach {
            val minAssertion = it.contestUA.minAssertion()
            if (minAssertion != null) {
                val margin = minAssertion.assorter.margin(it.contestUA.hasStyle)
                val haveMvrs = it.haveSampleSize
                val estRisk = estRiskFromMargin(2.0 / 1.03905, margin, haveMvrs)
                plotData.add(PlotData(it.id, margin, estRisk, cat))
            }
        }
        return plotData
    }

    fun readAuditContestData(location: String, cat: String): List<PlotData> {
        val countyAudit = AuditRecord.read(location)
        assertNotNull(countyAudit)

        val plotData = mutableListOf<PlotData>()
        countyAudit.contests.forEach {
            val minAssertion = it.minAssertion()
            if (minAssertion != null) {
                val margin = minAssertion.assorter.margin(it.hasStyle)
                val haveMvrss: String = it.contest.info().metadata.get("CORLAhaveMvrs")!!
                val haveMvrs = haveMvrss.toInt()
                val estRisk = estRiskFromMargin(2.0 / 1.03905, margin, haveMvrs)
                plotData.add(PlotData(it.id, margin, estRisk, cat))
            }
        }
        return plotData
    }
}


data class PercentileData(val x: Double, val y: Double, val cat: String)

// data:  xvalue, yvalue, category name,
fun plotCumul(writeFile: String, title: String, subtitle: String, data: List<PercentileData>, scaleType: ScaleType) {
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

data class PlotData(val contestId: Int, val margin: Double, val estRisk: Double, val cat: String)

fun makePlot(name: String, dirName: String, data: List<PlotData>) {

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
        xname = "margin",
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