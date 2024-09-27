package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.SRTcsvWriter
import org.junit.jupiter.api.Test

// explore comparison parameters
class ComparisonParameters {

    @Test
    fun cvrComparisonND() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.7, 1.8, 1.9, 1.95)

        val N = 10000
        val d = 10000
        val ntrials = 100

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0
        eta0Factors.forEach { eta0Factor ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                cvrMeanDiffs.forEach { cvrMeanDiff ->
                    tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff, eta0Factor, d = d, cvrs = cvrs))
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/cvrComparisonND.csv")

        val runner = ComparisonRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

}