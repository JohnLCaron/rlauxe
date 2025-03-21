package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.concur.RunRepeatedTasks
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import kotlin.test.Test

// explore comparison parameters
class PollingParameters {

    @Test
    fun pollingAlpha() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val thetas = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)

        val ns = listOf(5000, 10000, 20000)
        val ntrials = 1000

        val tasks = mutableListOf<PollingTask>()
        var taskIdx = 0
        dlist.forEach { d ->
            thetas.forEach { theta ->
                ns.forEach { N ->
                    val cvrs = makeCvrsByExactMean(N, theta)
                    cvrMeanDiffs.forEach { cvrMeanDiff ->
                        val cvrMean = theta - cvrMeanDiff
                        tasks.add(PollingTask(taskIdx++, N, cvrMean, cvrMeanDiff, d = d, cvrs = cvrs))
                    }
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/pollingAlpha.csv")

        val runner = RunRepeatedTasks()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

    @Test
    fun pollingBravo() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val thetas = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)

        val ns = listOf(5000, 10000, 20000)
        val ntrials = 1000

        val tasks = mutableListOf<PollingTask>()
        var taskIdx = 0
        thetas.forEach { theta ->
            ns.forEach { N ->
                val cvrs = makeCvrsByExactMean(N, theta)
                cvrMeanDiffs.forEach { cvrMeanDiff ->
                    val cvrMean = theta - cvrMeanDiff
                    tasks.add(
                        PollingTask(
                            taskIdx++,
                            N,
                            cvrMean,
                            cvrMeanDiff,
                            d = 0,
                            cvrs = cvrs,
                            useFixedEstimFn = true
                        )
                    )
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/pollingBravo.csv")

        val runner = RunRepeatedTasks()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

}