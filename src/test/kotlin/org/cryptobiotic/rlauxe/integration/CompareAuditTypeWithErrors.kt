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
import org.cryptobiotic.rlauxe.core.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.makePollingAudit
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.plots.SRT
import org.cryptobiotic.rlauxe.plots.SRTwriter
import org.cryptobiotic.rlauxe.plots.colHeader
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.plots.makeSRT
import org.cryptobiotic.rlauxe.plots.plotNTpct
import org.cryptobiotic.rlauxe.plots.plotNTsuccess
import kotlin.test.Test

// PlotSampleSizes
// DiffMeans, PlotDiffMeans
// DiffAuditType

// compare ballot polling to card comparison
class CompareAuditTypeWithErrors {
    val showCalculation = false
    val showContests = false
    val showAllPlots = false
    val showPctPlots = false
    val showGeoMeanPlots = false

    val N = 10000

    data class AlphaMartTask(val idx: Int, val N: Int, val cvrMean: Double, val cvrs: List<Cvr>)

    @Test
    fun plotAuditTypes() {
        val cvrMeans = listOf(.506, .507, .508, .51, .52, .53, .54)// , .6, .65, .7)
        // val cvrMeans = listOf(.506, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val tasks = mutableListOf<AlphaMartTask>()
        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(AlphaMartTask(taskIdx++, N, cvrMean, cvrs))
            }
        }

        val nthreads = 30
        val ntrials = 10000
        val d = 1000
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75)

        // val reportedMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val reportedMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiffs = listOf(-0.005) // listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)

        val writer = SRTwriter("/home/stormy/temp/AuditTypes/SRT$ntrials.csv")
        var totalCalculations = 0

        cvrMeanDiffs.forEach { cvrMeanDiff ->
            eta0Factors.forEach { eta0Factor ->

                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                calculate(task, ntrials, eta0Factor = eta0Factor, d = d, cvrMeanDiff = cvrMeanDiff)
                            })
                    }

                    // wait for all calculations to be done
                    joinAll(*calcJobs.toTypedArray())
                }

                val pollingResults = calculations.map { it.first }
                val compareResults = calculations.map { it.second }

                /*
                println("Polling ntrials=$ntrials, N=$N, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
                show(pollingResults)

                println("Comparison ntrials=$ntrials, N=$N, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
                show(compareResults)

                println("Ratio nsamples Comparison/Polling ntrials=$ntrials, N=$N, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
                showRatio(pollingResults, compareResults)
                */

                println("Success Percentage Ratio nsamples Comparison and Polling; ntrials=$ntrials, N=$N, d=$d eta0Factor=$eta0Factor cvrMeanDiff=$cvrMeanDiff; theta (col) vs N (row)")
                colHeader(pollingResults, "theta", colf = "%6.3f") { it.theta }

                plotNTsuccess(pollingResults, "polling", 20)
                plotNTsuccess(compareResults, "compare", 20)
                showSuccessRatio(pollingResults, compareResults, 20)

                calculations = mutableListOf<Pair<SRT, SRT>>()
            }
        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    fun showRatio(pollSrs: List<SRT>, compareSrs: List<SRT>) {
        colHeader(pollSrs, "theta", colf = "%6.3f") { it.theta }

        val srs = pollSrs.mapIndexed { idx, it ->
            val comp = compareSrs[idx].nsamples
            val poll = if (it.nsamples == 0.0) 1.0 else it.nsamples.toDouble()
            val den = if (poll == 0.0) 1.0 else poll

            it.copy(stddev = (comp - poll) / den) // hijack stddev
        }

        val utitle = ""
        plotSRS(srs, utitle, false, ff = "%6.2f", colTitle = "cvrMean",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.stddev }
        )
    }

    fun showSuccessRatio(pollSrs: List<SRT>, compareSrs: List<SRT>, sampleMaxPct: Int) {

        val srs = pollSrs.mapIndexed { idx, it ->
            val comp = compareSrs[idx].percentHist!!.cumul(sampleMaxPct)
            val poll = it.percentHist!!.cumul(sampleMaxPct)
            val fld = (comp - poll)
            it.copy(stddev = fld) // hijack stddev
        }

        plotSRS(srs, "RLA success difference for sampleMaxPct=$sampleMaxPct % cutoff: (compare - polling)", false, ff = "%6.2f", colTitle = "cvrMean",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.stddev }
        )
    }

    fun show(srs: List<SRT>) {
        colHeader(srs, "theta", colf = "%6.3f") { it.theta }
        plotNTpct(srs, "title", colTitle = "cvrMean")

        /*
        plotSRS(srts, "successes", true, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsuccess.toDouble() }
        )

        plotSRS(srts, "successPct", false, colf = "%6.3f", rowf = "%6.2f", ff = "%6.1f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.successPct }
        )

        plotSRS(srts, "nsamples", true, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        plotSRS(srts, "pct nsamples", false, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.pctSamples}
        )

        plotTFsuccess(srts, "", sampleMaxPct = 10, colTitle = "theta")
        plotTFsuccess(srts, "", sampleMaxPct = 20, colTitle = "theta")
        plotTFsuccess(srts, "", sampleMaxPct = 30, colTitle = "theta")
        plotTFsuccess(srts, "", sampleMaxPct = 40, colTitle = "theta")
        plotTFsuccess(srts, "", sampleMaxPct = 50, colTitle = "theta")
        plotTFsuccess(srts, "", sampleMaxPct = 100, colTitle = "theta")

         */
    }

    fun calculate(task: AlphaMartTask, ntrials: Int, cvrMeanDiff: Double, d: Int, eta0Factor: Double): Pair<SRT, SRT> {
        // if (margin2theta(task.margin) + reportedMeanDiff <= .5) return null
        val prr = runDiffAuditTypes(
            cvrMean = task.cvrMean,
            cvrs = task.cvrs,
            cvrMeanDiff = cvrMeanDiff,
            ntrials = ntrials,
            eta0Factor = eta0Factor,
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
        silent: Boolean = true
    ): Pair<SRT, SRT> {
        val N = cvrs.size
        val theta = cvrMean + cvrMeanDiff // the true mean
        if (!silent) println(" N=${cvrs.size} theta=$theta d=$d diffMean=$cvrMeanDiff")

        val pollingAssorter = makeStandardPluralityAssorter()
        val compareAssorter = makeStandardComparisonAssorter(cvrMean)
        val comparisonSample = ComparisonWithErrors(cvrs, compareAssorter, theta)
        val mvrs = comparisonSample.mvrs

        val pollingResult = runAlphaMartRepeated(
            drawSample = PollWithoutReplacement(mvrs, pollingAssorter),
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
            eta0 = eta0Factor *  compareAssorter.noerror,
            d = d,
            ntrials = ntrials,
            withoutReplacement = true,
            upperBound = compareAssorter.upperBound
        )
        // fun makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0, rr: AlphaMartRepeatedResult): SRT {
        return Pair(
            makeSRT(N, cvrMean, cvrMeanDiff, d, eta0Factor, pollingResult),
            makeSRT(N, cvrMean, cvrMeanDiff, d, eta0Factor, compareResult))
    }

    fun runDiffAuditTypesOld(
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
}
