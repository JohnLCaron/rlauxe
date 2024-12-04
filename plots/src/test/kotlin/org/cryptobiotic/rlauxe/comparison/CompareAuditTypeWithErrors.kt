@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.comparison

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
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.sampling.ComparisonWithErrors
import org.cryptobiotic.rlauxe.sampling.PollWithoutReplacement
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.plots.colHeader
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.plots.plotNTsuccess
import org.cryptobiotic.rlauxe.plots.plotNTsuccessDecile
import org.cryptobiotic.rlauxe.plots.plotNTsuccessPct
import org.cryptobiotic.rlauxe.plots.plotTFdiffSuccessDecile
import org.cryptobiotic.rlauxe.plots.plotTFsuccessDecile
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.sim.runAlphaMartRepeated
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import kotlin.test.Test

// PlotSampleSizes
// DiffMeans, PlotDiffMeans
// DiffAuditType

// compare ballot polling to card comparison
class CompareAuditTypeWithErrors {
    val showCalculation = false
    data class AlphaMartTask(val idx: Int, val N: Int, val cvrMean: Double, val eta0Factor: Double, val cvrs: List<Cvr>)

    @Test
    fun plotAuditTypesNT() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)// , .6, .65, .7)
        // val cvrMeans = listOf(.506, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val tasks = mutableListOf<AlphaMartTask>()
        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(AlphaMartTask(taskIdx++, N, cvrMean, 1.0, cvrs))
            }
        }

        val nthreads = 30
        val ntrials = 1000
        val d = 1000
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75)
        val cvrMeanDiff =
            -0.005 // listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)

        val writer = SRTcsvWriter("/home/stormy/temp/AuditTypes/NT$ntrials.csv")
        var totalCalculations = 0

        eta0Factors.forEach { eta0Factor ->

            runBlocking {
                val taskProducer = produceTasks(tasks)
                val calcJobs = mutableListOf<Job>()
                repeat(nthreads) {
                    calcJobs.add(
                        launchCalculations(taskProducer) { task ->
                            calculate(task.copy(eta0Factor = eta0Factor), ntrials, d = d, cvrMeanDiff = cvrMeanDiff)
                        })
                }

                // wait for all calculations to be done
                joinAll(*calcJobs.toTypedArray())
            }

            val pollingResults = calculations.map { it.first }
            val compareResults = calculations.map { it.second }
            println("CompareAuditTypeWithErrors.plotAuditTypesNT")

            println("Polling ntrials=$ntrials, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
            showNT(pollingResults)

            println("Comparison ntrials=$ntrials, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
            showNT(compareResults)

            println("Ratio nsamples Comparison/Polling ntrials=$ntrials, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
            showCompMinusPollNT(pollingResults, compareResults)

            println("Success Percentage Ratio nsamples Comparison and Polling; ntrials=$ntrials, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
            colHeader(pollingResults, "theta", colf = "%6.3f") { it.theta }

            plotNTsuccessDecile(pollingResults, "polling", 20)
            plotNTsuccessDecile(compareResults, "compare", 20)
            showSuccessDiff(pollingResults, compareResults, 20)

            calculations = mutableListOf<Pair<SRT, SRT>>()
        }
        writer.close()
    }

    @Test
    fun plotAuditTypesTF() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)// , .6, .65, .7)
        // val cvrMeans = listOf(.506, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val N = 10000
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75)

        val tasks = mutableListOf<AlphaMartTask>()
        var taskIdx = 0
        eta0Factors.forEach { eta0Factor ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(AlphaMartTask(taskIdx++, N, cvrMean, eta0Factor, cvrs))
            }
        }

        val nthreads = 30
        val ntrials = 1000
        val d = 1000

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiff = -0.005// listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)

        val writer = SRTcsvWriter("/home/stormy/temp/AuditTypes/SRT$ntrials.csv")
        var totalCalculations = 0

        println("CompareAuditTypeWithErrors.plotAuditTypesTF ntrials=$ntrials, N=$N, d=$d cvrMeanDiff=$cvrMeanDiff;")

        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d = d, cvrMeanDiff = cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }

        val pollingResults = calculations.map { it.first }
        val compareResults = calculations.map { it.second }

        println("Polling")
        showTF(pollingResults)

        println("Comparison")
        showTF(compareResults)

        // println("Comparison - Polling")
        showDecile(pollingResults, compareResults, 10)
        showDecile(pollingResults, compareResults, 20)
        showDecile(pollingResults, compareResults, 30)
        showDecile(pollingResults, compareResults, 40)
        showDecile(pollingResults, compareResults, 50)
        showDecile(pollingResults, compareResults, 100)

        /*
        println("Success Percentage Ratio nsamples Comparison and Polling theta (col) vs N (row)")
        colHeader(pollingResults, "theta", colf = "%6.3f") { it.theta }
        plotNTsuccess(pollingResults, "polling", 20)
        plotNTsuccess(compareResults, "compare", 20)
        showSuccessRatio(pollingResults, compareResults, 20)
         */

        // writer.writeCalculations(calculations)
        calculations = mutableListOf<Pair<SRT, SRT>>()
        writer.close()
    }


    fun showNT(srts: List<SRT>) {
        colHeader(srts, "theta", colf = "%6.3f") { it.theta }
        plotNTsuccess(srts, "", colTitle = "cvrMean")
        plotNTsuccessPct(srts, "", colTitle = "cvrMean")
        // plotNTsamplesPct(srts, "", colTitle = "cvrMean")
    }

    fun showTF(srts: List<SRT>) {
        colHeader(srts, "cvrMean", colf = "%6.3f") { it.reportedMean }
        //plotTFsuccess(srts, "")
        //plotTFsuccessPct(srts, "")
        //plotTFsamplesPct(srts, "")
        // plotTFsuccessCombo(srts, "")
        plotTFsuccessDecile(srts, "", 20)
        plotTFsuccessDecile(srts, "", 40)
        plotTFsuccessDecile(srts, "", 100)
    }

    fun showDecile(pollingResults: List<SRT>, compareResults: List<SRT>, sampleMaxPct: Int) {
        println("\nDecile $sampleMaxPct %")
        colHeader(pollingResults, "cvrMean", colf = "%6.3f") { it.reportedMean }
        plotTFsuccessDecile(pollingResults, "Polling", sampleMaxPct)
        plotTFsuccessDecile(compareResults, "Comparison", sampleMaxPct)
        plotTFdiffSuccessDecile(pollingResults, compareResults, sampleMaxPct, "")
    }

    fun showCompMinusPollNT(pollSrs: List<SRT>, compareSrs: List<SRT>) {
        colHeader(pollSrs, "theta", colf = "%6.3f") { it.theta }

        val srs = pollSrs.mapIndexed { idx, it ->
            val comp = compareSrs[idx].pctSamples
            val poll = if (it.pctSamples == 0.0) 1.0 else it.pctSamples.toDouble()
            val den = if (poll == 0.0) 1.0 else poll

            it.copy(stddev = (comp - poll) / den) // hijack stddev
        }

        val utitle = "Comparison - Polling"
        plotSRS(srs, utitle, false, ff = "%6.2f", colTitle = "cvrMean",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.stddev }
        )
    }

    fun showCompMinusPollTF(pollSrs: List<SRT>, compareSrs: List<SRT>) {
        val srs = pollSrs.mapIndexed { idx, it ->
            val comp = compareSrs[idx].pctSamples
            val poll = if (it.pctSamples == 0.0) 1.0 else it.pctSamples.toDouble()
            val den = if (poll == 0.0) 1.0 else poll

            it.copy(stddev = (comp - poll) / den) // hijack stddev
        }

        val utitle = "Comparison - Polling"
        plotSRS(srs, utitle, false, ff = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.stddev }
        )
    }

    fun showSuccessDiff(pollSrs: List<SRT>, compareSrs: List<SRT>, sampleMaxPct: Int) {

        val srs = pollSrs.mapIndexed { idx, it ->
            val comp = compareSrs[idx].percentHist!!.cumul(sampleMaxPct)
            val poll = it.percentHist!!.cumul(sampleMaxPct)
            val fld = (comp - poll)
            it.copy(stddev = fld) // hijack stddev
        }

        plotSRS(srs,
            "RLA success difference for sampleMaxPct=$sampleMaxPct % cutoff: (compare - polling)",
            false,
            ff = "%6.2f",
            colTitle = "cvrMean",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.stddev }
        )
    }

    fun calculate(task: AlphaMartTask, ntrials: Int, cvrMeanDiff: Double, d: Int): Pair<SRT, SRT> {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val prr = runDiffAuditTypes(
            cvrMean = task.cvrMean,
            cvrs = task.cvrs,
            cvrMeanDiff = cvrMeanDiff,
            ntrials = ntrials,
            eta0Factor = task.eta0Factor,
            d = d,
            silent = true
        )
        // println("${task.idx} (${calculations.size}): ${task.N}, ${task.theta}, \npolling=${rr.first} \ncomparison=${rr.second}")
        return prr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<AlphaMartTask>): ReceiveChannel<AlphaMartTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<Pair<SRT, SRT>>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<AlphaMartTask>,
        calculate: (AlphaMartTask) -> Pair<SRT, SRT>,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            mutex.withLock {
                calculations.add(calculation)
            }
            yield()
        }
    }

    fun runDiffAuditTypes(
        cvrMean: Double,
        cvrs: List<Cvr>,
        cvrMeanDiff: Double,
        ntrials: Int,
        eta0Factor: Double,
        d: Int = 1000,
        silent: Boolean = true,
    ): Pair<SRT, SRT> {
        val N = cvrs.size
        val theta = cvrMean + cvrMeanDiff // the true mean
        if (!silent) println(" N=${cvrs.size} theta=$theta d=$d diffMean=$cvrMeanDiff")

        val contestUA = ContestUnderAudit(makeContestsFromCvrs(cvrs).first(), cvrs.size)

        contestUA.makePollingAssertions()
        val pollingAssertion = contestUA.pollingAssertions.first()
        val pollingAssorter = pollingAssertion.assorter

        contestUA.makeComparisonAssertions(cvrs)
        val compareAssertion = contestUA.comparisonAssertions.first()
        val compareAssorter = compareAssertion.cassorter

        val comparisonSample = ComparisonWithErrors(cvrs, compareAssorter, theta)
        val mvrs = comparisonSample.mvrs

        val pollingResult = runAlphaMartRepeated(
            drawSample = PollWithoutReplacement(contestUA, mvrs, pollingAssorter),
            maxSamples = N,
            eta0 = cvrMean, // use the reportedMean for the initial guess
            d = d,
            ntrials = ntrials,
            withoutReplacement = true,
            upperBound = pollingAssorter.upperBound()
        )

        val compareResult = runAlphaMartRepeated(
            drawSample = comparisonSample,
            maxSamples = N,
            eta0 = eta0Factor * compareAssorter.noerror,
            d = d,
            ntrials = ntrials,
            withoutReplacement = true,
            upperBound = compareAssorter.upperBound
        )
        // fun makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0, rr: AlphaMartRepeatedResult): SRT {
        return Pair(
            pollingResult.makeSRT(N, cvrMean, cvrMeanDiff),
            compareResult.makeSRT(N, cvrMean, cvrMeanDiff)
        )
    }
}
