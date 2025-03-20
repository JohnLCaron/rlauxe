package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.audit.Sampler
import org.cryptobiotic.rlauxe.audit.PollWithoutReplacement
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.concur.RepeatedTask
import kotlin.math.max

data class PollingTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrMeanDiff: Double,
    val d: Int, // parameter for shrinkTruncate
    val cvrs: List<Cvr>,
    val withoutReplacement: Boolean = true,
    val useFixedEstimFn: Boolean = false
): RepeatedTask {
    val theta = cvrMean + cvrMeanDiff
    val pollingAssorter = makeStandardPluralityAssorter(N)
    var eta0: Double = 0.0

    init {
        require(N == cvrs.size)
    }

    override fun makeSampler(): Sampler {
        val contestUA = ContestUnderAudit(makeContestsFromCvrs(cvrs).first())
        return PollWithoutReplacement(contestUA.id, cvrs, pollingAssorter)
    }

    override fun makeTestFn(): RiskTestingFn {
        return if (useFixedEstimFn) {
            AlphaMart(estimFn = FixedEstimFn(cvrMean), N = N)
        } else {
            val t = 0.5
            eta0 = cvrMean
            val c = max(eps, ((eta0 - t) / 2))

            val useEstimFn = TruncShrinkage(
                N, withoutReplacement = withoutReplacement, upperBound = pollingAssorter.upperBound(),
                d = d, eta0 = eta0, c = c
            )
            AlphaMart(
                estimFn = useEstimFn,
                N = N,
                upperBound = pollingAssorter.upperBound(),
                withoutReplacement = withoutReplacement,
            )
        }
    }

    override fun makeTestParameters(): Map<String, Double> {
        return mapOf("eta0" to eta0, "d" to d.toDouble())
    }

    // override fun maxSamples(): Int  = N
    override fun name(): String = "AlphaPollingTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = cvrMeanDiff
}
