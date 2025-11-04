@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.concur

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
import org.cryptobiotic.rlauxe.core.RiskTestingFn
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.estimate.runTestRepeated
import org.cryptobiotic.rlauxe.estimate.Sampler
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.util.Stopwatch

interface RepeatedTask {
    fun makeSampler() : Sampler
    fun makeTestFn() : RiskTestingFn
    fun makeTestParameters() : Map<String, Double>
    fun name() : String
    fun N() : Int
    fun reportedMean() : Double
    fun reportedMeanDiff() : Double
}

class RunRepeatedTasks {
    private val showCalculation = false
    private val showTaskResult = false
    private val mutex = Mutex()
    private val calculations = mutableListOf<SRT>()

    // run all the tasks concurrently
    fun run(tasks: List<RepeatedTask>, ntrials: Int, nthreads: Int = 30): List<SRT> {
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
        println("RunRepeatedTasks took ${stopwatch.tookPer(tasks.size, "task")}")
        return calculations
    }

    fun calculate(task: RepeatedTask, ntrials: Int): SRT {
        val stopwatch = Stopwatch()
        val rr = runTask(task, ntrials = ntrials)
        val sr = rr.makeSRT(
            reportedMean = task.reportedMean(),
            reportedMeanDiff = task.reportedMeanDiff(),
        )
        if (showCalculation) println("${task.name()} (${calculations.size}): $sr")
        if (showTaskResult) println("${task.name()} (${calculations.size}): $rr took $stopwatch")
        return sr
    }

    fun runTask(
        task: RepeatedTask,
        ntrials: Int,
        silent: Boolean = false
    ): RunTestRepeatedResult {
        if (!silent) println(" runTask=${task.name()}")

        return runTestRepeated(
            drawSample = task.makeSampler(),
            ntrials = ntrials,
            testFn = task.makeTestFn(),
            testParameters = task.makeTestParameters(),
            Nc=task.N(),
            )
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<RepeatedTask>): ReceiveChannel<RepeatedTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<RepeatedTask>,
        calculate: (RepeatedTask) -> SRT?,
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