package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.sim.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class GenCorlaSRT {

    @Test
    fun genCorlaSrtData() {
        val stopwatch = Stopwatch()

        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val N = 10000
        val ntrials = 1000

        val tasks = mutableListOf<CorlaTask>()
        var taskCount = 0
        reportedMeans.forEach { mean ->
            p2s.forEach { p2 ->
                // the cvrs get generated with this exact margin.
                // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
                val cvrs = makeCvrsByExactMean(N, mean)
                tasks.add(
                    CorlaTask(
                        idx=taskCount++,
                        N=N,
                        cvrMean = mean,
                        cvrs = cvrs,
                        p1 = 0.0,
                        p2 = p2,
                    )
                )
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/corla/plotCorla${ntrials}.csv")

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename} took ${stopwatch.tookPer(taskCount, "task")} of $ntrials trials each")
    }

}