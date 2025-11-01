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
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.EstimationResult
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeEstimationTasks
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class MeasureEstimationTaskConcurrency {
    @Test
    fun measure() {
        val test = MultiContestTestData(15, 1, 20000)
        val cvrs = test.makeCvrsFromContests()
        val contestsUA  = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val nassertions = contestsUA.sumOf { it.assertions().size }
        println("ncontests=${contestsUA.size} nassertions=${nassertions} ncvrs=${cvrs.size}")
        val contestRounds = contestsUA.map{ contest -> ContestRound(contest, 1) }

        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles = true, nsimEst = 10)
        val tasks = mutableListOf<ConcurrentTaskG<EstimationResult>>()

        contestRounds.filter { !it.done }.forEach { contest ->
            tasks.addAll(makeEstimationTasks(auditConfig, contest, 1, cvrIterator = null))
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

    fun runWest(nthreads: Int, tasks: List<ConcurrentTaskG<EstimationResult>>, one: Double): Long {
        val runner = EstimationWTaskRunner()
        return runner.calc(nthreads, tasks, one)
    }
}

class EstimationWTaskRunner() {
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationResult>()

    fun calc(nthreads: Int, tasks: List<ConcurrentTaskG<EstimationResult>>, one: Double): Long {
        val stopWatch = Stopwatch()
        runBlocking {
            val jobs = mutableListOf<Job>()
            val producer = producer(tasks)
            repeat(nthreads) {
                jobs.add(launchCalculations(producer) { task -> task.run() })
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
    private fun CoroutineScope.producer(producer: Iterable<ConcurrentTaskG<EstimationResult>>): ReceiveChannel<ConcurrentTaskG<EstimationResult>> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<ConcurrentTaskG<EstimationResult>>,
        taskRunner: (ConcurrentTaskG<EstimationResult>) -> EstimationResult?,
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