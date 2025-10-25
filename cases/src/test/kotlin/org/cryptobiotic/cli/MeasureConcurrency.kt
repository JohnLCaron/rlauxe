package org.cryptobiotic.cli

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
import java.math.BigInteger
import kotlin.random.Random

/////////////////////////////////////////////////

// run as main to make sure test framework not interfering
fun main() {
    val wtf = Dispatchers.Default
    println("CoroutineDispatcher: ${wtf}") // corePoolSize = 48. could use reflection to get at it
    val one = runVest(1, 100, 0.0).toDouble()
    runVest(2, 100, one)
    runVest(4, 100, one)
    runVest(6, 100, one)
    runVest(8, 100, one)
    runVest(12, 100, one)
    runVest(16, 100, one)
    runVest(20, 100, one)
    runVest(24, 100, one)
    runVest(28, 100, one)
}

fun runVest(nthreads: Int, ntasks: Int, one: Double): Long {
    val tasks: List<EstimationVTask> = List(ntasks) { EstimationVTask() }
    val runner = EstimationVTaskRunner()
    return runner.calc(nthreads, tasks, one)
}

class EstimationVTaskRunner() {
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationVResult>()

    fun calc(nthreads: Int, tasks: List<EstimationVTask>, one: Double): Long {
        val stopWatch = Stopwatch()
        runBlocking {
            val jobs = mutableListOf<Job>()
            val producer = producer(tasks)
            repeat(nthreads) {
                jobs.add(launchCalculations(producer) { task -> task.estimate() })
            }
            // wait for all calculations to be done, then close everything
            joinAll(*jobs.toTypedArray())
        }
        print("$nthreads ${stopWatch.tookPer(tasks.size, "tasks")}")
        val elapsed =  stopWatch.elapsed()
        println(" speedup = ${one/elapsed} scale = ${one/elapsed/nthreads}")
        return elapsed
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.producer(producer: Iterable<EstimationVTask>): ReceiveChannel<EstimationVTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<EstimationVTask>,
        taskRunner: (EstimationVTask) -> EstimationVResult?,
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

data class EstimationVResult(val sum: BigInteger)

class EstimationVTask() {
    fun estimate(): EstimationVResult {
        var sum = BigInteger.ONE
        val bytes = ByteArray(1000)
        Random.nextBytes(bytes)
        val q = BigInteger(1, bytes)
        Random.nextBytes(bytes)
        val p = BigInteger(1, bytes)
        repeat(1000) {
            sum = (sum * p).mod(q)
        }
        return EstimationVResult(sum)
    }
}