package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestRaireWorkflow {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct = null))
    }

    @Test
    fun testRaireComparisonNoStyle() {
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
        val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
        val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
            .import(ncs, nps)
        print(raireResults.show())

        // check consistencey
        raireResults.contests.forEach { rrc ->
            val rc = rcontests.find { it.contestNumber == rrc.id }
            requireNotNull(rc)
            require(rc.candidates == rrc.candidates)
            // TODO rrc.ncvrs = rc.ncvrs
            // TODO rrc.Nc = rc.ncvrs
        }

        val workflow = ComparisonWorkflow(auditConfig, emptyList(), raireResults.contests, cvrs)

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        // cant make fuzzed cvrs from raire as it stands
        // val fuzzedMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, cvrs, auditConfig.fuzzPct!!)

        var prevMvrs = emptyList<Cvr>()
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
    }

}