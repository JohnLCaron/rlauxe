package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.estimate.RunRepeatedResult
import org.cryptobiotic.rlauxe.workflow.Sampler
import org.cryptobiotic.rlauxe.estimate.runRepeated
import org.cryptobiotic.rlauxe.util.mean2margin

// run AlphaMart with TrunkShrinkage in repeated trials
// this creates the riskTestingFn for you
fun runAlphaMartRepeated(
    name: String,
    drawSample: Sampler,
    N: Int,
    eta0: Double,
    d: Int = 500,
    withoutReplacement: Boolean = true,
    ntrials: Int = 1,
    upper: Double = 1.0,
    sampleUpperBound: Double = 1.0,
    estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RunRepeatedResult {

    val useEstimFn = estimFn ?: TruncShrinkage(
        drawSample.maxSamples(),
        true,
        upperBound = sampleUpperBound,
        d = d,
        eta0 = eta0
    )

    val tracker = ClcaErrorTracker(0.0, upper)

    val alpha = AlphaMart(
        estimFn = useEstimFn,
        N = drawSample.maxSamples(),
        upperBound = sampleUpperBound,
        withoutReplacement = withoutReplacement,
        tracker = tracker,
    )

    return runRepeated(
        name = name,
        drawSample = drawSample,
        terminateOnNullReject = true,
        ntrials = ntrials,
        testFn = alpha,
        testParameters = mapOf("eta0" to eta0, "d" to d.toDouble(), "margin" to mean2margin(eta0)),
        N=N,
        tracker = tracker
   )
}