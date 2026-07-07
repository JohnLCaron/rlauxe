package org.cryptobiotic.rlauxe.variance


import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.cli.CreateCaseData
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path

class VarianceDataGenerator(
    val case: String,
    val toptopdir: String,
    val otherParameters: Array<String> = emptyArray(),
    val nruns: Int,
    val nsimTrials: Int,
    val nthreads: Int = 10,  // can we increase this ??
) {

    fun createAndRunTasks() {
        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTask<Boolean>>()
        repeat(nruns) { run ->
            tasks.add( RunVarianceTask(case, toptopdir, otherParameters, nsimTrials = nsimTrials, run+1))
        }

        val estResults = ConcurrentTaskRunner<Boolean>().run(tasks, nthreads = nthreads) // OOM, reduce threads
        println(estResults)

        //$prefix avoid      other  .0001  .001   .01 .05
        // final      0    581,840   7901    42  2556   0 // nrun = 1, nsim = 1,  time= 7min 25sec, nthreads=1
        // final      0 11,610,773  61453     0  5739   0 // nrun = 1, nsim = 20, time= 9min 39sec, nthreads=1 (but the sims are threaded)
        // final      0 11,469,893  61703   149 13911   0 // nrun = 2, nsim = 10, time= 17min 49sec, nthreads=1
        // final      0 17,379,813  81536     0 9808    0 // nrun = 2, nsim = 10, time= 33min 20sec, nthreads=1

        // switch to comparing to prevBet, not maxBet
        //$prefix    avoid      .0001   .001   .01   .05    > .05
        //    final 592387     448956  92769 36000 11576     3086  // nrun = 1, nsim = 1,  time= 5min 53 sec, nthreads=1; <.001 91%
        // decimate 603552     502552  72771 18183  7813     2233; 7 min 19 sec = 439 secs
        // decimate 554191      13451  19250 13318  7361     1955; 2 min 12 sec = 132 secs
        GeneralAdaptiveBetting.showCounts("final")
        println("\n that took $stopwatch")
    }

    class RunVarianceTask(
        val case: String,
        val toptopdir: String,
        val otherParameters: Array<String>,
        val nsimTrials: Int, // wtf ?
        val runIndex: Int,
    ) : ConcurrentTask<Boolean> {
        val toptopdirn = "$toptopdir$runIndex"

        override fun name() = "RunVarianceTask $runIndex"

        override fun run(): Boolean {
            validateOutputDir(Path(toptopdirn))

            CreateCaseData.main(
                arrayOf(
                    "--case", case,
                    "--output", toptopdirn,
                ) + otherParameters
            )

            return runAllRoundsAndVerify(toptopdirn)
        }
    }
}