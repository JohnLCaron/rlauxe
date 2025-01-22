package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.ClcaErrorRates
import kotlin.test.Test
import kotlin.test.assertEquals

class TestComparisonSamplerSimulation {

    @Test
    fun testComparisonSamplerPlurality() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)

            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
            val compareAssorter = contestUA.comparisonAssertions.first().cassorter as ComparisonAssorter

            val sampler = ComparisonSimulation(cvrs,
                contestUA.contest as Contest,
                compareAssorter,
                ClcaErrorRates.standard,
            )

            testLimits(sampler, N, compareAssorter.upperBound)

            assertEquals(sampler.p1 * N, sampler.flippedVotes1.toDouble())
            assertEquals(sampler.p2 * N, sampler.flippedVotes2.toDouble())
            assertEquals(sampler.p3 * N, sampler.flippedVotes3.toDouble())
            assertEquals(sampler.p4 * N, sampler.flippedVotes4.toDouble())

            val noerror = compareAssorter.noerror
            val p = 1.0 - sampler.p1 - sampler.p2 - sampler.p3 - sampler.p4
            assertEquals(sampler.p1 * N, countAssortValues(sampler, N, noerror / 2).toDouble())
            assertEquals(sampler.p2 * N, countAssortValues(sampler, N, 0.0).toDouble())
            assertEquals(p * N, countAssortValues(sampler, N, noerror).toDouble())
            assertEquals(sampler.p3 * N, countAssortValues(sampler, N, 3 * noerror / 2).toDouble())
            assertEquals(sampler.p4 * N, countAssortValues(sampler, N, 2 * noerror).toDouble())
        }
    }

    @Test
    fun testComparisonSamplerStandard() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)
            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
            val compareAssorter = contestUA.comparisonAssertions.first().cassorter

            run(cvrs, contestUA, compareAssorter as ComparisonAssorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, assorter: ComparisonAssorter) {
        println("\n${assorter.assorter.desc()}")

        val sampler = ComparisonSimulation(cvrs, contestUA.contest, assorter, ClcaErrorRates.standard)

        val orgCvrs = cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { assorter.assorter.assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrs.map { assorter.bassort(it, it) }.average()
        sampler.reset()
        val welford = Welford()
        repeat(cvrs.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(assorter.noerror)} noerror=${df(before)} sampleMean = ${df(welford.mean)}")
    }

}