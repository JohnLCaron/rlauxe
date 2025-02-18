package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
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

        val pollConfig = AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst, samplePctCutoff = 1.0, minMargin = 0.0)
        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingWorkflowTaskGenerator(
                 N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct, nsimEst = nsimEst,
                 parameters = mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "auditType" to "polling"),
                 auditConfig = pollConfig,
             )
             tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "pollingVariance"
        val dirName = "/home/stormy/temp/extra/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPolling()
    }

    @Test
    fun regenPolling() {
        val name = "pollingVariance"
        val dirName = "/home/stormy/temp/extra/$name"
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

        val clcaConfig = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst, samplePctCutoff = 1.0, minMargin = 0.0,
            clcaConfig =ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct))

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
             val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct, nsimEst = nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "auditType" to "clca"),
                auditConfig=clcaConfig
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "clcaVariance"
        val dirName = "/home/stormy/temp/extra/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenClca()
    }

    @Test
    fun regenClca() {
        val name = "clcaVariance"
        val dirName = "/home/stormy/temp/extra/$name"
        val subtitle = "Nc=${N} nruns=${nruns} fuzzPct=$fuzzPct"
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleVarianceVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showNroundsVsMargin(dirName, name, subtitle, catName = "auditType", catfld = {"clca"} )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.Linear )
        showNmvrsVsMargin(dirName, name, subtitle, ScaleType.LogLinear )
    }

    fun showSampleVarianceVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
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
                    "needed" -> wr.samplesNeeded
                    "plusStdev" -> wr.samplesNeeded + wr.neededStddev
                    "minusStdev" -> wr.samplesNeeded - wr.neededStddev
                    else -> 0.0
                }
            },
            catName = "legend",
            catflds = listOf("needed", "plusStdev", "minusStdev"),
            scaleType=scaleType,
        )
    }

    fun showNroundsVsMargin(dirName: String, name:String, subtitle: String, catName: String, catfld: (WorkflowResult) -> String) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
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
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
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
                    "samplesNeeded" -> wr.samplesNeeded
                    else -> 0.0
                }
            },
            catName = "legend",
            catflds = listOf("nmvrs", "samplesNeeded"),
            scaleType=scaleType,
        )
    }

}