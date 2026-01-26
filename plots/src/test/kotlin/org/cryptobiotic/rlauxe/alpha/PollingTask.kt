package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.betting.AlphaMart
import org.cryptobiotic.rlauxe.betting.FixedEstimFn
import org.cryptobiotic.rlauxe.betting.PollingSamplerTracker
import org.cryptobiotic.rlauxe.betting.RiskMeasuringFn
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TruncShrinkage
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
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

    override fun makeSampler(): SamplerTracker {
        val contestUA = ContestWithAssertions(makeContestsFromCvrs(cvrs).first()).addStandardAssertions()
        return PollingSamplerTracker(contestUA.id, pairs, pollingAssorter)
    }

    override fun makeTestFn(): RiskMeasuringFn {
        return if (useFixedEstimFn) {
            AlphaMart(estimFn = FixedEstimFn(cvrMean), N = N, upperBound = pollingAssorter.upperBound())
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
