package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import kotlin.test.assertNotNull

// not used

class ExtraVsMarginByStrategy {
    val N = 50000
    val nruns = 10
    val nsimEst = 100
    val name = "extraVsMarginByStrategy"
    val dirName = "/home/stormy/rla/extra/$name"
    val fuzzMvrs = .01
    var phantomPct = .01

    @Test
    fun estSamplesVsMarginByStrategy() {
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val stopwatch = Stopwatch()

        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val clcaGenerator1 = ClcaContestAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzMvrs,
                parameters=mapOf("nruns" to nruns, "cat" to "oracle", "fuzzPct" to fuzzMvrs),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaContestAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzMvrs,
                parameters= mapOf("nruns" to nruns, "cat" to "noerror", "fuzzPct" to fuzzMvrs),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaContestAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzMvrs,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzMvrs),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzMvrs))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            val clcaGenerator4 = ClcaContestAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzMvrs,
                parameters= mapOf("nruns" to nruns, "cat" to "previous", "fuzzPct" to fuzzMvrs),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.previous)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val clcaGenerator5 = ClcaContestAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzMvrs,
                parameters= mapOf("nruns" to nruns, "cat" to "phantoms", "fuzzPct" to fuzzMvrs),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))
        }

        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = " Nc=${N} nruns=${nruns} fuzzPct=${fuzzMvrs} phantomPct=${phantomPct}"
        showNmrsVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showNmrsVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showFailuresVsMargin(subtitle)
        showNroundsVsMargin(subtitle)
    }

    fun showNmrsVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name number of mvrs used",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}Nmrs${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin },
            yname = "nmrvs", yfld = { it.nmvrs },
            catName = "strategy", catfld = { category(it) },
            scaleType = scaleType
        )
    }

    fun showExtraVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name extra samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}Extra${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin },
            yname = "extraSamples", yfld = { it.nmvrs - it.samplesUsed },
            catName = "strategy", catfld = { category(it) },
            scaleType = scaleType
        )
    }

    fun showFailuresVsMargin(subtitle: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showFailuresVsMargin(results, subtitle, "category") { category(it) }
    }

    fun showNroundsVsMargin(subtitle: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showNroundsVsMargin(results, subtitle, "category") { category(it) }
    }

    @Test
    fun testOne() {
        val N = 50000
        val margin = .02
        val fuzzPct = .01

        repeat(10) {
            val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct)
            val auditConfig = AuditConfig(
                AuditType.CLCA,
                true,
                quantile = .50,
                nsimEst = 100,
                clcaConfig = clcaConfig
            )

            val clcaGenerator2 = ClcaContestAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct,
                parameters = mapOf("nruns" to nruns.toDouble(), "strat" to 3.0, "fuzzPct" to fuzzPct),
                config = auditConfig
            )
            val task = clcaGenerator2.generateNewTask()

            val lastAuditRound = runAudit(name, task.workflow, quiet = false)
            assertNotNull(lastAuditRound)
            println("nmvrs = ${lastAuditRound.nmvrs}")

            val minAssertion = lastAuditRound.contestRounds.first().minAssertion()
            val lastAuditResult = minAssertion!!.auditResult!!
            println("lastAuditResult = $lastAuditResult")
            println("extra = ${lastAuditResult.nmvrs - lastAuditResult.samplesUsed}")
        }
    }

}