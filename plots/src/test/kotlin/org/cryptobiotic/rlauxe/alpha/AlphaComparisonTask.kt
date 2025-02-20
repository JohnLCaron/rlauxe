package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.sampling.Sampler
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.math.max

// CANDIDATE FOR REMOVAL

// run AlphaMart with TrunkShrinkage in repeated trials
// this creates the riskTestingFn for you
fun runAlphaMartRepeated(
    drawSample: Sampler,
    // maxSamples: Int,
    eta0: Double,
    d: Int = 500,
    withoutReplacement: Boolean = true,
    ntrials: Int = 1,
    upperBound: Double = 1.0,
    estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RunTestRepeatedResult {

    val t = 0.5
    val c = max(eps, ((eta0 - t) / 2))

    val useEstimFn = estimFn ?: TruncShrinkage(drawSample.maxSamples(), true, upperBound = upperBound, d = d, eta0 = eta0, c = c)

    val alpha = AlphaMart(
        estimFn = useEstimFn,
        N = drawSample.maxSamples(),
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return runTestRepeated(
        drawSample = drawSample,
        // maxSamples = maxSamples,
        terminateOnNullReject = true,
        ntrials = ntrials,
        testFn = alpha,
        testParameters = mapOf("eta0" to eta0, "d" to d.toDouble()),
        margin = mean2margin(eta0),
        Nc=drawSample.maxSamples(), // TODO ??
    )
}