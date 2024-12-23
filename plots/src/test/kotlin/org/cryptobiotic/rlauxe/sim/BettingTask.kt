package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.sampling.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.sampling.SampleGenerator

// call this CobraTask ?
data class BettingTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrs: List<Cvr>,
    val d2: Int, // weight p2, p4
    val p2oracle: Double, // oracle rate of 2-vote overstatements
    val p2prior: Double, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
): RepeatedTask {
    val compareAssorter = makeStandardComparisonAssorter(cvrMean, N)
    init {
        require( N == cvrs.size)
    }

    override fun makeSampler(): SampleGenerator {
        return ComparisonWithErrorRates(cvrs, compareAssorter, p2 = p2oracle, withoutReplacement = true)
    }

    override fun makeTestFn(): RiskTestingFn {
        val adaptive = AdaptiveComparison(
            Nc = N,
            withoutReplacement = true,
            a = compareAssorter.noerror,
            d1 = 0,
            d2 = d2,
            p1 = 0.0,
            p2 = p2prior,
            p3 = 0.0,
            p4 = 0.0,
        )
        return BettingMart(
            bettingFn = adaptive, Nc = N, noerror = compareAssorter.noerror,
            upperBound = compareAssorter.upperBound, withoutReplacement = true
        )
    }

    override fun makeTestParameters(): Map<String, Double> {
        return mapOf("p2oracle" to p2oracle, "p2prior" to p2prior, "d2" to d2.toDouble())
    }

    // override fun maxSamples(): Int  = N
    override fun name(): String = "BettingTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = -p2oracle // TODO
}