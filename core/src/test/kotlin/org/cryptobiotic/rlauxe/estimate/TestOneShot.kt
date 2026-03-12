package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import kotlin.test.Test

class TestOneShot {

    @Test
    fun testOneShot() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"
        val record = AuditRecord.readFrom(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val writeOneshot = Publisher(auditdir).privateOneshotFile()
        val oneshot = OneShotAudit(auditdir)
        oneshot.run(listOf(14), writeOneshot)

        val oneshotNmvrs = record.readOneShotMvrs()
        println(oneshotNmvrs)
    }

    @Test
    fun testEstimateAudit() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"
        val record = AuditRecord.readFrom(auditdir)
        if (record == null) throw RuntimeException("record is null")
        require (record is AuditRecord)

        val config = record.config
        val cardManifest = record.readCardManifest()
        val cardPools = record.readCardPools()
        val roundIdx = 1
        val auditRound = record.rounds[roundIdx-1]
        val estaudit = EstimateAudit(config, roundIdx, auditRound.contestRounds, cardPools, cardManifest)
        estaudit.run(contestOnly = 28)
    }

}