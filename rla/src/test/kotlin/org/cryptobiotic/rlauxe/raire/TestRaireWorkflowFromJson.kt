package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaAudit
import org.cryptobiotic.rlauxe.audit.findSamples
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
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

        val ballotCards = StartTestBallotCardsClca(testCvrs, testCvrs, auditConfig.seed)
        val workflow = ClcaAudit(auditConfig, emptyList(), raireResults.contests, ballotCards)

        runComparisonWorkflowR(workflow, ballotCards.cvrsUA, nassertions)
    }

}

fun runComparisonWorkflowR(workflow: ClcaAudit, sortedMvrs: Iterable<CvrUnderAudit>, nassertions: Int) {
    val stopwatch = Stopwatch()

    var done = false
    while (!done) {
        val roundStopwatch = Stopwatch()
        println("---------------------------")
        val currRound = workflow.startNewRound()
        println("${currRound.roundIdx} choose ${currRound.sampleNumbers.size} samples, new=${currRound.newmvrs} " +
                "took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        // TODO addMvrs ?
        val sampledMvrus = findSamples(currRound.sampleNumbers, sortedMvrs)
        done = workflow.runAuditRound(currRound)
        println("runAudit ${currRound.roundIdx} done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
    }

    /*
    if (rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    } */

    println("that took ${stopwatch.tookPer(nassertions, "Assertions")}")
}