package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord.runVerifyAuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {

    val show = true

    @Test
    fun testRunVerifyClca() {
        // val auditdir = "../core/src/test/data/testRunCli/clca/audit"
        val auditdir = "$testdataDir/persist/testRunCli/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyOA() {
        val auditdir = "../core/src/test/data/testRunCli/oneaudit/audit"
        // val auditdir = "$testdataDir/persist/testCliRoundOneAudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyPolling() {
        val auditdir = "../core/src/test/data/testRunCli/polling/audit"
        // val auditdir = "$testdataDir/persist/testCliRoundPolling/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyRaire() {
        val auditdir = "../core/src/test/data/testRunCli/raire/audit"
        // val auditdir = "$testdataDir/persist/testCliRoundRaire/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }
}