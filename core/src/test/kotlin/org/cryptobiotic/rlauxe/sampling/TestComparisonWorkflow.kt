package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestComparisonWorkflow {

    @Test
    fun testComparisonWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.80)

        val testData = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = testData.makeContests()
        val cvrs = testData.makeCvrsFromContests()

        val workflow = StylishWorkflow(contests, emptyList(), auditConfig, cvrs)
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        // form simulated mvrs. TODO kinda dicey because these are intended for a single assorter
        val contestUA = workflow.contestsUA.first()
        val assorter = contestUA.comparisonAssertions.first().assorter // take the one with the smallest margin??
        // dont permute
        // also using default error rates
        val sampler = ComparisonSamplerSimulation(workflow.cvrsUA, contestUA, assorter,
            p1=auditConfig.p1, p2=auditConfig.p2, p3=auditConfig.p3, p4=auditConfig.p4, )
        println(sampler.showFlips())

        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = sampler.mvrs.zip(sampler.cvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }
        var count = 0
        cvrPairs.forEach { (mvr, cvr) ->
            if (mvr.votes != cvr.votes) {
                count++
            }
        }
        println("diff = $count out of ${cvrPairs.size} = ${df(count.toDouble()/cvrPairs.size)}\n")
/*
        val samples = PrevSamplesWithRates(assorter.noerror)
        cvrPairs.forEach { (mvr, cvr) -> samples.addSample(assorter.bassort(mvr,cvr)) }
        println("samplingErrors= ${samples.samplingErrors()}")
 */

        var done = false
        var prevMvrs = emptyList<CvrIF>()
        var round = 0
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, round)
            println("$round chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}\n")
            // println("$round samples=${indices}")
            stopwatch.start()

            val sampledMvrs = indices.map { sampler.mvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }

    @Test
    fun testRaireWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.80)

         // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json").import()

        // check consistencey
        raireResults.contests.forEach { rrc ->
            val rc = rcontests.find { it.contestNumber == rrc.id }
            requireNotNull(rc)
            require(rc.candidates == rrc.candidates)
            rrc.ncvrs = rc.ncvrs
            rrc.Nc = rc.ncvrs
        }

        val workflow = StylishWorkflow(emptyList(), raireResults.contests, auditConfig, cvrs)
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        // form simulated mvrs. TODO kinda dicey because these are intended for a single assorter
        val contestUA = workflow.contestsUA.first()
        val assorter = contestUA.comparisonAssertions.first().assorter // take the one with the smallest margin??
        // dont permute
        val sampler = ComparisonSamplerSimulation(workflow.cvrsUA, contestUA, assorter,
            p1=auditConfig.p1, p2=auditConfig.p2, p3=auditConfig.p3, p4=auditConfig.p4, )
        println(sampler.showFlips())

        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = sampler.mvrs.zip(sampler.cvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }
        var count = 0
        cvrPairs.forEach { (mvr, cvr) ->
            if (mvr.votes != cvr.votes) {
                count++
            }
        }
        println("diff = $count out of ${cvrPairs.size} = ${df(count.toDouble()/cvrPairs.size)}\n")

        val samples = PrevSamplesWithRates(assorter.noerror)
        cvrPairs.forEach { (mvr, cvr) -> samples.addSample(assorter.bassort(mvr,cvr)) }
        println("samplingErrors= ${samples.samplingErrors()}")

        var done = false
        var prevMvrs = emptyList<CvrIF>()
        var round = 0
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, round)
            println("$round chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}\n")
            // println("$round samples=${indices}")
            stopwatch.start()

            val sampledMvrs = indices.map { sampler.mvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }

    /*
    @Test
    fun testPollingWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.50)

        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json").import()

        // check consistencey
        raireResults.contests.forEach { rrc ->
            val rc = rcontests.find { it.contestNumber == rrc.id }
            requireNotNull(rc)
            require(rc.candidates == rrc.candidates)
            rrc.ncvrs = rc.ncvrs
            rrc.Nc = rc.ncvrs
        }

        val ballots = test.makeBallots()

        val workflow = PollingWorkflow(auditConfig, raireResults.contests, ballots)
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        var done = false
        var prevMvrs = emptyList<CvrIF>()
        var round = 0
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, round)
            println("$round chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}\n")
            // println("$round samples=${indices}")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testCvrs[it]
            }

            done = workflow.runAudit(indices, sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }

     */
}