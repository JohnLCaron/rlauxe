package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.readRaireBallots
import org.cryptobiotic.rlauxe.raire.readRaireResults
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.ComparisonErrorRates
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
            val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs)  // contestId -> candId, vote count (or rank?)

            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs, votes[contest.id]!!)
            val compareAssorter = contestUA.comparisonAssertions.first().cassorter

            val sampler = ComparisonSamplerSimulation(cvrs,
                contestUA,
                compareAssorter,
                ComparisonErrorRates.standard,
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
            val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs)  // contestId -> candId, vote count (or rank?)

            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs, votes[contest.id]!!)
            val compareAssorter = contestUA.comparisonAssertions.first().cassorter

            run(cvrs, contestUA, compareAssorter)
        }
    }

    @Test
    fun testComparisonSamplerForRaire() {
        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val cvrs = raireCvrs.cvrs
        val cvrsUA = cvrs.map { CvrUnderAudit(it) }

        val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
        val raireResults =
            readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
                .import(ncs)
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        contestUA.makeComparisonAssertions(cvrsUA)

        contestUA.comparisonAssertions.forEach { assert ->
            run(cvrs, contestUA, assert.cassorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, assorter: ComparisonAssorter) {
        println("\n${assorter.assorter.desc()}")

        val sampler = ComparisonSamplerSimulation(cvrs, contestUA, assorter, ComparisonErrorRates.standard)

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