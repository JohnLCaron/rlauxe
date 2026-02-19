package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.workflow.ContestAuditTaskGenerator
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.Int

class ClcaSingleRoundAssortTaskGenerator(
    val N: Int,
    val margin: Double,
    val upper: Double,
    val maxLoss: Double,
    val errorRates: Double = .001,
    val parameters : Map<String, Any> = emptyMap(),
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundAuditTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundAssortTask {
        return ClcaSingleRoundAssortTask(N, margin, upper, maxLoss, errorRates, parameters)
    }
}

// Assort values and error rates are given, audit them in a single round
class ClcaSingleRoundAssortTask(
    val N: Int,
    val margin: Double,
    val upper: Double,
    val maxLoss: Double,
    val errorRates: Double,
    val parameters : Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {
    val sampling: SamplerTracker
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

        sampling = SampleFromArray(assorts.toDoubleArray())
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
        val testH0Result = runAudit(N, noerror, sampling, maxLoss)


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
                measuredCounts = null,
            )
        return result
    }

    fun runAudit(
        N: Int,
        noerror: Double,
        samplerTracker: SamplerTracker,
        maxLoss: Double,
    ): TestH0Result {

        // data class GeneralAdaptiveBetting2(
        //    val Npop: Int, // population size for this contest
        //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
        //    val nphantoms: Int, // number of phantoms in the population
        //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
        //
        //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val debug: Boolean = false,
        val betFun = GeneralAdaptiveBetting2(
            Npop = N,
            aprioriCounts = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = 0,
            maxLoss = maxLoss,
            oaAssortRates = null,
            d = 0,
            debug=false,
        )

        val tracker = ClcaErrorTracker(noerror, upper)
        val testFn = BettingMart(
            bettingFn = betFun,
            N = N,
            sampleUpperBound = 2*noerror,
            riskLimit = .05,
            withoutReplacement = true,
            tracker=samplerTracker
        )
        tracker.setDebuggingSequences(testFn.setDebuggingSequences())

        val testH0Result = testFn.testH0(
            sampling.maxSamples(),
            terminateOnNullReject = true,
        ) { sampling.sample() }


        return testH0Result
    }
}


