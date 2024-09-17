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
import org.cryptobiotic.rlauxe.core.SampleFnFromArray
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.Welford
import org.cryptobiotic.rlauxe.core.makePollingAudit
import org.cryptobiotic.rlauxe.core.randomPermute
import org.cryptobiotic.rlauxe.integration.FixedMean
import org.cryptobiotic.rlauxe.core.cardsPerContest
import org.cryptobiotic.rlauxe.shangrla.eps
import org.cryptobiotic.rlauxe.core.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.margin2theta
import org.cryptobiotic.rlauxe.core.tabulateVotes
import kotlin.test.Test

import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.math.max

// compare Alpha to Bravo (Alpha with fixed mean)
class PlotAlphaBravo {

    @Test
    fun testAlphaBravoConcurrent() {
        val margins = listOf(.01, .02, .04, .06, .08, .1, .15, .2, .3, .4)
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

        val dl = listOf(10, 100, 500)
        val reportedMeanDiffs = listOf(0.0, 0.005, 0.01, 0.02, 0.05, 0.1)   // % greater than actual mean

        reportedMeanDiffs.forEach { reportedMeanDiff ->
            dl.forEach { d ->
                calculations.clear()

                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                compareAlphaBravo(
                                    task.margin,
                                    task.cvrs,
                                    nrepeat = nrepeat,
                                    reportedMeanDiff = reportedMeanDiff,
                                    d = d
                                )
                            })
                    }

                    // wait for all verifications to be done
                    joinAll(*calcJobs.toTypedArray())
                }

                plotSamplePctnVt(
                    calculations,
                    margins,
                    nlist,
                    ": diff bravo - alpha; reportedMeanDiff = $reportedMeanDiff, d = $d"
                )
            }
        }
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
        calculate: (CalcTask) -> List<SR>,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            mutex.withLock {
                calculations.add(calculation.first())
            }
            yield()
        }
    }

    fun compareAlphaBravo(
        margin: Double,
        cvrs: List<Cvr>,
        nrepeat: Int,
        reportedMeanDiff: Double,
        d: Int,
        silent: Boolean = true
    ): List<SR> {
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

        val results = mutableListOf<SR>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach { assert ->
                if (!silent && showContests) println("  ${assert}")

                // the cvrs have exactly the right number of votes for the stated margin
                // but the "reported mean is going to now diverge
                val assortValues = cvrs.map { cvr -> assert.assorter.assort(cvr) }
                val assortMean = assortValues.average()
                val reportedMean = assortMean + reportedMeanDiff // reportedMean != true mean

                val result = runCompareAlphaBravoRepeated(
                    assortValues = assortValues,
                    eta0 = reportedMean,       // use the reportedMean for the initial guess
                    withoutReplacement = true,
                    margin = margin,
                    nrepeat = nrepeat,
                    d = d,
                )
                results.add(result)
            }
        }
        return results
    }

    fun runCompareAlphaBravoRepeated(
        assortValues: List<Double>,
        eta0: Double,
        margin: Double,
        withoutReplacement: Boolean = true,
        nrepeat: Int = 1,
        d: Int = 500,
    ): SR {
        val N = assortValues.size
        val upperBound = 1.0
        val t = 0.5
        val minsd = 1.0e-6
        val c = max(eps, ((eta0 - t) / 2))
        val d: Int = d

        val alpha = AlphaMart(
            estimFn = TruncShrinkage(N, true, upperBound = upperBound, minsd = minsd, d = d, eta0 = eta0, c = c),
            N = N,
            upperBound = upperBound,
            withoutReplacement = true
        )

        // run alpha with fixed mean, which is the same as Bravo
        val bravo = AlphaMart(
            estimFn = FixedMean(eta0),
            N = N,
            upperBound = upperBound,
            withoutReplacement = true
        )

        val welford = Welford()

        repeat(nrepeat) {
            // each repetition gets different permutation
            val permuteValues = randomPermute(assortValues.toDoubleArray())
            val sampleFn = SampleFnFromArray(permuteValues)

            // both alpha and bravo get the exact same sample
            val alphaResult: TestH0Result = alpha.testH0(N, terminateOnNullReject=true) { sampleFn.sample() }
            sampleFn.reset()
            val bravoResult: TestH0Result = bravo.testH0(N, terminateOnNullReject=true) { sampleFn.sample() }
            // check success ??

            welford.update((bravoResult.sampleCount - alphaResult.sampleCount).toDouble())
        }

        val (nsamplesDiff, stddevDiff, _) = welford.result()
        return SR(N, margin, nsamplesDiff, 100.0 * nsamplesDiff / N, stddevDiff, null)
    }
}
