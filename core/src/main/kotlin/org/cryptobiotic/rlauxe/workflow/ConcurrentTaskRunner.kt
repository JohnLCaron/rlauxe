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
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit

// assumes that the task returns RunTestRepeatedResult
// TODO: other kinds of tasks that return maybe TestH0Status ?

interface ConcurrentTask {
    fun name() : String
    fun run() : RunTestRepeatedResult
}

class ConcurrentTaskRunner {
    private val show = true
    private val showTaskResult = false
    private val mutex = Mutex()
    private val results = mutableListOf<RunTestRepeatedResult>()

    // run all the tasks concurrently
    fun run(tasks: List<ConcurrentTask>, nthreads: Int = 30): List<RunTestRepeatedResult> {
        val stopwatch = Stopwatch()
        if (show) println("\nConcurrentTaskRunner run ${tasks.size} concurrent tasks with $nthreads threads")
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
        if (show) println("that took $stopwatch")
        // println("that ${stopwatch.tookPer(tasks.size, "task")}")
        return results
    }

    fun runTask(
        task: ConcurrentTask,
    ): RunTestRepeatedResult {
        val stopwatch = Stopwatch()
        val result =  task.run()
        if (showTaskResult) println("${task.name()} (${results.size}): ${stopwatch.elapsed(TimeUnit.SECONDS)}")
        return result
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<ConcurrentTask>): ReceiveChannel<ConcurrentTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<ConcurrentTask>,
        taskRunner: (ConcurrentTask) -> RunTestRepeatedResult?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val result = taskRunner(task) // not inside the mutex!!
            if (result != null) {
                mutex.withLock {
                    results.add(result)
                    if (results.size % 100 == 0) print(" ${results.size}")
                }
            }
            yield()
        }
    }
}