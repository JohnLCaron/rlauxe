package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.makeStandardContest
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.readRaireBallots
import org.cryptobiotic.rlauxe.raire.readRaireResults
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestComparisonSamplerSimulation {

    @Test
    fun testComparisonSamplerPlurality() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<CvrIF> = makeCvrsByExactMean(N, theta)
            val cvrsUA = cvrs.map { CvrUnderAudit(it as Cvr, false) }
            val contest = makeStandardContest()
            val contestUA = ContestUnderAudit(contest)
            val compareAssorter = makeStandardComparisonAssorter(theta)

            val sampler = ComparisonSamplerSimulation(cvrsUA, contestUA, compareAssorter)
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
            val cvrs: List<CvrIF> = makeCvrsByExactMean(N, theta)
            val cvrsUA = cvrs.map { CvrUnderAudit(it as Cvr, false) }
            val contest = makeStandardContest()
            val contestUA = ContestUnderAudit(contest)
            val compareAssorter = makeStandardComparisonAssorter(theta)
            run(cvrsUA, contestUA, compareAssorter)
        }
    }

    @Test
    fun testComparisonSamplerForRaire() {
        val raireResults =
            readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json").import()
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val cvrs = raireCvrs.cvrs
        val cvrsUA = cvrs.map { CvrUnderAudit(it, false) }

        contestUA.makeComparisonAssertions(cvrsUA)

        contestUA.comparisonAssertions.forEach { assert ->
            run(cvrsUA, contestUA, assert.assorter)
        }
    }

    fun run(cvrsUA: List<CvrUnderAudit>, contestUA: ContestUnderAudit, assorter: ComparisonAssorter) {
        println("\n${assorter.assorter.desc()}")

        val sampler = ComparisonSamplerSimulation(cvrsUA, contestUA, assorter)

        val orgCvrs = cvrsUA.map { assorter.assorter.assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { assorter.assorter.assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrsUA.map { assorter.bassort(it, it) }.average()
        sampler.reset()
        var welford = Welford()
        repeat(cvrsUA.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(assorter.noerror)} noerror=${df(before)} sampleMean = ${df(welford.mean)}")
    }

}