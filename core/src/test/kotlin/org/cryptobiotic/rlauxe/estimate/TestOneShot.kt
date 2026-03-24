package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestOneShot {

    @Test
    fun testOneShot() {
        val auditdir = "$testdataDir/persist/persistWorkflow/oneauditProblem2/audit"
        val record = AuditRecord.readFrom(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val writeOneshot = Publisher(auditdir).privateOneshotFile()
        val oneshot = OneShotAudit(auditdir)
        oneshot.run(listOf(), writeOneshot, show=true)

        val oneshotNmvrs = record.readOneShotMvrs()
        println(oneshotNmvrs)
    }

    // @Test dont use for unit tests
    fun testEstimatePollingAudit() {
        val auditdir = "/home/stormy/rla/cases/corla/polling/audit"
        val record = AuditRecord.readFrom(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val roundIdx = 4
        val auditRound = record.rounds[roundIdx-1]
        val estaudit = EstimateAudit(
            record.config,
            roundIdx,
            auditRound.contestRounds,
            pools = record.readCardPools(),
            batches = record.readBatches(),
            cardManifest = record.readSortedManifest(),
        )
        estaudit.run()
    }

}