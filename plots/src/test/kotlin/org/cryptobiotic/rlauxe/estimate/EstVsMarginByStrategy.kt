package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.log10
import kotlin.test.Test

class EstVsMarginByStrategy {
    val Nc = 50000
    val nruns = 250  // number of times to run workflow
    val nsimEst = 100 // number of simulations
    val phantomPct = .01
    val fuzzMvrs = .01
    val simFuzzPct = .01

    @Test
    fun testDefault() {
        val margin = .03
        // val simFuzzPct = .01
        val fuzzMvrs = .01

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
        val clcaGenerator = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.01, fuzzMvrs,
            parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "phantoms"),
            auditConfigIn=auditConfig)
        val task = clcaGenerator.generateNewTask()

        val nmvrs = runWorkflow("phantoms", task.workflow, task.testCvrs, quiet = false)
        println("nmvrs = $nmvrs")

        val minAssertion = task.workflow.getContests().first().minClcaAssertion()!!
        if (minAssertion.roundResults.isNotEmpty()) {
            val lastRound = minAssertion.roundResults.last()
            println("lastRound = $lastRound")
            println("extra = ${lastRound.estSampleSize - lastRound.samplesNeeded}")
        }
    }

    @Test
    fun estSamplesVsMarginByStrategy() {
        val name = "estByStrategy"
        val dirName = "/home/stormy/temp/workflow/$name"

        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val auditConfig1 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=simFuzzPct))
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "fuzzPct", "simFuzzPct" to simFuzzPct),
                auditConfigIn=auditConfig1)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val auditConfig2 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.mixed, simFuzzPct=simFuzzPct))
            val clcaGenerator2 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "mixed", "simFuzzPct" to simFuzzPct),
                auditConfigIn=auditConfig2)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val auditConfig3 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
            val clcaGenerator3 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "oracle", "simFuzzPct" to simFuzzPct),
                auditConfigIn=auditConfig3)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            val auditConfig4 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            val clcaGenerator4 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "noerror", "simFuzzPct" to simFuzzPct),
                auditConfigIn=auditConfig4)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val auditConfig5 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.previous, simFuzzPct=simFuzzPct))
            val clcaGenerator5 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "previous", "simFuzzPct" to simFuzzPct),
                auditConfigIn=auditConfig5)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val name = "estByStrategy"
        val dirName = "/home/stormy/temp/workflow/$name"

        val subtitle = "Nc=${Nc} nruns=${nruns} mvrFuzz=$fuzzMvrs"

        showExtra(dirName, name, subtitle, Scale.Linear)
        showExtra(dirName, name, subtitle, Scale.Log)
        showExtra(dirName, name, subtitle, Scale.Pct)
        showFailuresVsMargin(dirName, name, )
        showNroundsVsMargin(dirName, name, )

        showSamplesNeeded(dirName, name, subtitle, Scale.Linear)
        showSamplesNeeded(dirName, name, subtitle, Scale.Log)
        showSamplesNeeded(dirName, name, subtitle, Scale.Pct)

        showNmvrsNeeded(dirName, name, subtitle, Scale.Linear)
        showNmvrsNeeded(dirName, name, subtitle, Scale.Log)
        showNmvrsNeeded(dirName, name, subtitle, Scale.Pct)
    }

    @Test
    fun estSamplesVsMarginByStrategyWithPhantoms() {
        val margins = listOf(.025, .03, .04, .05, .06, .07, .08, .09, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
            margins.forEach { margin ->
                val auditConfig1 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=simFuzzPct))
                val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "fuzzPct", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig1)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

                val auditConfig2 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.mixed))
                val clcaGenerator2 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "mixed", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig2)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

                val auditConfig3 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
                val clcaGenerator3 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "oracle", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig3)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

                val auditConfig4 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
                val clcaGenerator4 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "noerror", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig4)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

                val auditConfig5 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
                val clcaGenerator5 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "phantoms", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig5)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))

                val auditConfig6 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.previous))
                val clcaGenerator6 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "previous", "simFuzzPct" to simFuzzPct),
                    auditConfigIn=auditConfig6)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator6))
            }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "estByStrategyWithPhantoms"
        val dirName = "/home/stormy/temp/workflow/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlotsWithPhantoms()
    }

    @Test
    fun regenPlotsWithPhantoms() {
        val name = "estByStrategyWithPhantoms"
        val dirName = "/home/stormy/temp/workflow/$name"

        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzMvrs=$fuzzMvrs phantomPct=$phantomPct"

        showExtra(dirName, name, subtitle, Scale.Linear)
        showExtra(dirName, name, subtitle, Scale.Log)
        showExtra(dirName, name, subtitle, Scale.Pct)
        showFailuresVsMargin(dirName, name, )
        showNroundsVsMargin(dirName, name, )

        showSamplesNeeded(dirName, name, subtitle, Scale.Linear)
        showSamplesNeeded(dirName, name, subtitle, Scale.Log)
        showSamplesNeeded(dirName, name, subtitle, Scale.Pct)

        showNmvrsNeeded(dirName, name, subtitle, Scale.Linear)
        showNmvrsNeeded(dirName, name, subtitle, Scale.Log)
        showNmvrsNeeded(dirName, name, subtitle, Scale.Pct)
    }

    val keepPrevious = true

    fun showExtra(dir: String, name: String, subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dir/${name}.cvs")
        val data = io.readResults().filter { keepPrevious || it.parameters["cat"] != "previous"}

        val catName = "strategy"
        val catfld = { it: WorkflowResult -> category(it) }

        wrsPlot(
            titleS = "$name extra samples",
            subtitleS = subtitle,
            data,
            "$dir/Extra${yscale.name}",
            "margin",
            yscale.desc("extra samples"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> (it.nmvrs - it.samplesNeeded)
                Scale.Log -> log10( (it.nmvrs - it.samplesNeeded))// needed?
                Scale.Pct -> (100* (it.nmvrs - it.samplesNeeded)/it.nmvrs )
            }},
            catfld = catfld,
        )
    }

    fun showFailuresVsMargin(dirName: String, name: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults().filter { keepPrevious || it.parameters["cat"] != "previous"}

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="strategy") { category(it) }
    }

    fun showNroundsVsMargin(dirName: String, name: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults().filter { keepPrevious || it.parameters["cat"] != "previous"}

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsMargin(results, catName="strategy") { category(it) }
    }

    fun showSamplesNeeded(dir: String, name: String, subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dir/${name}.cvs")
        val data = io.readResults().filter { keepPrevious || it.parameters["cat"] != "previous"}
        val catName = "strategy"
        val catfld = { it: WorkflowResult -> category(it) }

        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            wrs = data,
            "$dir/Samples${yscale.name}",
            "margin",
            yscale.desc("samplesNeeded"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> it.samplesNeeded
                Scale.Log -> log10(it.samplesNeeded)
                Scale.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }

    fun showNmvrsNeeded(dir: String, name: String, subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dir/${name}.cvs")
        val data = io.readResults().filter { keepPrevious || it.parameters["cat"] != "previous"}
        val catName = "strategy"
        val catfld = { it: WorkflowResult -> category(it) }

        wrsPlot(
            titleS = "$name nmvrs",
            subtitleS = subtitle,
            wrs = data,
            "$dir/Nmvrs${yscale.name}",
            "margin",
            yscale.desc("nmvrs"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> it.nmvrs
                Scale.Log -> log10(it.nmvrs)
                Scale.Pct -> (100*it.nmvrs/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }


}