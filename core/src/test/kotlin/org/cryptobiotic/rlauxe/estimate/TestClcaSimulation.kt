package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestClcaSimulation {

    @Test
    fun testClcaSimulationErrorRates() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)

            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeClcaAssertions()
            val compareAssorter = contestUA.clcaAssertions.first().cassorter

            val sampler = ClcaSimulation(cvrs,
                contestUA.contest as Contest,
                compareAssorter,
                ClcaErrorTable.standard,
            )

            testLimits(sampler, N, compareAssorter.upperBound())

            val errs = sampler.errorRates
            assertEquals(errs.p1o * N, sampler.flippedVotesP1o.toDouble())
            assertEquals(errs.p2o * N, sampler.flippedVotesP2o.toDouble())
            assertEquals(errs.p1u * N, sampler.flippedVotesP1u.toDouble())
            assertEquals(errs.p2u * N, sampler.flippedVotesP2u.toDouble())

            val noerror = compareAssorter.noerror()
            val p = 1.0 - errs.p1o - errs.p2o - errs.p1u - errs.p2u
            assertEquals(errs.p1o * N, countAssortValues(sampler, N, noerror / 2).toDouble())
            assertEquals(errs.p2o * N, countAssortValues(sampler, N, 0.0).toDouble())
            assertEquals(p * N, countAssortValues(sampler, N, noerror).toDouble())
            assertEquals(errs.p1u * N, countAssortValues(sampler, N, 3 * noerror / 2).toDouble())
            assertEquals(errs.p2u * N, countAssortValues(sampler, N, 2 * noerror).toDouble())
        }
    }

    @Test
    fun testClcaSamplerErrorRates() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)
            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeClcaAssertions()
            val compareAssorter = contestUA.clcaAssertions.first().cassorter

            runClcaSimulation(cvrs, contestUA, compareAssorter)
        }
    }

    fun runClcaSimulation(cvrs: List<Cvr>, contestUA: ContestUnderAudit, assorter: ClcaAssorter) {
        println("\n${assorter.assorter.desc()}")

        val phantomRate = contestUA.contest.phantomRate()
        val errorRates = ClcaErrorRates(0.0, phantomRate, 0.0, 0.0)
        val sampler = ClcaSimulation(cvrs, contestUA.contest, assorter, errorRates)
        sampler.reset()

        val orgCvrs = cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { assorter.assorter.assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrs.map { assorter.bassort(it, it) }.average()

        val tracker = PrevSamplesWithRates(assorter.noerror())
        while (sampler.hasNext()) { tracker.addSample(sampler.next()) }
        println(" bassort expectedNoerror=${df(assorter.noerror())} noerror=${df(before)} sampleMean = ${df(tracker.mean())}")
        assertTrue( tracker.mean() > .5)
    }

}