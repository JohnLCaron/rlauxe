package org.cryptobiotic.rlauxe.sampling

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
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType

fun main() {
    val test = MultiContestTestData(15, 1, 20000)
    val cvrs = test.makeCvrsFromContests()
    val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit( it ).makeComparisonAssertions(cvrs) }
    val nassertions = contestsUA.sumOf { it.assertions().size }
    println("ncontests=${contestsUA.size} nassertions=${nassertions} ncvrs=${cvrs.size}")

    val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles = true, seed = 1234567890L, fuzzPct = null, ntrials = 10)
    val tasks = mutableListOf<EstimationTask>()
    contestsUA.filter { !it.done }.forEach { contestUA ->
        tasks.addAll(makeEstimationTasks(auditConfig, contestUA, cvrs, emptyList(), 1))
    }

    val one = runWest(1, tasks, 0.0).toDouble()
    runWest(2, tasks, one)
    runWest(4, tasks, one)
    runWest(6, tasks, one)
    runWest(8, tasks, one)
    runWest(10, tasks, one)
    runWest(12, tasks, one)
    runWest(14, tasks, one)
    runWest(16, tasks, one)
}

fun runWest(nthreads: Int, tasks: List<EstimationTask>, one: Double): Long {
    val runner = EstimationWTaskRunner()
    return runner.calc(nthreads, tasks, one)
}

class EstimationWTaskRunner() {
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationResult>()

    fun calc(nthreads: Int, tasks: List<EstimationTask>, one: Double): Long {
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
    private fun CoroutineScope.producer(producer: Iterable<EstimationTask>): ReceiveChannel<EstimationTask> =
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