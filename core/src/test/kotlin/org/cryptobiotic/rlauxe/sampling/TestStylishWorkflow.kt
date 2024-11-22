package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestStylishWorkflow {

    @Test
    fun testWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.50)

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
}

// seems suspiciouly higher (2x) than the audit

// simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/18 alreadyElim=[16, 17] margin=0.0289 samplesEst=275 samplesNeeded=0 took 3624 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=18/17 alreadyElim=[15, 16] margin=0.0458 samplesEst=161 samplesNeeded=0 took 6800 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=17/16 alreadyElim=[15] margin=0.0581 samplesEst=113 samplesNeeded=0 took 9913 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/17 alreadyElim=[16] margin=0.0806 samplesEst=83 samplesNeeded=0 took 13127 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=18/16 alreadyElim=[15, 17] margin=0.0830 samplesEst=79 samplesNeeded=0 took 16270 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/17 alreadyElim=[16, 18] margin=0.1095 samplesEst=54 samplesNeeded=0 took 19381 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/16 alreadyElim=[17] margin=0.1355 samplesEst=44 samplesNeeded=0 took 22699 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/16 alreadyElim=[17, 18] margin=0.1365 samplesEst=43 samplesNeeded=0 took 25778 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/16 alreadyElim=[] margin=0.1563 samplesEst=38 samplesNeeded=0 took 28849 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=18/16 alreadyElim=[15] margin=0.1488 samplesEst=40 samplesNeeded=0 took 32159 ms
//simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/16 alreadyElim=[18] margin=0.1667 samplesEst=35 samplesNeeded=0 took 35326 ms
// computeSize=276 consistentSamplingSize= 275
//0 chooseSamples 275 took 36018
//
// simulateSampleSizes at 50.0% quantile: RaireAssorter winner/loser=15/18 alreadyElim=[16, 17] margin=0.0289 samplesEst=275 samplesNeeded=0 took 3624 ms
// runOneAssertionAudit: RaireAssorter winner/loser=15/18 alreadyElim=[16, 17] margin=0.0289 samplesEst=275 samplesNeeded=239, status = StatRejectNull
// simulateSampleSize: RaireAssorter winner/loser=15/18 alreadyElim=[16, 17] margin=0.02892364757061272 50.0% quantile = 247 actual= 239 0.36%
//
//runOneAssertionAudit: RaireAssorter winner/loser=18/17 alreadyElim=[15, 16] margin=0.0458 samplesEst=161 samplesNeeded=113, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=18/17 alreadyElim=[15, 16] margin=0.04579236612074755 50.0% quantile = 143 actual= 113 0.12%
//
//runOneAssertionAudit: RaireAssorter winner/loser=17/16 alreadyElim=[15] margin=0.0581 samplesEst=113 samplesNeeded=89, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=17/16 alreadyElim=[15] margin=0.058079120699289 50.0% quantile = 113 actual= 89 0.04%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/17 alreadyElim=[16] margin=0.0806 samplesEst=83 samplesNeeded=57, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/17 alreadyElim=[16] margin=0.08064120222006799 50.0% quantile = 81 actual= 57 0.01%
//
//runOneAssertionAudit: RaireAssorter winner/loser=18/16 alreadyElim=[15, 17] margin=0.0830 samplesEst=79 samplesNeeded=72, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=18/16 alreadyElim=[15, 17] margin=0.08300036819355383 50.0% quantile = 72 actual= 72 0.4%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/17 alreadyElim=[16, 18] margin=0.1095 samplesEst=54 samplesNeeded=42, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/17 alreadyElim=[16, 18] margin=0.10951712099930444 50.0% quantile = 54 actual= 42 0.03%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/16 alreadyElim=[17] margin=0.1355 samplesEst=44 samplesNeeded=42, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/16 alreadyElim=[17] margin=0.135481583504947 50.0% quantile = 44 actual= 42 0.18%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/16 alreadyElim=[17, 18] margin=0.1365 samplesEst=43 samplesNeeded=42, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/16 alreadyElim=[17, 18] margin=0.13652479851633892 50.0% quantile = 43 actual= 42 0.25%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/16 alreadyElim=[] margin=0.1563 samplesEst=38 samplesNeeded=38, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/16 alreadyElim=[] margin=0.15626406294743345 50.0% quantile = 38 actual= 38 0.23%
//
//runOneAssertionAudit: RaireAssorter winner/loser=18/16 alreadyElim=[15] margin=0.1488 samplesEst=40 samplesNeeded=40, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=18/16 alreadyElim=[15] margin=0.14875018750598623 50.0% quantile = 40 actual= 40 0.18%
//
//runOneAssertionAudit: RaireAssorter winner/loser=15/16 alreadyElim=[18] margin=0.1667 samplesEst=35 samplesNeeded=35, status = StatRejectNull
//simulateSampleSize: RaireAssorter winner/loser=15/16 alreadyElim=[18] margin=0.16666893946624262 50.0% quantile = 35 actual= 35 0.19%
