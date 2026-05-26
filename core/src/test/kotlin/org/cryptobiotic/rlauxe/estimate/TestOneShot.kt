package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test

class TestOneShot {

    @Test
    fun testOneShot() {
        val auditdir = "$testdataDir/cases/corla/consistent/audit"
        val record = AuditRecord.read(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val writeOneshot = Publisher(auditdir).privateOneshotFile()
        val oneshot = OneShotAudit(auditdir)
        oneshot.run(null, writeOneshot, show=true)

        val oneshotNmvrs = record.readOneShotMvrs()
        println(oneshotNmvrs)
    }

    // @Test dont use for unit tests TODO WHY NOT?
    fun testEstimatePollingAudit() {
        val auditdir = "/home/stormy/rla/cases/corla/polling/audit"
        val record = AuditRecord.read(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)
        val mvrManager = PersistedMvrManager(record)

        val roundIdx = 4
        val auditRound = record.rounds[roundIdx-1]
        val estaudit = EstimateAudit(
            auditdir,
            record.config,
            roundIdx,
            auditRound.contestRounds,
            pools = mvrManager.pools(),
            styles = mvrManager.styles(),
            sortedManifest = mvrManager.sortedManifest(),
        )
        estaudit.run()
    }

}