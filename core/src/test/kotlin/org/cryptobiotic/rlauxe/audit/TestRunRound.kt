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
        val auditdir = "$testdataDir/cases/corla/clca/audit"
        val auditRecord = AuditRecord.read(auditdir)!!
        val lastRound = auditRecord.rounds.last()
        val removeContests = listOf(52, 8, 106)
        removeContests.forEach {
            val contestRound = lastRound.contestRounds.find { contest -> contest.id == it }!!
            contestRound.auditorWantNewMvrs = 0
        }

        resampleAndRun(auditdir, lastRound as AuditRound)
    }

}