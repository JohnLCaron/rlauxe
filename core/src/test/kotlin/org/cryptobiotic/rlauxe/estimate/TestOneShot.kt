package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test

class TestOneShot {

    @Test
    fun testOneShot() {
        val topdir = "$testdataDir/cases/corla/consistent"
        val record = AuditRecord.read(topdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val writeOneshot = Publisher(topdir).privateOneshotFile()
        val oneshot = OneShotAudit(topdir)
        oneshot.run(null, writeOneshot, show=true)

        val oneshotNmvrs = record.readOneShotMvrs()
        println(oneshotNmvrs)
    }

    // @Test dont use for unit tests TODO WHY NOT?
    fun testEstimatePollingAudit() {
        val topdir = "/home/stormy/rla/cases/corla/polling"
        val record = AuditRecord.read(topdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)
        val mvrManager = PersistedMvrManager(record)

        val roundIdx = 4
        val auditRound = record.rounds[roundIdx-1]
        val estaudit = EstimateAudit(
            topdir,
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