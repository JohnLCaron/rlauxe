package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.margin2mean
import java.util.concurrent.TimeUnit
import kotlin.test.Test


class PlotFindSampleSizes {

    @Test
    fun plotComparisonVsPoll() {
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 1000)
        val finder = FindSampleSize(auditConfig)
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val srts = mutableListOf<SRT>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val stopwatch = Stopwatch()
            val fcontest = FuzzedContest(0, 2, margin)
            fcontest.ncards = N
            val contest = fcontest.makeContest()
            print("margin = $margin ${contest.votes}")
            val contestUA = ContestUnderAudit(contest, N).makePollingAssertions()

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.pollingAssertions.first().assorter
            val result = finder.simulateSampleSizePollingAssorter(contestUA, assort, N)
            // RunTestRepeatedResult.makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double)
            srts.add(result.makeSRT(N, margin2mean(margin), 0.0))

            // comparison
            val cvrs = fcontest.makeCvrs().map { CvrUnderAudit.fromCvrIF(it, false)}
            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.comparisonAssertions.first().assorter
            val cresult = finder.simulateSampleSizeAssorter(contestUA, cassort, cvrs)
            // RunTestRepeatedResult.makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double)
            srts.add(cresult.makeSRT(N, margin2mean(margin), 0.0))
            println(" took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
        }

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonVsPoll"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = EstimateSampleSize(dirname, filename)
        plotter.showSamples()
    }

    @Test
    fun plotPollingFuzzConcurrent() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 1000)
        val finder = FindSampleSize(auditConfig)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<AlphaTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val fcontest = FuzzedContest(0, 4, margin)
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
                val fcontest = FuzzedContest(0, 4, margin)
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