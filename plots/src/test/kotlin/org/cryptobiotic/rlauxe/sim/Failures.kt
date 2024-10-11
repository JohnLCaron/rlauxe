package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.junit.jupiter.api.Test

class Failures {

    @Test
    fun cvrComparisonFailure() {
        val cvrMeanDiffs = listOf(-.15, -.01, -.005, 0.0, .005, .01, .15)
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .508, .51, .52, .53, .54, .55)

        val N = 10000
        val d = 500
        val ntrials = 1000
        val eta0Factor = 1.99

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0
        cvrMeans.forEach { cvrMean ->
            cvrMeanDiffs.forEach { cvrMeanDiff ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(
                    ComparisonTask(
                        taskIdx++,
                        N,
                        cvrMean,
                        cvrMeanDiff,
                        eta0Factor = eta0Factor,
                        d = d,
                        cvrs = cvrs
                    )
                )
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/failures/comparison99.csv")

        val runner = ComparisonRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }
}