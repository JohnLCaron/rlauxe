package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.core.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.RiskTestingFn
import org.cryptobiotic.rlauxe.core.GenSampleFn
import org.cryptobiotic.rlauxe.corla.Corla
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter

data class CorlaTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrs: List<Cvr>,
    val riskLimit: Double = 0.05,
    val p2: Double,      // oracle rate of 2-vote overstatements
    val p1: Double,     // oracle rate of 1-vote overstatements
): RepeatedTask {
    val compareAssorter = makeStandardComparisonAssorter(cvrMean)

    init {
        require(N == cvrs.size)
    }

    override fun makeSampler(): GenSampleFn {
        // generate with the oracle, or true rates
        return ComparisonWithErrorRates(cvrs, compareAssorter, p1 = p1, p2 = p2, withoutReplacement = true)
    }

    override fun makeTestFn(): RiskTestingFn {
        return Corla(
            N = N, riskLimit = riskLimit, reportedMargin = compareAssorter.margin, noerror = compareAssorter.noerror,
            p1 = p1, p2 = p2, p3 = 0.0, p4 = 0.0
        )
    }

    override fun makeTestParameters(): Map<String, Double> {
        return mapOf("p1" to p1, "p2oracle" to p2)
    }

    override fun maxSamples(): Int  = N
    override fun name(): String = "CorlaTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = -p2 // TODO
}