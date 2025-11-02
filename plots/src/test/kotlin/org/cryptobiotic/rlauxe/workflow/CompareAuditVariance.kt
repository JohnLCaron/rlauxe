package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditVariance {
    val nruns = 1000
    val nsimEst = 100
    val N = 50000
    val fuzzPct = .01

    @Test
    fun genAuditVariationPollingPlots() {
        val margins =
            listOf(.02, .03, .04, .05, .06, .07, .08, .10)

        val pollConfig = AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst)
        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                 N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct, nsimEst = nsimEst,
                 parameters = mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "auditType" to "polling"),
                 auditConfig = pollConfig,
             )
             tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "pollingVariance"
        val dirName = "/home/stormy/rla/extra/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPolling()
    }

    @Test
    fun regenPolling() {
        val name = "pollingVariance"
        val dirName = "/home/stormy/rla/extra/$name"
        val subtitle = "Nc=${N} nruns=${nruns} fuzzPct=$fuzzPct"
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showNroundsVsMargin(dirName, name, subtitle, catName = "auditType", catfld = {"polling"} )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.Linear )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.LogLinear )
    }

    @Test
    fun genAuditVariationClcaPlots() {
        val margins =
            listOf(.006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val clcaConfig = AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct)
        )

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
             val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct, nsimEst = nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "auditType" to "clca"),
                config=clcaConfig
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "clcaVariance"
        val dirName = "/home/stormy/rla/extra/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenClca()
    }

    @Test
    fun regenClca() {
        val name = "clcaVariance"
        val dirName = "/home/stormy/rla/extra/$name"
        val subtitle = "Nc=${N} nruns=${nruns} fuzzPct=$fuzzPct"
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showNroundsVsMargin(dirName, name, subtitle, catName = "auditType", catfld = {"clca"} )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.Linear )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.LogLinear )
    }

    fun showSampleVarianceVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlotMultipleFields(
            titleS = "$name samples needed and variance",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin },
            yname = "samplesNeeded",
            yfld = { cat: String, wr: WorkflowResult ->
                when (cat) {
                    "needed" -> wr.samplesUsed
                    "plusStdev" -> wr.samplesUsed + wr.usedStddev
                    "minusStdev" -> wr.samplesUsed - wr.usedStddev
                    else -> 0.0
                }
            },
            catName = "legend",
            catflds = listOf("needed", "plusStdev", "minusStdev"),
            scaleType=scaleType,
        )
    }

    fun showNroundsVsMargin(dirName: String, name:String, subtitle: String, catName: String, catfld: (WorkflowResult) -> String) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlot(
            titleS = "$name number of audit rounds",
            subtitleS = subtitle,
            wrs=data,
            writeFile="$dirName/${name}Nrounds",
            xname="margin", xfld = { it.margin },
            yname="auditRounds", yfld = { it.nrounds},
            catName=catName, catfld = catfld,
        )
    }

    fun showNmvrsVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType,) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlotMultipleFields(
            titleS = "$name nmvrs and samplesNeeded",
            subtitleS = subtitle,
            wrs=data,
            writeFile="$dirName/${name}Nmvrs${scaleType.name}",
            xname="margin", xfld = { it.margin },
            yname = "number",
            yfld = { cat: String, wr: WorkflowResult ->
                when (cat) {
                    "nmvrs" -> wr.nmvrs
                    "samplesNeeded" -> wr.samplesUsed
                    else -> 0.0
                }
            },
            catName = "legend",
            catflds = listOf("nmvrs", "samplesNeeded"),
            scaleType=scaleType,
        )
    }

}