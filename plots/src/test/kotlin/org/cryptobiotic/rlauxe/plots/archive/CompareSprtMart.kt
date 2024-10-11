@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.plots.archive

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
import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.ArrayAsSampleFn
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.Welford
import org.cryptobiotic.rlauxe.core.makePollingAudit
import org.cryptobiotic.rlauxe.core.randomPermute
import org.cryptobiotic.rlauxe.integration.FixedMean
import org.cryptobiotic.rlauxe.util.cardsPerContest
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.margin2theta
import org.cryptobiotic.rlauxe.util.tabulateVotes
import kotlin.test.Test

import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.assertEquals

// is SprtMart equal to Alpha with fixed mean ?
class CompareSprtMart {

    @Test
    fun testSprtMartConcurrent() {
        val margins =
            listOf(.02, .04, .06, .08, .1, .15, .2, .3, .4) // winning percent: 70, 65, 60, 57.5, 55, 54, 53, 52, 51, 50.5
        val nlist = listOf(1000, 5000, 10000, 20000, 50000)
        val tasks = mutableListOf<CalcTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMean(N, margin2theta(margin))
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
                        compareSprt(task, nrepeat)
                    })
            }

            // wait for all verifications to be done
            joinAll(*calcJobs.toTypedArray())
        }
        val count = calculations.size
        println("did $count tasks ${stopwatch.tookPer(count, "task")}")
    }

    fun compareSprt(task: CalcTask, nrepeat: Int): List<Double> {
        return compareSprtMart(task.margin, task.cvrs, nrepeat = nrepeat, silent = true)
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<CalcTask>): ReceiveChannel<CalcTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private val calculations = mutableListOf<Double>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<CalcTask>,
        calculate: (CalcTask) -> List<Double>,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            mutex.withLock {
                calculations.add(calculation.first())
            }
            yield()
        }
    }

    fun compareSprtMart(
        margin: Double,
        cvrs: List<Cvr>,
        nrepeat: Int,
        silent: Boolean = true
    ): List<Double> {
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

        val results = mutableListOf<Double>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach { assert ->
                if (!silent && showContests) println("  ${assert}")

                val assortValues = cvrs.map { cvr -> assert.assorter.assort(cvr) }
                val assortSum = assortValues.sum()
                val assortMean = assortValues.average()

                val result = runCompareSprtMartRepeated(
                    assortValues = assortValues,
                    reportedRatio = .5 + margin / 2,
                    eta0 = assortSum / N, // use the true value
                    nrepeat = nrepeat,
                    withoutReplacement = true,
                )
                results.add(result)
            }
        }
        return results
    }

    fun runCompareSprtMartRepeated(
        assortValues: List<Double>,
        reportedRatio: Double,
        eta0: Double,
        withoutReplacement: Boolean = true,
        nrepeat: Int = 1,
        showDetail: Boolean = false,
    ): Double {
        val N = assortValues.size
        val upperBound = 1.0

        val sprt = SprtMart(
            N = N,
            eta = eta0,
            upper = upperBound,
            withoutReplacement = withoutReplacement
        )

        // run alpha with fixed mean, to simulate sprtMart == Bravo?
        val estimFn = FixedMean(eta0)
        val alpha = AlphaMart(
            estimFn = estimFn,
            N = N,
            upperBound = upperBound,
            withoutReplacement = withoutReplacement
        )

        val welford = Welford()

        repeat(nrepeat) {
            val permuteValues = randomPermute(assortValues.toDoubleArray())
            val sprtResult: TestH0Result = sprt.testH0(permuteValues)

            val sampleFn = ArrayAsSampleFn(permuteValues)
            val alphaResult: TestH0Result = alpha.testH0(N, terminateOnNullReject=true) { sampleFn.sample() }

            // TestH0Result val status: TestH0Status, val sampleCount: Int, val sampleMean: Double, val pvalues: List<Double>
            assertEquals(sprtResult.status, alphaResult.status)
            assertEquals(sprtResult.sampleCount, alphaResult.sampleCount)
            welford.update((sprtResult.sampleCount - alphaResult.sampleCount).toDouble()/N)
        }

        val (avg, _, _) = welford.result()
        return avg
    }
}
