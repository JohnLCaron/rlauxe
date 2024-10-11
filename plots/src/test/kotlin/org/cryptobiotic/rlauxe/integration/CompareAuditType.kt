@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.integration

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
import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonNoErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.makePollingAudit
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.sim.AlphaMartRepeatedResult
import org.cryptobiotic.rlauxe.sim.makeSRT
import org.cryptobiotic.rlauxe.sim.runAlphaMartRepeated
import kotlin.test.Test

// PlotSampleSizes
// DiffMeans, PlotDiffMeans
// DiffAuditType

// compare ballot polling to card comparison
class CompareAuditType {
    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    val N = 10000

    data class AlphaMartTask(val idx: Int, val N: Int, val theta: Double, val cvrs: List<Cvr>)

    @Test
    fun plotOver() {
        val theta = .505
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrOver = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = .01,
            silent = false
        )
        println("rrOver")
        println("polling = ${rrOver.first}")
        println("comparison = ${rrOver.second}")
    }

    @Test
    fun plotUnder() {
        val theta = .51
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrUnder = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.03,
            silent = false
        )
        println("rrUnder")
        println("polling = ${rrUnder.first}")
        println("comparison = ${rrUnder.second}")
    }

    @Test
    fun plotEven() {
        val theta = .51
        val cvrs = makeCvrsByExactMean(N, theta)

        val rrEven = runDiffAuditTypes(
            theta,
            cvrs,
            nrepeat = 100,
            reportedMeanDiff = -.01,
            silent = false
        )
        println("rrEven")
        println("polling = ${rrEven.first}")
        println("comparison = ${rrEven.second}")
    }

    @Test
    fun plotAuditTypesConcurrent() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val theta = listOf(.505, .55, .7)
        // val thetas = theta.map{ theta2margin(it) }
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<AlphaMartTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            thetas.forEach { theta ->
                val cvrs = makeCvrsByExactMean(N, theta)
                tasks.add(AlphaMartTask(taskIdx++, N, theta, cvrs))
            }
        }

        val nthreads = 30
        val nrepeat = 1000

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(10, 50, 250, 1250)

        val writer = SRTcsvWriter("/home/stormy/temp/AuditTypes/SRT$nrepeat.csv")
        var totalCalculations = 0

        reportedMeanDiffs.forEach { reportedMeanDiff ->
            val dlcalcs = mutableMapOf<Int, List<SRT>>()
            dl.forEach { d ->
                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                calculate(task, nrepeat, d = d, reportedMeanDiff = reportedMeanDiff)
                            })
                    }

                    // wait for all calculations to be done
                    joinAll(*calcJobs.toTypedArray())
                }
                /*
                dlcalcs[d] = calculations.toList()
                writer.writeCalculations(calculations)
                println("$reportedMeanDiff, $d ncalcs = ${calculations.size}")
                totalCalculations += calculations.size

                if (showAllPlots) {
                    plotSRTsamples(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTstdev(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 10, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 20, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                    plotSRTsuccess(calculations, thetas, nlist, 30, nrepeat, "d=$d reportedMeanDiff=$reportedMeanDiff")
                } else if (showPctPlots) {
                    plotSRTpct(calculations, thetas, nlist, "d=$d reportedMeanDiff=$reportedMeanDiff")
                }

                 */

                calculations = mutableListOf<Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>>()
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    fun calculate(task: AlphaMartTask, nrepeat: Int, d: Int, reportedMeanDiff: Double): Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult> {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val rr = runDiffAuditTypes(
            task.theta,
            task.cvrs,
            reportedMeanDiff = reportedMeanDiff,
            nrepeat = nrepeat,
            d = d,
            silent = true
        )
        // println("${task.idx} (${calculations.size}): ${task.N}, ${task.theta}, \npolling=${rr.first} \ncomparison=${rr.second}")
        return rr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<AlphaMartTask>): ReceiveChannel<AlphaMartTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<AlphaMartTask>,
        calculate: (AlphaMartTask) -> Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult>,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            mutex.withLock { calculations.add(calculation) }
            yield()
        }
    }

    fun runDiffAuditTypes(
        theta: Double,
        cvrs: List<Cvr>,
        reportedMeanDiff: Double,
        nrepeat: Int,
        d: Int = 500,
        silent: Boolean = true,
    ): Pair<AlphaMartRepeatedResult, AlphaMartRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" N=${cvrs.size} theta=$theta withoutReplacement")

        // ignore the "reported winner". just focus on d vs reportedMeanDiff
        val reportedMean = theta + reportedMeanDiff

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        // polling
        val pollingAudit = makePollingAudit(contests = listOf(contest))
        val pollingAssertions = pollingAudit.assertions[contest]
        require(pollingAssertions!!.size == 1)
        val pollingAssertion = pollingAssertions.first()
        val pollingSampler = PollWithoutReplacement(cvrs, pollingAssertion.assorter)
        //println("pollingSampler mean=${pollingSampler.truePopulationMean()} count=${pollingSampler.truePopulationCount()}")
        val pollingResult = runAlphaMartRepeated(
            drawSample = PollWithoutReplacement(cvrs, pollingAssertion.assorter),
            maxSamples = N,
            eta0 = reportedMean, // use the reportedMean for the initial guess
            d = d,
            ntrials = nrepeat,
            withoutReplacement = true,
            upperBound = pollingAssertion.assorter.upperBound()
        )

        // comparison
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertions = compareAudit.assertions[contest]
        require(compareAssertions!!.size == 1)
        val compareAssertion = compareAssertions.first()
        // val compareSampler = ComparisonNoErrors(cvrs, compareAssertion.assorter)
        // println("compareSampler mean=${compareSampler.truePopulationMean()} count=${compareSampler.truePopulationCount()}")

        val compareResult = runAlphaMartRepeated(
            drawSample = ComparisonNoErrors(cvrs, compareAssertion.assorter),
            maxSamples = N,
            eta0 = reportedMean, // use the reportedMean for the initial guess
            d = d,
            ntrials = nrepeat,
            withoutReplacement = true,
            upperBound = compareAssertion.assorter.upperBound()
        )

        return Pair(pollingResult, compareResult)
    }

    // using values from alpha.ipynb, compare polling and comparison audit
    @Test
    fun compareAlphaPaper() {
        val d = 100
        val N = 10000
        val reps = 100

        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val etas = listOf(0.9, 1.0, 1.5, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0) // should be .9, 1, 1.009, 2, 2.018

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        val pollingSrs = mutableListOf<SRT>()
        val compareSrs = mutableListOf<SRT>()
        for (theta in thetas) {
            val cvrs = makeCvrsByExactMean(N, theta)

            val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
            val compareAssertion = compareAudit.assertions[contest]!!.first()

            val pollingAudit = makePollingAudit(contests = listOf(contest))
            val pollingAssertion = pollingAudit.assertions[contest]!!.first()

            for (eta in etas) {
                val compareResult: AlphaMartRepeatedResult = runAlphaMartRepeated(
                    drawSample = ComparisonNoErrors(cvrs, compareAssertion.assorter),
                    maxSamples = N,
                    eta0 = eta,
                    d = d,
                    ntrials = reps,
                    upperBound = compareAssertion.assorter.upperBound,
                )
                compareSrs.add(makeSRT(N, theta, 0.0, d, rr=compareResult))

                val pollingResult = runAlphaMartRepeated(
                    drawSample = PollWithoutReplacement(cvrs, pollingAssertion.assorter),
                    maxSamples = N,
                    eta0 = eta, // use the reportedMean for the initial guess
                    d = d,
                    ntrials = reps,
                    withoutReplacement = true,
                    upperBound = pollingAssertion.assorter.upperBound()
                )
                pollingSrs.add(makeSRT(N, theta, 0.0, d, rr=pollingResult))
            }
        }

        val ctitle = " nsamples, ballot comparison, N=$N, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(compareSrs, ctitle, true, colf = "%6.3f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val ptitle = " nsamples, ballot polling, N=$N, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(pollingSrs, ptitle, true, colf = "%6.3f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )
    }
    //  nsamples, ballot comparison, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9955,   9766,   9336,   8571,   7461,   2257,    464,    221,    140,    101,     59,     41,     25,     18,
    // 1.000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
    // 1.500,   9916,   9014,   5954,   3189,   1827,    418,    153,     98,     74,     59,     39,     29,     19,     14,
    // 2.000,   9825,   6722,   2923,   1498,    937,    309,    148,     98,     74,     59,     39,     29,     19,     14,
    // 5.000,   5173,   1620,    962,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 7.500,   3310,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //10.000,   2765,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //15.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //20.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //
    // nsamples, ballot polling, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9683,   9762,   9641,   9615,   9476,   9043,   7995,   6446,   4840,   3759,   1764,    852,    242,     84,
    // 1.000,   9683,   9562,   9843,   9723,   9408,   9585,   8691,   7847,   7313,   6191,   3988,   2783,   1293,    589,
    // 1.500,   9483,   9562,   9246,   9426,   9307,   9414,   9234,   8778,   8521,   9001,   8350,   7672,   6309,   6069,
    // 2.000,   9782,   9664,   9644,   9525,   9903,   9510,   9040,   8873,   8893,   8725,   8175,   7586,   6545,   6217,
    // 5.000,   9682,   9663,   9247,   9524,   9607,   9803,   9137,   9343,   8890,   8548,   8092,   7752,   6695,   5934,
    // 7.500,   9782,   9763,   9544,   9426,   9803,   9609,   9427,   9150,   8701,   8547,   8438,   7417,   6537,   5717,
    //10.000,   9683,   9564,   9742,   9624,   9706,   9512,   9039,   8962,   8890,   8908,   8352,   7589,   7080,   5717,
    //15.000,   9284,   9863,   9941,   9526,   9604,   9512,   9234,   9060,   8704,   8547,   7997,   7748,   6617,   6146,
    //20.000,   9582,   9862,   9445,   9922,   9408,   9513,   9329,   8965,   8799,   9002,   8437,   7421,   6927,   6360,
}
