package org.cryptobiotic.rlauxe.timing

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.cli.RunRlaRoundCli
import org.cryptobiotic.rlauxe.cli.StartAuditFirstRoundCli
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

// DO NOT put into Unit Tests
class TestRunRoundCli {


    @Test
    fun testRunRoundCli() {
        val auditdir = "${testdataDir}/cases/sf2024/clca/audit"

        RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--auditorMaxNewMvrs", "105",
                // "--onlyTask", "17-1/0",
            )
        )
    }

    @Test
    fun testRunAllRoundsCli() {
        val auditdir = "${testdataDir}/cases/corla/polling/audit"

        /* RunRlaRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "28-NEN 107/102",
            )
        ) */

        val stopRound = 7
        println("============================================================")
        var done = false
        var finalRound: AuditRoundIF? = null
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            if (lastRound != null) finalRound = lastRound
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
        }

        print("auditIsComplete = ${finalRound?.auditIsComplete}")
    }

    @Test
    fun testStartAuditFirstRound() {
        val auditdir = "/home/stormy/datadrive/rla/cases/corla/Colorado2020all"

        StartAuditFirstRoundCli.main(
            arrayOf(
                "-in", auditdir,
                // "--onlyTask", "14-37/35",
            )
        )
    }

    @Test
    fun testResampleAndSaveResults() {
        val auditdir = "${testdataDir}/cases/corla/consistent"
        resampleAndSaveResults(auditdir)
    }

    @Test
    fun testRemoveAndResample() {
        val auditdir = "${testdataDir}/cases/boulder24/oa/audit"
        val auditRecord = AuditRecord.read(auditdir)!!
        val lastRound = auditRecord.rounds.last()
        val removeContests = listOf(16,17)
        removeContests.forEach {
            val contestRound = lastRound.contestRounds.find { contest -> contest.id == it }!!
            contestRound.included = false
        }

        resampleAndSaveResults(auditRecord as AuditRecord, lastRound as AuditRound)
    }

}