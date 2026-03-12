package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class TestOneShot {

    @Test
    fun testSf24oa() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"
        val record = AuditRecord.readFrom(auditdir)
        if (record == null) throw RuntimeException("record is null")

        val oneshot = OneShotOA(auditdir)
        oneshot.run(listOf(14, 23, 28))
    }

}