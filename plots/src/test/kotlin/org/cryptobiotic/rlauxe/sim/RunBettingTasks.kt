@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.sim

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.yield
import org.cryptobiotic.rlauxe.core.AdaptiveComparison
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.theta2margin

data class BettingTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrs: List<Cvr>,
    val d2: Int, // weight p2, p4
    val p2oracle: Double, // oracle rate of 2-vote overstatements
    val p2prior: Double, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
) {
    // val theta = cvrMean + cvrMeanDiff
    init {
        require( N == cvrs.size)
    }
}

class BettingRunner {
    private val showCalculation = false
    private val showTaskResult = false
    private val mutex = Mutex()
    private val calculations = mutableListOf<SRT>()

    // run all the tasks concurrently
    fun run(tasks: List<BettingTask>, ntrials: Int, nthreads: Int = 30): List<SRT> {
        val stopwatch = Stopwatch()
        println("run ${tasks.size} comparison tasks with $nthreads threads and $ntrials trials")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        println("that took ${stopwatch.tookPer(tasks.size, "task")}")
        return calculations
    }

    fun calculate(task: BettingTask, ntrials: Int): SRT {
        val stopwatch = Stopwatch()
        val rr = runBettingMart(task, ntrials = ntrials)
        val sr = rr.makeSRT(
            N=task.N,
            reportedMean = task.cvrMean,
            reportedMeanDiff = -task.p2oracle, // TODO
        )
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, $sr")
        if (showTaskResult) println("${task.idx} (${calculations.size}): $rr took $stopwatch")
        return sr
    }

    fun runBettingMart(
        task: BettingTask,
        ntrials: Int,
        silent: Boolean = false
    ): BettingMartRepeatedResult {
        if (!silent) println(" task=${task.idx}")

        // generate with the oracle, or true rates
        val compareAssorter = makeStandardComparisonAssorter(task.cvrMean)

        val sampler = ComparisonWithErrorRates(task.cvrs, compareAssorter, p2 = task.p2oracle, withoutReplacement = true)
        val upperBound = compareAssorter.upperBound
        if (!silent) println("runBettingMart: p2=${task.p2oracle} p2prior=${task.p2prior}")

        //     val N: Int,
        //    val withoutReplacement: Boolean = true,
        //    val upperBound: Double,
        //    val a: Double, // noerror
        //    val d1: Int,  // weight p1, p3 // TODO derive from p1-p4 ??
        //    val d2: Int, // weight p2, p4
        //    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; set to 0 to remove consideration
        //    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
        //    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; set to 0 to remove consideration
        //    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; set to 0 to remove consideration
        //    val eps: Double = .00001
        val adaptive = AdaptiveComparison(
            N = task.N,
            withoutReplacement = true,
            upperBound = upperBound,
            a = compareAssorter.noerror,
            d1 = 0,
            d2 = task.d2,
            p1 = 0.0,
            p2 = task.p2prior,
            p3 = 0.0,
            p4 = 0.0,
        )
        val betting =
            BettingMart(bettingFn = adaptive, N = task.N, noerror=compareAssorter.noerror, upperBound = upperBound, withoutReplacement = true)

        return runBettingMartRepeated(
            drawSample = sampler,
            maxSamples = task.N,
            ntrials = ntrials,
            bettingMart = betting,
            testParameters = mapOf("p2oracle" to task.p2oracle, "p2prior" to task.p2prior, "d2" to task.d2.toDouble()),
            showDetails = false,
        )
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<BettingTask>): ReceiveChannel<BettingTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<BettingTask>,
        calculate: (BettingTask) -> SRT?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            if (calculation != null) {
                mutex.withLock {
                    calculations.add(calculation)
                    if (calculations.size % 100 == 0) print(" ${calculations.size}")
                }
            }
            yield()
        }
    }
}