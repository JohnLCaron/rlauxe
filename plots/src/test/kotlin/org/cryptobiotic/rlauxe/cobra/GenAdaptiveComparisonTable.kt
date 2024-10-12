package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.BettingRunner
import org.cryptobiotic.rlauxe.sim.BettingTask
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.makeCvrsByMargin
import org.cryptobiotic.rlauxe.util.margin2theta
import org.cryptobiotic.rlauxe.util.theta2margin
import org.junit.jupiter.api.Test

class GenAdaptiveComparisonTable {

    @Test
    fun plotAdaptiveComparison() {
        val stopwatch = Stopwatch()

        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val N = 10000
        val ntrials = 100
        val p2prior = .001
        val d2 = (1.0/p2prior).toInt()/10

        val tasks = mutableListOf<BettingTask>()
        var taskCount = 0
        reportedMeans.forEach { mean ->
            p2s.forEach { p2 ->
                // the cvrs get generated with this exact margin.
                // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
                val cvrs = makeCvrsByExactMean(N, mean)
                tasks.add(
                    //     val N: Int,
                    //    val cvrMean: Double,
                    //    val cvrs: List<Cvr>,
                    //    val d2: Int, // weight p2, p4
                    //    val p2oracle: Double = 1.0e-4, // oracle rate of 2-vote overstatements
                    //    val p2prior: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
                    BettingTask(
                        idx=taskCount++,
                        N=N,
                        cvrMean = mean,
                        cvrs = cvrs,
                        d2 = d2,
                        p2oracle = p2,
                        p2prior = p2prior,
                    )
                )
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/bet/plotAdaptiveComparison.csv")

        val runner = BettingRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "task")} of $ntrials trials each")
    }

    // just do one repeated task
    @Test
    fun testOneAdaptiveComparison() {
        val stopwatch = Stopwatch()

        val p2s = listOf(.001) //, .005, .01)
        val cvrMeans = listOf(.55) // .503, .504, .505, .506, .508, .51, .52, .53, .54, .55)

        val N = 10000
        val ntrials = 100
        val p2prior = .001

        val tasks = mutableListOf<BettingTask>()
        var taskCount = 0
        cvrMeans.forEach { cvrMean ->
            p2s.forEach { p2 ->
                // the cvrs get generated with this exact margin.
                // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
                val cvrs = makeCvrsByMargin(N, theta2margin(cvrMean))
                tasks.add(
                    //     val N: Int,
                    //    val cvrMean: Double,
                    //    val cvrs: List<Cvr>,
                    //    val d2: Int, // weight p2, p4
                    //    val p2oracle: Double = 1.0e-4, // oracle rate of 2-vote overstatements
                    //    val p2prior: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
                    BettingTask(
                        idx=taskCount++,
                        N=N,
                        cvrMean = cvrMean,
                        cvrs = cvrs,
                        d2 = (1.0/p2prior).toInt(),
                        p2oracle = p2,
                        p2prior = p2prior,
                    )
                )
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/bet/plotAdaptiveComparison.csv")

        val runner = BettingRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "tasks")} of $ntrials trials each")
    }

    @Test
    fun makeTheta() {
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach{ print("${margin2theta(it)}, " ) }
        println()
    }
}