@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.workflow

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

import org.cryptobiotic.rlauxe.sampling.SimulateSampleSizeTask
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit

// TODO: can we generify?

interface EstimationTask {
    fun name() : String
    fun estimate() : EstimationResult
}

data class EstimationResult(
    val task: SimulateSampleSizeTask,
    val repeatedResult: RunTestRepeatedResult,
    val failed: Boolean
)

// runs set of EstimationTask concurrently, which return EstimationResult
// the task itself typically calls runTestRepeated
class EstimationTaskRunner {
    private val showTime = false
    private val showTaskResult = false
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationResult>()

    // run all the tasks concurrently
    fun run(tasks: List<EstimationTask>, nthreads: Int = 30): List<EstimationResult> {
        val stopwatch = Stopwatch()
        if (showTime) println("\nEstimationTaskRunner run ${tasks.size} concurrent tasks with $nthreads threads")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(launchCalculations(taskProducer) { task -> runTask(task) })
            }
            // wait for all tasks to be done
            joinAll(*calcJobs.toTypedArray())
        }

        // doesnt return until all tasks are done
        if (showTime) println("EstimationTaskRunner took $stopwatch")
        // println("that ${stopwatch.tookPer(tasks.size, "task")}")
        return results
    }

    fun runTask(
        task: EstimationTask,
    ): EstimationResult {
        val stopwatch = Stopwatch()
        val result =  task.estimate()
        if (showTaskResult) println(" ${task.name()} (${results.size}): took ${stopwatch.elapsed(TimeUnit.SECONDS)} secs")
        return result
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<EstimationTask>): ReceiveChannel<EstimationTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<EstimationTask>,
        taskRunner: (EstimationTask) -> EstimationResult?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val result = taskRunner(task) // not inside the mutex!!
            if (result != null) {
                mutex.withLock {
                    results.add(result)
                }
            }
            yield()
        }
    }
}