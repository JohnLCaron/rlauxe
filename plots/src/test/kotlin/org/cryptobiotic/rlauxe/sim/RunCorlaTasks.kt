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
import org.cryptobiotic.rlauxe.core.ComparisonWithErrorRates
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.corla.Corla
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.util.Stopwatch

data class CorlaTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrs: List<Cvr>,
    val riskLimit: Double = 0.05,
    val p2: Double,      // oracle rate of 2-vote overstatements
    val p1: Double,     // oracle rate of 1-vote overstatements
) {
    // val theta = cvrMean + cvrMeanDiff
    init {
        require( N == cvrs.size)
    }
}

class CorlaRunner {
    private val showCalculation = false
    private val showTaskResult = false
    private val mutex = Mutex()
    private val calculations = mutableListOf<SRT>()

    // run all the tasks concurrently
    fun run(tasks: List<CorlaTask>, ntrials: Int, nthreads: Int = 30): List<SRT> {
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

    fun calculate(task: CorlaTask, ntrials: Int): SRT {
        val stopwatch = Stopwatch()
        val rr = runCorla(task, ntrials = ntrials)
        val sr = rr.makeSRT(
            N=task.N,
            reportedMean = task.cvrMean,
            reportedMeanDiff = -task.p2, // TODO
        )
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, $sr")
        if (showTaskResult) println("${task.idx} (${calculations.size}): $rr took $stopwatch")
        return sr
    }

    fun runCorla(
        task: CorlaTask,
        ntrials: Int,
        silent: Boolean = false
    ): BettingMartRepeatedResult {
        if (!silent) println(" task=${task.idx}")

        // generate with the oracle, or true rates
        val compareAssorter = makeStandardComparisonAssorter(task.cvrMean)

        val sampler = ComparisonWithErrorRates(task.cvrs, compareAssorter, p1 = task.p1, p2 = task.p2, withoutReplacement = true)
        val upperBound = compareAssorter.upperBound
        if (!silent) println("runCorla: p1=${task.p1} p2=${task.p2}")

        val corla = Corla(N = task.N, riskLimit=task.riskLimit, reportedMargin=compareAssorter.margin, noerror=compareAssorter.noerror,
            p1 = task.p1, p2 = task.p2, p3 = 0.0, p4 = 0.0)

        return runCorlaRepeated(
            drawSample = sampler,
            maxSamples = task.N,
            ntrials = ntrials,
            corla = corla,
            testParameters = mapOf("p1" to task.p1, "p2oracle" to task.p2),
            showDetails = false,
        )
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<CorlaTask>): ReceiveChannel<CorlaTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<CorlaTask>,
        calculate: (CorlaTask) -> SRT?,
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