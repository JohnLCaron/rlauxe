package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestRaireWorkflowFromJson {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, ntrials=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 123568667890L, ntrials=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val stopwatch = Stopwatch()

         // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val ncs = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), it.ncvrs + 2) }
        val nps = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), 2) }
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

        val nassertions = raireResults.contests.sumOf { it.assertions.size }
        val workflow = ComparisonWorkflow(auditConfig, emptyList(), raireResults.contests, cvrs)
        runComparisonWorkflowR(workflow, cvrs, nassertions)
    }

}

fun runComparisonWorkflowR(workflow: ComparisonWorkflow, testMvrs: List<Cvr>, nassertions: Int) {
    val stopwatch = Stopwatch()

    var prevMvrs = emptyList<Cvr>()
    val previousSamples = mutableSetOf<Int>()
    var rounds = mutableListOf<Round>()
    var roundIdx = 1

    var done = false
    while (!done) {
        val roundStopwatch = Stopwatch()
        println("---------------------------")
        val indices = workflow.chooseSamples(prevMvrs, roundIdx, show=false)
        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        rounds.add(currRound)
        previousSamples.addAll(indices)
        println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${roundStopwatch.elapsed(
            TimeUnit.MILLISECONDS)} ms\n")

        val sampledMvrs = indices.map { testMvrs[it] }
        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        prevMvrs = sampledMvrs
        roundIdx++
    }

    rounds.forEach { println(it) }
    workflow.showResults()
    println("that took ${stopwatch.tookPer(nassertions, "Assertions")}")
}