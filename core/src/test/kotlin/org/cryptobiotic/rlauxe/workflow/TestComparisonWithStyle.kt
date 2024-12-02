package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.sampling.ComparisonSamplerSimulation
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestComparisonWithStyle {

    @Test
    fun testComparisonWorkflowRepeat() {
        repeat(100) { testComparisonWorkflow() }
    }

    @Test
    fun testComparisonWorkflow() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.80, fuzzPct = .05)

        // each contest has a specific margin between the top two vote getters.
        val testData = MultiContestTestData(20, 11, 10000)
        val contests: List<Contest> = testData.makeContests()
        println("Start testComparisonWorkflow")
        contests.forEach{ println(" $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes.
        val cvrs = testData.makeCvrsFromContests()

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val fuzzedMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, cvrs, auditConfig.fuzzPct!!)

        val workflow = ComparisonWithStyle(contests, emptyList(), auditConfig, cvrs)
        val stopwatch = Stopwatch()

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

            val sampledMvrs = indices.map { fuzzedMvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()
    }

    @Test
    fun testRaireWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, fuzzPct=null, quantile=.80)

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

        val workflow = ComparisonWithStyle(emptyList(), raireResults.contests, auditConfig, cvrs)
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        // form simulated mvrs. TODO kinda dicey because these are intended for a single assorter
        val contestUA = workflow.contestsUA.first()
        val assorter = contestUA.comparisonAssertions.first().assorter // take the one with the smallest margin??
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

            done = workflow.runAudit(indices, sampledMvrs)
            println("$roundIdx runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
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