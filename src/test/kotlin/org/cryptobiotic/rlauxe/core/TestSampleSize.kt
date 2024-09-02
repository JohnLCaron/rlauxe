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
import kotlin.math.sqrt

class TestSampleSize {

    val showCalculation = true
    val showContests = false

    fun showSRSnVt(srs: List<SR>, margins: List<Double>) {
        println()
        println("nvotes sampled vs theta = winning percent")
        print("     N, ")
        val theta = margins.sorted().map { .5 + it * .5 }
        theta.forEach { print("${"%6.3f".format(it)}, ") }
        println()

        val mmap = mutableMapOf<Int, MutableMap<Double, Int>>() // N, m -> pct
        srs.forEach {
            val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
            dmap[it.margin] = it.nsamples.toInt()
        }

        mmap.toSortedMap().forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, nmap ->
                print("${"%6d".format(nmap)}, ")
            }
            println()
        }
    }

    fun showStddevSnVt(srs: List<SR>, margins: List<Double>) {
        println()
        println("stddev sampled vs theta = winning percent")
        print("     N, ")
        val theta = margins.sorted().map { .5 + it * .5 }
        theta.forEach { print("${"%6.3f".format(it)}, ") }
        println()

        val mmap = mutableMapOf<Int, MutableMap<Double, Double>>() // N, m -> pct
        srs.forEach {
            val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
            dmap[it.margin] = it.stddev
        }

        mmap.toSortedMap().forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, nmap ->
                print("${"%6.3f".format(nmap)}, ")
            }
            println()
        }
    }

    data class CalcTask(val idx: Int, val N: Int, val margin: Double, val cvrs: List<Cvr>)
    data class SR(val N: Int, val margin: Double, val nsamples: Double, val pct: Double, val stddev: Double)

    fun makeSR(N: Int, margin:Double, rr: RepeatedResult): SR {
        val (sampleCountAvg, sampleCountVar, _)  = rr.sampleCount.result()
        val pct = (100.0 * sampleCountAvg / N)
        return SR(N, margin, sampleCountAvg, pct, sqrt(sampleCountVar))
    }

    @Test
    fun testSampleSizeConcurrent() {
        val margins = listOf(.02, .04, .06, .08, .1, .15, .2, .3, .4) // winning percent: 70, 65, 60, 57.5, 55, 54, 53, 52, 51, 50.5
        val Nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<CalcTask>()

        var taskIdx = 0
        Nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMargin(N, margin)
                tasks.add(CalcTask(taskIdx++, N, margin, cvrs))
            }
        }

        val stopwatch = Stopwatch()
        val nthreads = 20

        runBlocking {
            val taskProducer = produceTasks(tasks)
            val verifierJobs = mutableListOf<Job>()
            repeat(nthreads) {
                verifierJobs.add(
                    launchVerifier(taskProducer) {
                        task -> calculate(task)
                    })
            }

            // wait for all verifications to be done
            joinAll(*verifierJobs.toTypedArray())
        }
        val count = calculations.size
        println( "did $count tasks ${stopwatch.tookPer(count, "task")}")
        showSRSnVt(calculations, margins)
        showStddevSnVt(calculations, margins)
    }

    fun calculate(task: CalcTask): SR {
        val rr = testSampleSize(task.margin, task.cvrs, silent = true).first()
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

    private fun CoroutineScope.launchVerifier(
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

    fun testSampleSize(margin: Double, cvrs: List<Cvr>, silent: Boolean = true): List<RepeatedResult> {
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
        val audit = PollingAudit(auditType = AuditType.POLLING, contests = contests)

        val results = mutableListOf<RepeatedResult>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach {
                if (!silent && showContests) println("  ${it}")

                val cvrSampler = PollWithoutReplacement(cvrs, it.assorter)
                val result = runAlphaMart(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    genRatio = .5 + margin / 2,
                    d = 1000,
                    nrepeat = 10,
                    withoutReplacement = true,
                )
                if (!silent) println(result)
                results.add(result)
            }
        }
        return results
    }

    /* single threaded
    @Test
    fun testSampleSize() {
        val margins = listOf(.4, .3, .2, .15, .1, .08, .06, .04, .02, .01) // winning percent: 70, 65, 60, 57.5, 55, 54, 53, 52, 51, 50.5
        val Nlist = listOf(1000, 5000, 10000, 20000, 50000, 100000)
        val srs = mutableListOf<SR>()
        val show = false

        if (show) println("N, margin, eta0, nSample, pctVotes")

        Nlist.forEach { N ->
            margins.forEach { margin ->
                val cvrs = makeCvrsByExactMargin(N, margin)
                val resultWithout = testSampleSize(margin, cvrs, silent = true).first()

                val pct = (100.0 * resultWithout.sampleCountAvg / N)
                if (show) println("${cvrs.size}, $margin, ${resultWithout.eta0}, ${resultWithout.sampleCountAvg}, ${pct}")
                srs.add(SR(N, margin, resultWithout.sampleCountAvg, pct))
            }
        }
        showSRSnVt(srs, margins)
        // showSRSbyNBallots(srs, Nlist, margins)
    }

     */
}

// 9/01/2024
// compares well with table 3 of ALPHA
// eta0 = theta, no divergence of sample from true. 1000 repetitions
//
// nvotes sampled vs theta = winning percent
//     N,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
//  1000,    897,    726,    569,    446,    340,    201,    128,     60,     36,
//  5000,   3447,   1948,   1223,    799,    527,    256,    145,     68,     38,
// 10000,   5665,   2737,   1430,    871,    549,    266,    152,     68,     38,
// 20000,   8456,   3306,   1546,    926,    590,    261,    154,     65,     38,
// 50000,  12225,   3688,   1686,    994,    617,    263,    155,     67,     37,
//
// stddev sampled vs theta = winning percent
//     N,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
//  1000, 119.444, 176.939, 195.837, 176.534, 153.460, 110.204, 78.946, 40.537, 24.501,
//  5000, 1008.455, 893.249, 669.987, 478.499, 347.139, 176.844, 101.661, 52.668, 28.712,
// 10000, 2056.201, 1425.911, 891.215, 583.694, 381.797, 199.165, 113.188, 52.029, 27.933,
// 20000, 3751.976, 2124.064, 1051.194, 656.632, 449.989, 190.791, 123.333, 47.084, 28.173,
// 50000, 6873.319, 2708.147, 1274.291, 740.712, 475.265, 194.538, 130.865, 51.086, 26.439,