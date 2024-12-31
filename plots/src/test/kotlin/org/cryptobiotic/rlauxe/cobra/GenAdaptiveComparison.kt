package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.comparison.makeCvrsByMargin
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.BettingTask
import org.cryptobiotic.rlauxe.sim.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.test.Test

class GenAdaptiveComparison {

    @Test
    fun genAdaptiveComparison() {
        val stopwatch = Stopwatch()

        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val N = 10000
        val ntrials = 10000
        val p2prior = .001
        val d2 = 100

        val tasks = mutableListOf<BettingTask>()
        var taskCount = 0
        reportedMeans.forEach { reportedMean ->
            p2s.forEach { p2 ->
                // the cvrs get generated with the reportedMeans.
                // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
                val cvrs = makeCvrsByExactMean(N, reportedMean)
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
                        cvrMean = reportedMean,
                        cvrs = cvrs,
                        d2 = d2,
                        p2oracle = p2,
                        p2prior = p2prior,
                    )
                )
            }
        }

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        val dirname = "/home/stormy/temp/p2errors"
        val filename = "plotAdaptiveComparison001"
        val writer = SRTcsvWriter("$dirname/${filename}.csv")
        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "task")} of $ntrials trials each")

        val plotter = PlotCobraDetails(dirname, filename)
        plotter.plotSuccessVsTheta()
        plotter.plotSuccess20VsTheta()
        plotter.plotSuccess20VsThetaNarrow()
        plotter.plotFailuresVsTheta()

    }

    // just do one task
    @Test
    fun genOneAdaptiveComparison() {
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
                val cvrs = makeCvrsByMargin(N, mean2margin(cvrMean))
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

        val writer = SRTcsvWriter("/home/stormy/temp/betting/plotAdaptiveComparison.csv")

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "tasks")} of $ntrials trials each")
    }

    @Test
    fun makeTheta() {
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach{ print("${margin2mean(it)}, " ) }
        println()
    }
}