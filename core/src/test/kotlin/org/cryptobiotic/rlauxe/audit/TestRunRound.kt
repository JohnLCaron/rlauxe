package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.cli.StartAuditFirstRound
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestRunRound {

    @Test
    fun testStartAuditFirstRound() {
        val auditdir = "$testdataDir/cases/corla/polling3/audit"

        StartAuditFirstRound.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "14-37/35",
            )
        )
    }

    @Test
    fun testResampleAndRun() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"
        val auditRecord = AuditRecord.read(auditdir)!!
        val lastRound = auditRecord.rounds.last()
        val removeContests = listOf(16,17)
        removeContests.forEach {
            val contestRound = lastRound.contestRounds.find { contest -> contest.id == it }!!
            contestRound.included = false
        }

        resampleAndRun(auditdir, lastRound as AuditRound)
    }
}