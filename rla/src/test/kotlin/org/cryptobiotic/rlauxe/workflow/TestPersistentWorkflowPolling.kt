package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.Publisher
import kotlin.test.Test

class TestPersistentWorkflowPolling {
    // val topdir = "/home/stormy/temp/persist/testPersistentWorkflowPolling"
    val topdir = kotlin.io.path.createTempDirectory().toString()

    @Test
    fun testPersistentWorkflowPolling() {
        val fuzzMvrs = .01
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach{ println("  $it")}
        println()

        val (testCvrs, ballots) = testData.makeCvrsAndBallots(auditConfig.hasStyles)
        val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)
        val mvrManager = MvrManagerPollingForTesting(ballots, testMvrs, auditConfig.seed)

        // save the sorted testMvrs
        writeAuditableCardCsvFile(mvrManager.ballotsUA, publish.cardsCsvFile())
        println("   writeAuditableCardCsvFile ${publish.cardsCsvFile()}")

        val pollingWorkflow = PollingAudit(auditConfig, contests, mvrManager)
        writeContestsJsonFile(pollingWorkflow.contestsUA(), publish.contestsFile())

        var round = 1
        var done = false
        var workflow : RlauxAuditIF = pollingWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, mvrManager.mvrsUA, publish)
            workflow = PersistentAudit(topdir)
            round++
        }
    }
}