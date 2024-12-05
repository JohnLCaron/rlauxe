package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestRaireWorkflow {

    fun testComparisonWithStyleRepeat() {
        repeat(100) { testComparisonWithStyle() }
    }

   // @Test
    fun testComparisonNoStyleRepeat() {
        repeat(100) { testComparisonNoStyle() }
    }

    @Test
    fun testComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct = null))
    }

    @Test
    fun testComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 123568667890L, quantile=.80, fuzzPct = null))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val stopwatch = Stopwatch()

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

        val workflow = ComparisonWorkflow(emptyList(), raireResults.contests, auditConfig, cvrs)
        val contests = workflow.contestsUA.map { it.contest }

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        // cant make fuzzed cvrs from raire as it stands
        // val fuzzedMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, cvrs, auditConfig.fuzzPct!!)

        var prevMvrs = emptyList<CvrIF>()
        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples)
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map { cvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()

        /*
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        // form simulated mvrs. TODO kinda dicey because these are intended for a single assorter
        val contestUA = workflow.contestsUA.first()
        val assorter = contestUA.comparisonAssertions.first().cassorter // take the one with the smallest margin??
        // dont permute
        val sampler = ComparisonSamplerSimulation(workflow.cvrs, contestUA, assorter, ComparisonErrorRates.standard)
        println(sampler.showFlips())

        val cvrPairs: List<Pair<CvrIF, Cvr>> = sampler.mvrs.zip(sampler.cvrs)
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

        var prevMvrs = emptyList<CvrIF>()
        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples)
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            println("$roundIdx chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}\n")
            stopwatch.start()


            val sampledMvrs = indices.map { sampler.mvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            println("$roundIdx runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

         */
    }

}