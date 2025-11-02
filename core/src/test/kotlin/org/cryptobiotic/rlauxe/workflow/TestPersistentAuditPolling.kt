package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Path

class TestPersistentAuditPolling {
    // val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowPolling"
    val auditDir = kotlin.io.path.createTempDirectory().toString()

    // @Test
    fun testPersistentAuditPolling() {
        clearDirectory(Path.of(auditDir))

        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyle=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, hasStyle=true, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach{ println("  $it")}
        println()

        // We use the simulated cvrs as the mvrs. TODO assuming there are no errors for now
        val (testMvrs, cardLocations) = testData.makeCvrsAndBallots()
        // val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        // the order of the cardLocations cannot be changed once the audit is initialized.
        val prng = Prng(auditConfig.seed)
        val cards = cardLocations.mapIndexed { idx, it -> AuditableCard.fromCardLocation(it, idx, prng.next()) }.sortedBy { it.prn }
        writeAuditableCardCsvFile(cards, publisher.sortedCardsFile())
        println("write sortedCards to ${publisher.sortedCardsFile()} ")

        // put the testMvrs in the same sorted order as the cards
        val testMvrsUA = cards.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }

        val mvrManager = MvrManagerFromRecord(auditDir)
        val pollingWorkflow = WorkflowTesterPolling(auditConfig, contests, mvrManager)

        // these checks may modify the contest status
        val verifier = VerifyContests(auditDir)
        val resultsv = verifier.verify(pollingWorkflow.contestsUA(), false)
        println(resultsv.toString())
        assertFalse(resultsv.hasErrors)

        writeContestsJsonFile(pollingWorkflow.contestsUA(), publisher.contestsFile())
        println("write writeContestsJsonFile to ${publisher.contestsFile()} ")

        var round = 1
        var done = false
        var workflow : AuditWorkflow = pollingWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, publisher)
            workflow = PersistedWorkflow(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }
}