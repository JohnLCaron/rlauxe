package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.workflow.ContestAuditTaskGenerator
import org.cryptobiotic.rlauxe.workflow.Sampler
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.Int
import kotlin.random.Random

class ClcaSingleRoundAssortTaskGenerator(
    val N: Int,
    val margin: Double,
    val upper: Double,
    val maxRisk: Double,
    val errorRates: Double = .001,
    val parameters : Map<String, Any> = emptyMap(),
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundAuditTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundAssortTask {
        return ClcaSingleRoundAssortTask(N, margin, upper, maxRisk, errorRates, parameters)
    }
}

// Assort values and error rates are given, audit them in a single round
class ClcaSingleRoundAssortTask(
    val N: Int,
    val margin: Double,
    val upper: Double,
    val maxRisk: Double,
    val errorRates: Double,
    val parameters : Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {
    val sampling: SamplerFromAssortValues
    val noerror: Double

    init {
        noerror = 1.0 / (2.0 - margin / upper)

        val assorts = mutableListOf<Double>()
        repeat((N * errorRates).toInt()) { assorts.add( 0.0 * noerror) }
        repeat((N * errorRates).toInt()) { assorts.add( 0.5 * noerror) }
        repeat((N * errorRates).toInt()) { assorts.add( 1.5 * noerror) }
        repeat((N * errorRates).toInt()) { assorts.add( 2.0 * noerror) }

        val noerrorCount = N - assorts.size
        repeat(noerrorCount) { assorts.add(noerror) }

        sampling = SamplerFromAssortValues(assorts)
    }

    override fun name() = "ClcaSingleRoundAssortTask"

    override fun run(): WorkflowResult {
        // data class TestH0Result(
        //    val status: TestH0Status,  // how did the test conclude?
        //    val sampleCount: Int,      // number of samples used in testH0
        //    val pvalueMin: Double,     // smallest pvalue in the sequence.
        //    val pvalueLast: Double,    // last pvalue.
        //    val tracker: SampleTracker,
        //)
        val testH0Result = runAudit(N, noerror, sampling, maxRisk)


        // data class WorkflowResult(
        //    val name: String,
        //    val Nc: Int,
        //    val margin: Double,
        //    val status: TestH0Status,
        //    val nrounds: Double,
        //    val samplesUsed: Double,  // weighted
        //    val nmvrs: Double, // weighted
        //    val parameters: Map<String, Any>,
        //
        //    // from avgWorkflowResult()
        //    val failPct: Double = 100.0,
        //    val usedStddev: Double = 0.0, // success only
        //    val mvrMargin: Double = 0.0,
        //
        //    ////
        //    val startingRates: ClcaErrorCounts? = null, // starting error rates (clca only)
        //    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
        //)

        val result =
            WorkflowResult(
                name(),
                N,
                margin = margin,
                testH0Result.status,
                nrounds = 1.0,
                testH0Result.sampleCount.toDouble(),
                testH0Result.sampleCount.toDouble(),  // wtf ??
                parameters=parameters,
                0.0,
                mvrMargin = margin,
                startingRates = null,
                measuredCounts = (testH0Result.tracker as ClcaErrorTracker).measuredClcaErrorCounts(),
            )
        return result
    }

    fun runAudit(
        N: Int,
        noerror: Double,
        sampling: Sampler,
        maxRisk: Double,
    ): TestH0Result {

        val bettingFn = GeneralAdaptiveBetting(N,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms=0, oaAssortRates = null, d=0,  maxRisk = maxRisk, debug=false)

        val tracker = ClcaErrorTracker(noerror, upper)
        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = N,
            sampleUpperBound = 2*noerror,
            riskLimit = .05,
            withoutReplacement = true,
            tracker=tracker
        )
        tracker.setDebuggingSequences(testFn.setDebuggingSequences())

        val testH0Result = testFn.testH0(
            sampling.maxSamples(),
            terminateOnNullReject = true,
        ) { sampling.sample() }


        return testH0Result
    }
}

class SamplerFromAssortValues(val assortValues : List<Double>): Sampler {
    val maxSamples = assortValues.size
    val permutedIndex = MutableList(maxSamples) { it }
    private var idx = 0
    private var count = 0

    init {
        reset()
    }

    override fun sample(): Double {
        require (idx < maxSamples)
        require (permutedIndex[idx] < maxSamples)
        count++
        return assortValues[permutedIndex[idx++]]
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = idx // TODO

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()

    fun sampleMean() = assortValues.average()
}


