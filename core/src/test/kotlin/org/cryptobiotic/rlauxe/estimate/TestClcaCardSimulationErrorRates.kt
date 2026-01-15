package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import org.cryptobiotic.rlauxe.workflow.ClcaCardSimulatedErrorRates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestClcaCardSimulationErrorRates {

    @Test
    fun testClcaCardSimulationErrorRates() {
        val N = 30000
        val fuzzPcts = listOf(0.0001, 0.001, .005, .01, .02, .05)
        val margins = listOf(.015, .03, .05)
        println("N=$N")

        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)
            val cards = cvrs.map { AuditableCard.fromCvr(it, 0, 0L) }
            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestWithAssertions(contest).addStandardAssertions()
            val compareAssorter = contestUA.clcaAssertions.first().cassorter

            println("margin=$margin")
            for (fuzzPct in fuzzPcts) {
                println(" fuzzPct=$fuzzPct")
                val errorRates = ClcaErrorTable.getErrorRates(contest.ncandidates, fuzzPct) // TODO do better

                val sampler = ClcaCardSimulatedErrorRates(
                    cards,
                    contestUA.contest as Contest,
                    compareAssorter,
                    errorRates,
                )

                println(" errorRates = ${sampler.errorRates}")
                println(sampler.showFlips())

                testLimits(sampler, N, compareAssorter.upperBound())

                val errs = sampler.errorRates
                assertEquals(sampler.needToChange(0), sampler.flippedVotesP2o)
                assertEquals(sampler.needToChange(1), sampler.flippedVotesP1o)
                assertEquals(sampler.needToChange(2), sampler.flippedVotesP1u)
                assertEquals(sampler.needToChange(3), sampler.flippedVotesP2u)

                /* val noerror = compareAssorter.noerror()
                val p = 1.0 - errs.p1o - errs.p2o - errs.p1u - errs.p2u
                assertEquals(errs.p1o * N, countAssortValues(sampler, N, noerror / 2).toDouble())
                assertEquals(errs.p2o * N, countAssortValues(sampler, N, 0.0).toDouble())
                assertEquals(p * N, countAssortValues(sampler, N, noerror).toDouble())
                assertEquals(errs.p1u * N, countAssortValues(sampler, N, 3 * noerror / 2).toDouble())
                assertEquals(errs.p2u * N, countAssortValues(sampler, N, 2 * noerror).toDouble()) */
            }
        }
    }

    @Test
    fun testClcaSamplerErrorRates() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<Cvr> = makeCvrsByExactMean(N, theta)
            val cards = cvrs.map { AuditableCard.fromCvr(it, 0, 0L) }

            val contest = makeContestsFromCvrs(cvrs).first()
            val contestUA = ContestWithAssertions(contest).addStandardAssertions()
            val compareAssorter = contestUA.clcaAssertions.first().cassorter

            runClcaSimulation(cards, contestUA, compareAssorter)
        }
    }

    fun runClcaSimulation(cards: List<AuditableCard>, contestUA: ContestWithAssertions, assorter: ClcaAssorter) {
        println("\n${assorter.assorter.desc()}")

        val phantomRate = contestUA.phantomRate()
        val errorRates = PluralityErrorRates(0.0, phantomRate, 0.0, 0.0)
        val sampler = ClcaCardSimulatedErrorRates(cards, contestUA.contest, assorter, errorRates)
        sampler.reset()

        val orgCvrs = cards.map { assorter.assorter.assort(it.cvr()) }.average()
        val sampleCvrs = sampler.cvrs.map { assorter.assorter.assort(it.cvr()) }.average()
        val sampleMvrs = sampler.mvrs.map { assorter.assorter.assort(it.cvr()) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cards.map { assorter.bassort(it.cvr(), it.cvr()) }.average()

        val tracker = PluralityErrorTracker(assorter.noerror())
        while (sampler.hasNext()) { tracker.addSample(sampler.next()) }
        println(" bassort expectedNoerror=${df(assorter.noerror())} noerror=${df(before)} sampleMean = ${df(tracker.mean())}")
        assertTrue( tracker.mean() > .5)
    }

}