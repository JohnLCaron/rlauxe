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
import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.EstimFn
import org.cryptobiotic.rlauxe.core.FixedEstimFn
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.util.SRT

data class PollingTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrMeanDiff: Double,
    val d: Int, // parameter for shrinkTruncate
    val cvrs: List<Cvr>
) {
    val theta = cvrMean + cvrMeanDiff
    init {
        require(N == cvrs.size)
    }
}

class PollingRunner(val useFixedEstimFn: Boolean = false) {
    private val showCalculation = false
    private val showCalculationAll = false
    private val mutex = Mutex()
    private val calculations = mutableListOf<SRT>()

    // run all the tasks concurrently
    fun run(tasks: List<PollingTask>, ntrials: Int, nthreads: Int = 30): List<SRT> {
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
        return calculations
    }

    fun calculate(task: PollingTask, ntrials: Int): SRT {
        val rr = runPollingWithErrors(task, nrepeat = ntrials)
        val sr = makeSRT(
            task.N,
            reportedMean = task.cvrMean,
            reportedMeanDiff = task.cvrMeanDiff,
            d = task.d,
            rr = rr
        )
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, ${rr.eta0}, $sr")
        if (showCalculationAll) println("${task.idx} (${calculations.size}): $rr")
        return sr
    }

    fun runPollingWithErrors(
        task: PollingTask,
        nrepeat: Int,
        silent: Boolean = true
    ): AlphaMartRepeatedResult {
        if (!silent) println(" N=${task.N} theta=${task.theta} d=${task.d} cvrMeanDiff=${task.cvrMeanDiff}")

        val pollingAssorter = makeStandardPluralityAssorter()
        val sampler = PollWithoutReplacement(task.cvrs, pollingAssorter)

        val pollingResult = if (useFixedEstimFn) {
            val alpha = AlphaMart(estimFn = FixedEstimFn(task.cvrMean), N = task.N)
            runAlphaMartRepeated(
                drawSample = sampler,
                maxSamples = task.N,
                ntrials = nrepeat,
                alphaMart = alpha,
                eta0 = task.cvrMean,
            )
        } else {
            runAlphaMartRepeated(
                drawSample = sampler,
                maxSamples = task.N,
                eta0 = task.cvrMean,
                d = task.d,
                ntrials = nrepeat,
                withoutReplacement = true,
                upperBound = pollingAssorter.upperBound(),
            )
        }
        return pollingResult
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<PollingTask>): ReceiveChannel<PollingTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<PollingTask>,
        calculate: (PollingTask) -> SRT?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            if (calculation != null) {
                mutex.withLock {
                    calculations.add(calculation)
                }
            }
            yield()
        }
    }
}