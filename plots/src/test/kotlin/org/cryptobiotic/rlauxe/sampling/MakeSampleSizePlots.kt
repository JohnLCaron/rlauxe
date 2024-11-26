package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.*
import kotlin.test.Test


class MakeSampleSizePlots {

    @Test
    fun plotComparisonVsPoll() {
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 100)
        val finder = FindSampleSize(auditConfig)
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest()
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.minPollingAssertion().assorter
            tasks.add( PollingTask("Polling: margin = $margin", finder, contestUA, assort, N))

            // comparison
            val cvrs = fcontest.makeCvrs().map { CvrUnderAudit.fromCvrIF(it, false)}
            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.minComparisonAssertion().assorter
            tasks.add( ComparisonTask("Comparison: margin = $margin", finder, contestUA, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonVsPoll10"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = EstimateSampleSize(dirname, filename)
        plotter.showSamples( catfld = { if (it.isPolling) "polling" else "comparison"})
    }

    @Test
    fun plotVsFuzz() {
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 100)
        val finder = FindSampleSize(auditConfig)
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest()
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.minPollingAssertion().assorter
            tasks.add( PollingTask("Polling (standard): margin = $margin", finder, contestUA, assort, N))

            // alternative
            tasks.add( PollingAltTask("Polling fuzz=.01: margin = $margin", finder, fuzzPct=.01, contestUA, assort, N))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "PollStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = EstimateSampleSize(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
    fun compareVsFuzz() {
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 100)
        val finder = FindSampleSize(auditConfig)
        val N = 100000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val fcontest = TestContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest()
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N)

            val cvrs = fcontest.makeCvrs()
            val cvrsUA = cvrs.map { CvrUnderAudit.fromCvrIF(it, false)}
            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.minComparisonAssertion().assorter
            tasks.add( ComparisonTask("Comparison (standard): margin = $margin", finder, contestUA, cassort, cvrsUA))

            // alternative
            tasks.add( ComparisonAltTask("Comparison fuzz=.01: margin = $margin", finder, fuzzPct=.01, contestUA, cassort, cvrs))

            tasks.add( ComparisonAltTask("Comparison fuzz=.001: margin = $margin", finder, fuzzPct=.001, contestUA, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map{ it.makeSRT(N, 0.0, 0.0)}

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = EstimateSampleSize(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
        fun readCompareVsFuzz() {
        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"

        val plotter = EstimateSampleSize(dirname, filename)
        plotter.showSamples( catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"}
        )
    }

    @Test
    fun plotPollingFuzz() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 1000)
        val finder = FindSampleSize(auditConfig)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<AlphaTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val fcontest = TestContest(0, 4, margin)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                val cvrs = fcontest.makeCvrs().map { it as Cvr }

                print("fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val contestUA = ContestUnderAudit(contest, N)
                contestUA.makePollingAssertions()
                val minAssort = contestUA.minPollingAssertion().assorter
                val sampleFn = PollingSamplerRegen(fuzzPct, cvrs, contestUA, minAssort)

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

        val plotter = EstimateSampleSize(dirName, filename)
        plotter.showFuzzedSamples()
    }

    @Test
    fun plotComparisonFuzzConcurrent() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 1000)
        val finder = FindSampleSize(auditConfig)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<BettingTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val fcontest = TestContest(0, 4, margin)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                val cvrs = fcontest.makeCvrs().map { it as Cvr }

                print("fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val contestUA = ContestUnderAudit(contest, N)

                // comparison; regen mvrs each repition to smoothe things out
                contestUA.makeComparisonAssertions(cvrs)
                val minAssort = contestUA.minComparisonAssertion().assorter
                val sampleFn = ComparisonSamplerRegen(fuzzPct, cvrs, contestUA, minAssort)

                val otherParameters = mapOf("fuzzPct" to fuzzPct)
                tasks.add( BettingTask("fuzzPct = $fuzzPct, margin = $margin", finder,
                    sampleFn, margin, minAssort.noerror, minAssort.upperBound(), N, N, otherParameters,
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

        val plotter = EstimateSampleSize(dirName, filename)
        plotter.showFuzzedSamples()
    }
}

class BettingTask(val name: String,
                  val finder: FindSampleSize,
                  val sampleFn: GenSampleFn,
                  val margin: Double,
                  val noerror: Double,
                  val upperBound: Double,
                  val maxSamples: Int,
                  val Nc: Int,
                  val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        //  this uses auditConfig p1,,p4 to set apriori error rates. should be based on fuzzPct i think
        return finder.simulateSampleSizeBetaMart(sampleFn, margin, noerror, upperBound, Nc, Nc, otherParameters)
    }
}

class AlphaTask(val name: String,
                  val finder: FindSampleSize,
                  val sampleFn: GenSampleFn,
                  val margin: Double,
                  val upperBound: Double,
                  val maxSamples: Int,
                  val Nc: Int,
                  val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return finder.simulateSampleSizeAlphaMart(sampleFn, margin, upperBound, Nc, Nc, otherParameters)
    }
}

class PollingTask(val name: String,
                val finder: FindSampleSize,
                val contestUA: ContestUnderAudit,
                val assort: AssorterFunction,
                val Nc: Int,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return finder.simulateSampleSizePollingAssorter(contestUA, assort, Nc)
    }
}

class ComparisonTask(val name: String,
                  val finder: FindSampleSize,
                  val contestUA: ContestUnderAudit,
                  val cassort: ComparisonAssorter,
                  val cvrs: List<CvrUnderAudit>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return finder.simulateSampleSizeAssorter(contestUA, cassort, cvrs)
    }
}

class ComparisonAltTask(
    val name: String,
    val finder: FindSampleSize,
    val fuzzPct: Double,
    val contestUA: ContestUnderAudit,
    val cassort: ComparisonAssorter,
    val cvrs: List<Cvr>,
) : ConcurrentTask {
    override fun name() = name
    override fun run(): RunTestRepeatedResult {
        return finder.simulateSampleSizeAssorterAlt(fuzzPct, contestUA, cassort, cvrs, mapOf("fuzzPct" to fuzzPct))
    }
}

class PollingAltTask(
    val name: String,
    val finder: FindSampleSize,
    val fuzzPct: Double,
    val contestUA: ContestUnderAudit,
    val assort: AssorterFunction,
    val Nc: Int,
) : ConcurrentTask {
    override fun name() = name
    override fun run(): RunTestRepeatedResult {
        return finder.simulateSampleSizePollingAlt(fuzzPct, contestUA, assort, Nc, mapOf("fuzzPct" to fuzzPct))
    }
}

