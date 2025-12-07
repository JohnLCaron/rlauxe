package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord.runVerifyAuditRecord
import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {

    val show = true

    @Test
    fun testRunVerifyClca() {
        // val auditdir = "../core/src/test/data/testRunCli/clca/audit"
        val auditdir = "/home/stormy/rla/persist/testRunCli/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyOA() {
        val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundOneAudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyPolling() {
        val auditdir = "../core/src/test/data/testRunCli/polling/audit"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundPolling/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyRaire() {
        val auditdir = "../core/src/test/data/testRunCli/raire/audit"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundRaire/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}