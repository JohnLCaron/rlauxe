package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord.runVerifyAuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {
    val show = true
    val useLocal = true

    @Test
    fun testRunVerifyClca() {
        val auditdir = if (useLocal) "../core/src/test/data/testRunCli/clca/audit"
            else "$testdataDir/persist/testRunCli/clca/audit"
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
        val auditdir = if (useLocal) "../core/src/test/data/testRunCli/oneaudit/audit"
            else "$testdataDir/persist/testRunCli/oneaudit/audit"
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
        val auditdir = if (useLocal) "../core/src/test/data/testRunCli/polling/audit"
            else "$testdataDir/persist/testRunCli/polling/audit"
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
        val auditdir = if (useLocal) "../core/src/test/data/testRunCli/raire/audit"
            else "$testdataDir/persist/testRunCli/raire/audit"
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