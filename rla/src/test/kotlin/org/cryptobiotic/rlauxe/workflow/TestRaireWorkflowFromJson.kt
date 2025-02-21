package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestRaireWorkflowFromJson {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L, nsimEst=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=false, seed = 123568667890L, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val stopwatch = Stopwatch()

         // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallotsCsv(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val ncs = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), it.ncvrs + 2) }
        val nps = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), 2) }
        val raireResults = readRaireResultsJson("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
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

        val nassertions = raireResults.contests.sumOf { it.rassertions.size }
        val workflow = ClcaWorkflow(auditConfig, emptyList(), raireResults.contests, cvrs)
        runComparisonWorkflowR(workflow, cvrs, nassertions)
    }

}

fun runComparisonWorkflowR(workflow: ClcaWorkflow, testMvrs: List<Cvr>, nassertions: Int) {
    val stopwatch = Stopwatch()

    val previousSamples = mutableSetOf<Int>()
    var rounds = mutableListOf<Round>()
    var roundIdx = 1

    var done = false
    while (!done) {
        val roundStopwatch = Stopwatch()
        println("---------------------------")
        val indices = workflow.chooseSamples(roundIdx, show=false)
        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        rounds.add(currRound)
        previousSamples.addAll(indices)
        println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${roundStopwatch.elapsed(
            TimeUnit.MILLISECONDS)} ms\n")

        val sampledMvrs = indices.map { testMvrs[it] }
        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        roundIdx++
    }

    if (rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    }
    println("that took ${stopwatch.tookPer(nassertions, "Assertions")}")
}