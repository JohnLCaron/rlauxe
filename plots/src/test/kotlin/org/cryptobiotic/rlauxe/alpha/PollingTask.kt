package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.workflow.Sampler
import org.cryptobiotic.rlauxe.workflow.PollingSampler
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.concur.RepeatedTask
import org.cryptobiotic.rlauxe.util.mean2margin

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
    val pairs = cvrs.zip(cvrs)

    init {
        require(N == cvrs.size)
    }

    override fun makeSampler(): Sampler {
        val contestUA = ContestWithAssertions(makeContestsFromCvrs(cvrs).first()).addStandardAssertions()
        return PollingSampler(contestUA.id, pairs, pollingAssorter)
    }

    override fun makeTestFn(): RiskTestingFn {
        val tracker = ClcaErrorTracker(0.0, pollingAssorter.upperBound())

        return if (useFixedEstimFn) {
            AlphaMart(estimFn = FixedEstimFn(cvrMean), N = N, tracker=tracker)
        } else {
            eta0 = cvrMean

            val useEstimFn = TruncShrinkage(
                N, withoutReplacement = withoutReplacement, upperBound = pollingAssorter.upperBound(),
                d = d,
                eta0 = eta0,
            )
            AlphaMart(
                estimFn = useEstimFn,
                N = N,
                upperBound = pollingAssorter.upperBound(),
                withoutReplacement = withoutReplacement,
                tracker=tracker,
            )
        }
    }

    override fun makeTestParameters(): Map<String, Double> {
        return mapOf("eta0" to eta0, "d" to d.toDouble(), "margin" to mean2margin(reportedMean()))
    }

    // override fun maxSamples(): Int  = N
    override fun name(): String = "AlphaPollingTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = cvrMeanDiff
}
