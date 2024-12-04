package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test


class MakeSampleSizePlots {
    val useStyles = true

    @Test
    fun plotComparisonVsPoll() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.01, ntrials = 1000)
        val finder = EstimateSampleSize(auditConfig)
        val N = 100000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest(useStyles)
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.minPollingAssertion()!!.assorter
            tasks.add( PollingTask("Polling: margin = $margin", finder, contestUA, assort, N))

            // comparison
            val cvrs = fcontest.makeCvrs()
            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.minComparisonAssertion()!!.cassorter
            tasks.add( ComparisonTask("Comparison: margin = $margin", finder, contestUA, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonVsPoll1000"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples( catfld = { if (it.isPolling) "polling" else "comparison"})
    }

    @Test
    fun plotVsFuzz() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.01, ntrials = 100)
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest(useStyles)
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.minPollingAssertion()!!.assorter
            val standardEstimator = EstimateSampleSize(auditConfig)
            tasks.add( PollingTask("Polling (standard): margin = $margin", standardEstimator, contestUA, assort, N))

            // alternative
            val fuzzEstimator = EstimateSampleSize(auditConfig.copy(fuzzPct=.01))
            tasks.add( PollingTask("Polling fuzz=.01: margin = $margin", fuzzEstimator, contestUA, assort, N))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "PollStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
    fun compareVsFuzz() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=null, ntrials = 100)
        val N = 100000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest(useStyles)
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            val cvrs = fcontest.makeCvrs()
            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.minComparisonAssertion()!!.cassorter
            tasks.add( ComparisonTask("Comparison (standard): margin = $margin", EstimateSampleSize(auditConfig), contestUA, cassort, cvrs))

            // alternative
            val configAlt1 = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.01, ntrials = 100)
            tasks.add( ComparisonTask("Comparison fuzz=.01: margin = $margin", EstimateSampleSize(configAlt1), contestUA, cassort, cvrs))

            val configAlt2 = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.001, ntrials = 100)
            tasks.add( ComparisonTask("Comparison fuzz=.001: margin = $margin", EstimateSampleSize(configAlt2),  contestUA, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
    fun readCompareVsFuzz() {
        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
    fun plotPollingFuzz() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.01, ntrials = 1000)
        val finder = EstimateSampleSize(auditConfig)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<AlphaTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val fcontest = TestContest(0, 4, margin)
                fcontest.ncards = N
                val contest = fcontest.makeContest(useStyles)
                val cvrs = fcontest.makeCvrs()

                print("fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val contestUA = ContestUnderAudit(contest, N)
                contestUA.makePollingAssertions()
                val minAssort = contestUA.minPollingAssertion()!!.assorter
                val sampleFn = PollingFuzzSampler(fuzzPct, cvrs, contestUA, minAssort)

                val otherParameters = mapOf("fuzzPct" to fuzzPct)
                tasks.add( AlphaTask("fuzzPct = $fuzzPct, margin = $margin", finder,
                    sampleFn, margin, minAssort.upperBound(), N, N, otherParameters,
                ))
            }
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirName = "/home/stormy/temp/estimate"
        val filename = "PollingFuzzConcurrent"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showFuzzedSamples()
    }

    @Test
    fun plotComparisonFuzz() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct=.01, ntrials = 1000)
        val finder = EstimateSampleSize(auditConfig)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<BettingTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val fcontest = TestContest(0, 4, margin)
                fcontest.ncards = N
                val contest = fcontest.makeContest(useStyles)
                val cvrs = fcontest.makeCvrs().map { it }

                print("fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val contestUA = ContestUnderAudit(contest, N)

                // comparison; regen mvrs each repition to smoothe things out
                contestUA.makeComparisonAssertions(cvrs)
                val minAssort = contestUA.minComparisonAssertion()!!.cassorter
                val sampleFn = ComparisonFuzzSampler(fuzzPct, cvrs, contestUA, minAssort)

                val otherParameters = mapOf("fuzzPct" to fuzzPct)
                tasks.add( BettingTask("fuzzPct = $fuzzPct, margin = $margin", finder,
                    sampleFn, margin, minAssort.noerror, minAssort.upperBound(), N, N,
                    ComparisonErrorRates.getErrorRates(contestUA.ncandidates, fuzzPct),
                    otherParameters,
                ))
            }
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirName = "/home/stormy/temp/estimate"
        val filename = "ComparisonFuzzConcurrent"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showFuzzedSamples()
    }

    @Test
    fun plotPollingNoStyle() {
        val Nc = 10000
        val Ns = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=false, seed = 123556667890L, quantile=.50, fuzzPct=0.0, ntrials = 10)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile}")

        val tasks = mutableListOf<ConcurrentTask>()
        Ns.forEach { N ->
            margins.forEach { margin ->
                val fcontest = TestContest(0, 4, margin)
                fcontest.ncards = Nc
                val contest = fcontest.makeContest(useStyles)

                print("margin = $margin ${contest.votes} Nc=$Nc N=$N")
                val contestUA = ContestUnderAudit(contest, Nc)
                contestUA.makePollingAssertions()
                val assort = contestUA.minPollingAssertion()!!.assorter
                val standardEstimator = EstimateSampleSize(auditConfig)
                tasks.add( PollingNoStyleTask("Polling (no styles): margin=$margin N=$N", standardEstimator, contestUA, assort, Nc, N))
            }
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(Nc, 0.0, 0.0)}

        val dirName = "/home/stormy/temp/estimate"
        val filename = "PollingNoStyle"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showFuzzedSamples()
    }
}

