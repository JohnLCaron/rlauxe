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

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit

// TODO: can we genericify?

interface EstimationTask {
    fun name() : String
    fun estimate() : EstimationResult
}

data class EstimationResult(
    val contestUA: ContestUnderAudit,
    val assertion: Assertion,
    val success: Boolean,
    val nsuccess: Int,
    val totalSamplesNeeded: Int,
    val task: EstimationTask
)

class EstimationTaskRunner {
    private val show = true
    private val showTaskResult = true
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationResult>()

    // run all the tasks concurrently
    fun run(tasks: List<EstimationTask>, nthreads: Int = 30): List<EstimationResult> {
        val stopwatch = Stopwatch()
        if (show) println("\nEstimationTaskRunner run ${tasks.size} concurrent tasks with $nthreads threads")
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
        if (show) println("EstimationTaskRunner took $stopwatch")
        // println("that ${stopwatch.tookPer(tasks.size, "task")}")
        return results
    }

    fun runTask(
        task: EstimationTask,
    ): EstimationResult {
        val stopwatch = Stopwatch()
        val result =  task.estimate()
        if (showTaskResult) println(" ${task.name()} (${results.size}): ${stopwatch.elapsed(TimeUnit.SECONDS)}")
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