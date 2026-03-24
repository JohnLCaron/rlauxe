package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.sqrt

class RepeatedWorkflowRunner (val nruns: Int, val taskGenerator: ContestAuditTaskGenerator):
    ConcurrentTask<List<WorkflowResult>> {

    override fun name(): String = "Repeated-${taskGenerator.name()}"

    override fun run(): List<WorkflowResult> {
        val results = mutableListOf<WorkflowResult>()
        repeat(nruns) {
            val task = taskGenerator.generateNewTask()
            results.add(task.run())
        }
        return results // dont have a generic way to reduce this
    }
}

data class WorkflowResult(
    val name: String,
    val Nc: Int,
    val margin: Double,
    val status: TestH0Status,
    val nrounds: Double, // avg nrounds for successes
    val samplesUsed: Double,  // difference from nmvrs is the extra
    val nmvrs: Double, // nmvrs for this contest and round; avg nmvrs for successes
    val parameters: Map<String, Any>,
    val wtf: Double = 0.0,  // experimental, temporary field for whatever

    // only when averaging
    val failPct: Double = 100.0,
    val usedStddev: Double = 0.0, // success only
    val mvrMargin: Double = 0.0,  // true margin

    ////
    val startingRates: ClcaErrorCounts? = null, // starting error rates (clca only)
    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
) {
    fun Dparam(key: String): Double {
        return (parameters[key]!! as String).toDouble()
    }

    fun show() = buildString {
        appendLine("WorkflowResult(name='$name', Nc=$Nc, margin=$margin, status=$status, nrounds=$nrounds, samplesUsed=$samplesUsed, nmvrs=$nmvrs, parameters=$parameters, failPct=$failPct, usedStddev=$usedStddev, mvrMargin=$mvrMargin")
        if (startingRates != null) append("  startingRates=${startingRates.show()}")
        if (measuredCounts != null) append("  measuredCounts=${measuredCounts.show()}")
        appendLine(")")
    }
}

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val successRuns = runs.filter { it.status.success }

    val result =  if (runs.isEmpty()) { // TODO why all empty?
        WorkflowResult(
            "empty",
            0,
            0.0,
            TestH0Status.ContestMisformed,
            0.0, 0.0, 0.0,
            emptyMap(),
            )
    } else if (successRuns.isEmpty()) { // TODO why all empty?
        val first = runs.first()
        WorkflowResult(
            first.name,
            first.Nc,
            first.margin,
            TestH0Status.MinMargin, // TODO maybe TestH0Status.AllFail ?
            0.0, first.Nc.toDouble(), first.Nc.toDouble(),
            first.parameters,
            mvrMargin=runs.filter{ it.nrounds > 0 }.map { it.mvrMargin }.average(),
        )
    } else {
        val first = successRuns.first()
        val failures = runs.size - successRuns.count()
        val successPct = successRuns.count() / runs.size.toDouble()
        val failPct = failures / runs.size.toDouble()
        val Nc = first.Nc
        val welford = Welford()
        successRuns.forEach { welford.update(it.samplesUsed) }

        WorkflowResult(
            first.name,
            Nc,
            first.margin,
            first.status, // hmm kinda bogus
            nrounds = successRuns.filter{ it.nrounds > 0 } .map { it.nrounds }.average(),
            samplesUsed = welford.mean, // success only
            // nmvrs = successPct * successRuns.map { it.nmvrs }.average() + failPct * Nc, TODO why weighted ?
            nmvrs = successRuns.map { it.nmvrs }.average(),
            first.parameters,
            wtf = runs.map { it.wtf }.average(),  // TODO runs or successRuns?

            failPct = 100.0 * failPct,
            usedStddev=sqrt(welford.variance()), // success only
            mvrMargin=runs.filter{ it.nrounds > 0 }.map { it.mvrMargin }.average(), // TODO runs or successRuns?
        )
    }

    return result
}