class BettingTask(val name: String,
                  val finder: EstimateSampleSize,
                  val sampleFn: SampleGenerator,
                  val margin: Double,
                  val noerror: Double,
                  val upperBound: Double,
                  val maxSamples: Int,
                  val Nc: Int,
                  val errorRates: List<Double>,
                  val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        //  this uses auditConfig p1,,p4 to set apriori error rates. should be based on fuzzPct i think
        return simulateSampleSizeBetaMart(finder.auditConfig, sampleFn, margin, noerror, upperBound, Nc, Nc, errorRates, moreParameters=otherParameters)
    }
}

class AlphaTask(val name: String,
                val finder: EstimateSampleSize,
                val sampleFn: SampleGenerator,
                val margin: Double,
                val upperBound: Double,
                val maxSamples: Int,
                val Nc: Int,
                val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        //         sampleFn: SampleGenerator,
        //        margin: Double,
        //        upperBound: Double,
        //        maxSamples: Int,
        //        startingTestStatistic: Double = 1.0,
        //        Nc: Int,
        //        moreParameters: Map<String, Double> = emptyMap(),
        return finder.simulateSampleSizeAlphaMart(sampleFn, margin, upperBound, maxSamples=Nc, Nc=Nc, moreParameters=otherParameters)
    }
}

class PollingTask(val name: String,
                  val finder: EstimateSampleSize,
                  val contestUA: ContestUnderAudit,
                  val assort: AssorterFunction,
                  val Nc: Int,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        //         sampleFn: SampleGenerator,
        //        margin: Double,
        //        upperBound: Double,
        //        maxSamples: Int,
        //        startingTestStatistic: Double = 1.0,
        //        Nc: Int,
        //        moreParameters: Map<String, Double> = emptyMap(),
        return finder.simulateSampleSizePollingAssorter(contestUA, assort, Nc)
    }
}

class PollingNoStyleTask(val name: String,
                  val finder: EstimateSampleSize,
                  val contestUA: ContestUnderAudit,
                  val assort: AssorterFunction,
                  val Nc: Int,
    val N: Int
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        // TODO
        return finder.simulateSampleSizePollingAssorter(contestUA, assort, Nc)
    }
}

class ComparisonTask(val name: String,
                     val finder: EstimateSampleSize,
                     val contestUA: ContestUnderAudit,
                     val cassort: ComparisonAssorter,
                     val cvrs: List<Cvr>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return finder.simulateSampleSizeComparisonAssorter(contestUA, cassort, cvrs)
    }
}

