@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
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
import java.util.concurrent.TimeUnit


interface ConcurrentTask<T> {
    fun name() : String
    fun run() : T
}

// runs a set of ConcurrentTask<T> concurrently, each task.run() returns T.
class ConcurrentTaskRunner<T>(val show: Boolean = false, val showTaskResult: Boolean = false) {
    private val mutex = Mutex()
    private val results = mutableListOf<T>()

    // run all the tasks concurrently
    fun run(tasks: List<ConcurrentTask<T>>, nthreads: Int? = null): List<T> {
        val stopwatch = Stopwatch()
        val useThreads = nthreads ?: 30
        logger.debug{"ConcurrentTaskRunner run ${tasks.size} concurrent tasks with $nthreads threads"}
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(useThreads) {
                calcJobs.add(launchCalculations(taskProducer) { task -> runTask(task) })
            }
            // wait for all tasks to be done
            joinAll(*calcJobs.toTypedArray())
        }

        // doesnt return until all tasks are done
        logger.debug{"took $stopwatch"}
        return results
    }

    fun runTask(
        task: ConcurrentTask<T>,
    ): T {
        val stopwatch = Stopwatch()
        val result = task.run()
        if (showTaskResult) println("${task.name()} (${results.size}): ${stopwatch.elapsed(TimeUnit.SECONDS)}")
        return result
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<ConcurrentTask<T>>): ReceiveChannel<ConcurrentTask<T>> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<ConcurrentTask<T>>,
        taskRunner: (ConcurrentTask<T>) -> T?,
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

    companion object {
        private val logger = KotlinLogging.logger("ConcurrentTaskRunner")
    }
}