package org.cryptobiotic.rlauxe.persist.raire

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import java.util.concurrent.TimeUnit

class TestRaireWorkflowFromJson {

    // @Test TODO failing
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L, nsimEst=10))
    }

    // @Test TODO failing
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=false, seed = 123568667890L, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val stopwatch = Stopwatch()

        // This single contest cvr file is the only real cvr data in SHANGRLA
        // not sure who generates it, is it raire specific?
        val cvrFile = "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallotsCsv(cvrFile)
        val rcontests = raireCvrs.contests
        val testCvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val ncs = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), it.ncvrs + 2) }
        val nps = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), 2) }
        val raireResults = readRaireResultsJson("src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
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

        val ballotCards = MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed)
        val workflow = ClcaAuditTester(auditConfig, emptyList(), raireResults.contests, ballotCards)

        runComparisonWorkflowR(workflow, Closer(ballotCards.sortedCards.iterator()), nassertions)
    }

}

fun runComparisonWorkflowR(workflow: ClcaAuditTester, sortedMvrs: CloseableIterator<AuditableCard>, nassertions: Int) {
    val stopwatch = Stopwatch()

    var done = false
    while (!done) {
        val roundStopwatch = Stopwatch()
        println("---------------------------")
        val currRound = workflow.startNewRound()
        println("${currRound.roundIdx} choose ${currRound.samplePrns.size} samples, new=${currRound.newmvrs} " +
                "took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        // TODO addMvrs ?
        val sampledMvrus = findSamples(currRound.samplePrns, sortedMvrs) // TODO use IteratorCvrsCsvFile?
        done = workflow.runAuditRound(currRound)
        println("runAudit ${currRound.roundIdx} done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
    }

    /*
    if (rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    } */

    println("runComparisonWorkflowR took ${stopwatch.tookPer(nassertions, "Assertions")}")
}