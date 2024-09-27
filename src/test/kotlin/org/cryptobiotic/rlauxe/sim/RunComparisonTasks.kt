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
import org.cryptobiotic.rlauxe.core.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.comparisonAssorterCalc
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.util.SRT
import org.cryptobiotic.rlauxe.sim.makeSRT

data class ComparisonTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrMeanDiff: Double,
    val eta0Factor: Double,
    val d: Int, // parameter for shrinkTruncate
    val cvrs: List<Cvr>
) {
    val theta = cvrMean + cvrMeanDiff
    init {
        require( N == cvrs.size)
    }
}

class ComparisonRunner {
    private val showCalculation = false
    private val showCalculationAll = false
    private val mutex = Mutex()
    private val calculations = mutableListOf<SRT>()

    // run all the tasks concurrently
    fun run(tasks: List<ComparisonTask>, ntrials: Int, nthreads: Int = 30): List<SRT> {
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

    fun calculate(task: ComparisonTask, ntrials: Int): SRT {
        val rr = runComparisonWithErrors(task, nrepeat = ntrials)
        val sr = makeSRT(
            task.N,
            reportedMean = task.cvrMean,
            reportedMeanDiff = task.cvrMeanDiff,
            d = task.d,
            eta0Factor = task.eta0Factor,
            rr = rr
        )
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, ${rr.eta0}, $sr")
        if (showCalculationAll) println("${task.idx} (${calculations.size}): $rr")
        return sr
    }

    fun runComparisonWithErrors(
        task: ComparisonTask,
        nrepeat: Int,
        silent: Boolean = true
    ): AlphaMartRepeatedResult {
        if (!silent) println(" N=${task.N} theta=${task.theta} d=${task.d} cvrMeanDiff=${task.cvrMeanDiff}")

        val compareAssorter = makeStandardComparisonAssorter(task.cvrMean)
        val (_, noerrors, upperBound) = comparisonAssorterCalc(task.cvrMean, compareAssorter.upperBound)
        val sampleWithErrors = ComparisonWithErrors(task.cvrs, compareAssorter, task.theta)

        val compareResult = runAlphaMartRepeated(
            drawSample = sampleWithErrors,
            maxSamples = task.N,
            eta0 = task.eta0Factor * noerrors,
            d = task.d,
            ntrials = nrepeat,
            withoutReplacement = true,
            upperBound = upperBound
        )
        return compareResult
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<ComparisonTask>): ReceiveChannel<ComparisonTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<ComparisonTask>,
        calculate: (ComparisonTask) -> SRT?,
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