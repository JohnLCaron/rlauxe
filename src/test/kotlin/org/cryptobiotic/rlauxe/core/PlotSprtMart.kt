@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.core

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
import kotlin.test.Test

import org.cryptobiotic.rlauxe.util.Stopwatch

class PlotSprtMart {

    @Test
    fun testSprtMartConcurrent() {
        val margins = listOf(.01, .02, .04, .06, .08, .1, .15, .2, .3, .4) // winning percent: 70, 65, 60, 57.5, 55, 54, 53, 52, 51, 50.5
        val nlist = listOf(1000, 5000, 10000, 20000, 50000)
        val tasks = mutableListOf<CalcTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMargin(N, margin)
                tasks.add(CalcTask(taskIdx++, N, margin, cvrs))
            }
        }

        val stopwatch = Stopwatch()
        val nthreads = 20
        val nrepeat = 100

        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, nrepeat)
                    })
            }

            // wait for all verifications to be done
            joinAll(*calcJobs.toTypedArray())
        }
        val count = calculations.size
        println("did $count tasks ${stopwatch.tookPer(count, "task")}")
        plotSRSnVt(calculations, margins, nlist)
        plotStddevSnVt(calculations, margins, nlist)
        plotSuccesses(calculations, margins, nlist, 10, nrepeat)
        plotSuccesses(calculations, margins, nlist, 20, nrepeat)
        plotSuccesses(calculations, margins, nlist, 30, nrepeat)
    }

    fun calculate(task: CalcTask, nrepeat: Int): SR {
        val rr = testSprtMart(task.margin, task.cvrs, nrepeat = nrepeat, silent = true).first()
        val sr = makeSR(task.N, task.margin, rr)
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.margin}, ${rr.eta0}, $sr")
        return sr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<CalcTask>): ReceiveChannel<CalcTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private val calculations = mutableListOf<SR>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<CalcTask>,
        calculate: (CalcTask) -> SR,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            mutex.withLock {
                calculations.add(calculation)
            }
            yield()
        }
    }
}

fun testSprtMart(margin: Double, cvrs: List<Cvr>, nrepeat: Int, silent: Boolean = true): List<AlphaMartRepeatedResult> {
    val N = cvrs.size
    if (!silent) println(" N=${cvrs.size} margin=$margin withoutReplacement")

    // count actual votes
    val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
    if (!silent && showContests) {
        votes.forEach { key, cands ->
            println("contest ${key} ")
            cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
        }
    }

    // make contests from cvrs
    val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
    if (!silent && showContests) {
        println("Contests")
        contests.forEach { println("  ${it}") }
    }

    // Polling Audit
    val audit = makePollingAudit(contests = contests)

    val results = mutableListOf<AlphaMartRepeatedResult>()
    audit.assertions.map { (contest, assertions) ->
        if (!silent && showContests) println("Assertions for Contest ${contest.id}")
        assertions.forEach { assert ->
            if (!silent && showContests) println("  ${assert}")

            val assortValues = cvrs.map { cvr -> assert.assorter.assort(cvr) }.toDoubleArray()
            val assortSum = assortValues.sum()

            val result = runSprtMartRepeated(
                assortValues = assortValues,
                theta = margin2theta(margin),
                eta0 = assortSum/N, // use the true value
                nrepeat = nrepeat,
                withoutReplacement = true,
            )
            if (!silent) {
                println(result)
                println("truePopulationCount=${ff.format(assortSum)} truePopulationMean=${ff.format(assortSum/N)} failPct=${result.failPct} status=${result.status}")
            }
            results.add(result)
        }
    }
    return results
}

fun runSprtMartRepeated(
    assortValues: DoubleArray,
    theta: Double,
    eta0: Double,
    withoutReplacement: Boolean = true,
    nrepeat: Int = 1,
    showDetail: Boolean = false,
): AlphaMartRepeatedResult {
    val N = assortValues.size
    val upperBound = 1.0

    val sprt = SprtMart(
        N = N,
        eta = eta0,
        upper = upperBound,
        withoutReplacement = withoutReplacement
    )

    var sampleMeanSum = 0.0
    var fail = 0
    var nsuccess = 0
    val hist = Histogram(10) // bins of 10%
    val status = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()

    repeat(nrepeat) {
        val testH0Result = sprt.testH0( randomPermute(assortValues) )
        val currCount = status.getOrPut(testH0Result.status) { 0 }
        status[testH0Result.status] = currCount + 1
        sampleMeanSum += testH0Result.sampleMean
        if (testH0Result.status.fail) {
            fail++
        } else {
            nsuccess++
        }
        welford.update(testH0Result.sampleCount.toDouble())
        if (!testH0Result.status.fail) {
            val percent = ceilDiv(100 * testH0Result.sampleCount, N) // percent, rounded up
            hist.add(percent)
        }
        if (showDetail) println(" $it $testH0Result")
    }

    val failAvg = fail.toDouble() / nrepeat
    val sampleMeanAvg = sampleMeanSum / nrepeat
    return AlphaMartRepeatedResult(eta0=eta0, N=N, theta=theta, sampleMean=sampleMeanAvg, nrepeat, welford, failAvg, hist, status)
}
