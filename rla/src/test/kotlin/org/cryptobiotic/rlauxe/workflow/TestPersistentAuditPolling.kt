package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Prng
import java.nio.file.Path
import kotlin.test.Test

class TestPersistentAuditPolling {
    val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowPolling"
    // val auditDir = kotlin.io.path.createTempDirectory().toString()

    @Test
    fun testPersistentWorkflowPolling() {
        clearDirectory(Path.of(auditDir))

        val fuzzMvrPct = .01
        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach{ println("  $it")}
        println()

        // We use the simulated cvrs as the mvrs. TODO assuming there are no errors for now
        val (testMvrs, cardLocations) = testData.makeCvrsAndBallots(auditConfig.hasStyles)
        // val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        // the order of the cardLocations cannot be changed once the audit is initialized.
        val prng = Prng(auditConfig.seed)
        val cards = cardLocations.mapIndexed { idx, it -> AuditableCard.fromCardLocation(it, idx, prng.next()) }.sortedBy { it.prn }
        writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
        println("write sortedCards to ${publisher.cardsCsvFile()} ")

        // put the testMvrs in the same sorted order as the cards
        val testMvrsUA = cards.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }

        val mvrManager = MvrManagerFromRecord(auditDir)
        val pollingWorkflow = PollingAudit(auditConfig, contests, mvrManager)

        // these checks may modify the contest status
        checkContestsCorrectlyFormed(auditConfig, pollingWorkflow.contestsUA())
        checkContestsWithCvrs(pollingWorkflow.contestsUA(), CvrIteratorAdapter(readCardsCsvIterator(publisher.cardsCsvFile())))

        writeContestsJsonFile(pollingWorkflow.contestsUA(), publisher.contestsFile())
        println("write writeContestsJsonFile to ${publisher.contestsFile()} ")

        var round = 1
        var done = false
        var workflow : RlauxAuditIF = pollingWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, publisher)
            workflow = PersistentAudit(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }
}