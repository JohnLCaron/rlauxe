package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.ComparisonNoErrors
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.AlphaComparisonTask
import org.cryptobiotic.rlauxe.sim.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.sim.runAlphaMartRepeated
import org.cryptobiotic.rlauxe.util.listToMap
import org.junit.jupiter.api.Test

// TODO
class TestAuditComparison {

    @Test
    fun testComparisonWorkflow() {
        // simulated CVRs
        val theta = .51
        val N = 10000

        val cvrs = makeCvrsByExactMean(N, theta)
        println("ncvrs = ${cvrs.size} theta=$theta")

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B"),
        )
        val contestUA = ContestUnderAudit(info, cvrs).makeComparisonAssertions(cvrs)
        val assertion = contestUA.comparisonAssertions.first()

        val cvrSampler = ComparisonNoErrors(cvrs, assertion.cassorter)
        val result = runAlphaMartRepeated(
            drawSample = cvrSampler,
            maxSamples = N,
            eta0 = cvrSampler.sampleMean(),
            d = 100,
            ntrials = 100,
            upperBound = assertion.cassorter.upperBound()
        )
        println(result)
    }

    // TODO setting the eta0Factor by hand
    @Test
    fun genAlphaComparisonDValues() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val thetas = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)

        val ns = listOf(5000, 10000, 20000)
        val ntrials = 100

        val tasks = mutableListOf<AlphaComparisonTask>()
        var taskIdx = 0
        dlist.forEach { d ->
            thetas.forEach { theta ->
                ns.forEach { N ->
                    cvrMeanDiffs.forEach { cvrMeanDiff ->
                        val cvrMean = theta - cvrMeanDiff
                        val cvrs = makeCvrsByExactMean(N, cvrMean)
                        tasks.add(AlphaComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff, eta0Factor=1.9, d = d, cvrs = cvrs))
                    }
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/dvalues/alphaComparisonDValues.csv")

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

}
