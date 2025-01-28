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
            val contestUA = ContestUnderAudit(contest).makeClcaAssertions(cvrs)
            val compareAssorter = contestUA.clcaAssertions.first().cassorter as ClcaAssorter

            val sampler = ClcaSimulation(cvrs,
                contestUA.contest as Contest,
                compareAssorter,
                ClcaErrorRates.standard,
            )

            testLimits(sampler, N, compareAssorter.upperBound)

            val errs = sampler.errorRates
            assertEquals(errs.p1o * N, sampler.flippedVotesP1o.toDouble())
            assertEquals(errs.p2o * N, sampler.flippedVotesP2o.toDouble())
            assertEquals(errs.p1u * N, sampler.flippedVotesP1u.toDouble())
            assertEquals(errs.p2u * N, sampler.flippedVotesP2u.toDouble())

            val noerror = compareAssorter.noerror
            val p = 1.0 - errs.p1o - errs.p2o - errs.p1u - errs.p2u
            assertEquals(errs.p1o * N, countAssortValues(sampler, N, noerror / 2).toDouble())
            assertEquals(errs.p2o * N, countAssortValues(sampler, N, 0.0).toDouble())
            assertEquals(p * N, countAssortValues(sampler, N, noerror).toDouble())
            assertEquals(errs.p1u * N, countAssortValues(sampler, N, 3 * noerror / 2).toDouble())
            assertEquals(errs.p2u * N, countAssortValues(sampler, N, 2 * noerror).toDouble())
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
            val contestUA = ContestUnderAudit(contest).makeClcaAssertions(cvrs)
            val compareAssorter = contestUA.clcaAssertions.first().cassorter

            run(cvrs, contestUA, compareAssorter as ClcaAssorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, assorter: ClcaAssorter) {
        println("\n${assorter.assorter.desc()}")

        val sampler = ClcaSimulation(cvrs, contestUA.contest, assorter, ClcaErrorRates.standard)

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