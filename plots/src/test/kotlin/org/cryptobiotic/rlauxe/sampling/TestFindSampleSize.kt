package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AuditConfig
import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.margin2mean
import java.util.concurrent.TimeUnit
import kotlin.test.Test


class PlotFindSampleSize {

    @Test
    fun testFindSampleSize() {
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, ntrials = 1000)
        val finder = FindSampleSize(auditConfig)
        val N = 100000
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

        val writer = SRTcsvWriter("/home/stormy/temp/polling/EstimateSampleSize.csv")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = EstimateSampleSize("/home/stormy/temp/polling", "EstimateSampleSize.csv")
        plotter.showSamples()
    }
}