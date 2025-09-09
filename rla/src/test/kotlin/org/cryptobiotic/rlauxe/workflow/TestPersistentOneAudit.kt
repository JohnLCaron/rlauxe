package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Prng
import java.nio.file.Path
import kotlin.test.Test

class TestPersistentOneAudit {
    val auditDir = "/home/stormy/rla/persist/testPersistentOneAudit"
    // val topdir = kotlin.io.path.createTempDirectory().toString()

    @Test
    fun testPersistentWorkflowClca() {
        clearDirectory(Path.of(auditDir))

        val fuzzMvrPct = .01
        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        val N = 5000
        val (contestOA, testCvrs) = makeOneContestUA(N+100, N-100, cvrPercent = .95, undervotePercent=.0, phantomPercent = .0)

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testMvrs = testCvrs

        // the order of the cvrs cannot be changed once the audit is initialized.
        val prng = Prng(auditConfig.seed)
        val cvrsUA = testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
        writeAuditableCardCsvFile(cvrsUA, publisher.cardsCsvFile())
        println("write sortedCards to ${publisher.cardsCsvFile()} ")

        // put the testMvrs in the same sorted order as the cvrsUA
        val testMvrsUA = cvrsUA.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }

        // val mvrManagerTest = MvrManagerTestFromRecord(testCvrs, testMvrs, auditConfig.seed) this does the mvrs manipulations internally
        val mvrManager = MvrManagerFromRecord(auditDir)
        var oaWorkflow = OneAudit(auditConfig, listOf(contestOA), mvrManager)

        // these checks may modify the contest status
        checkContestsCorrectlyFormed(auditConfig, oaWorkflow.contestsUA())
        checkContestsWithCvrs(oaWorkflow.contestsUA(), CvrIteratorAdapter(readCardsCsvIterator(publisher.cardsCsvFile())))

        writeContestsJsonFile(oaWorkflow.contestsUA(), publisher.contestsFile())
        println("write writeContestsJsonFile to ${publisher.contestsFile()} ")

        var round = 1
        var done = false
        var workflow : RlauxAuditIF = oaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, publisher)
            workflow = PersistentAudit(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }
}
