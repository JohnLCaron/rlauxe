package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// TODO replace ConcurrentTaskRunner
class ConcurrentTaskRunnerWithFlows<T>(
    val show: Boolean = false,
    val showTaskResult: Boolean = false,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default // Default for production
) {
    // This is now a clean, idiomatic suspend function
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run(tasks: List<ConcurrentTask<T>>, nthreads: Int? = null): List<T> {
        val stopwatch = Stopwatch()
        val useThreads = nthreads ?: 30
        logger.debug { "ConcurrentTaskRunner run ${tasks.size} concurrent tasks with $useThreads concurrency limit" }

        // Thread-safe atomic counter just for your log sizing metric
        val completedCount = AtomicInteger(0)

        val results = tasks.asFlow()
            // 1. Move processing to the worker pool
            .flowOn(dispatcher)
            // 2. Concurrently map tasks to their results up to your 'useThreads' limit
            .flatMapMerge(concurrency = useThreads) { task ->
                flow {
                    val result = runTask(task, completedCount.incrementAndGet())
                    emit(result)
                }
            }
            // 3. Collect everything safely back into a standard List
            .toList()

        logger.debug { "took $stopwatch" }
        return results
    }

    fun runTask(task: ConcurrentTask<T>, currentSize: Int): T {
        val stopwatch = Stopwatch()
        logger.debug { "start task ${task.name()}" }

        val result = task.run()

        logger.debug { "finish task ${task.name()} ($currentSize): ${stopwatch.elapsed(TimeUnit.SECONDS)}" }
        return result
    }

    companion object {
        private val logger = KotlinLogging.logger("ConcurrentTaskRunnerWithFlows")
    }
}
