package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord.runVerifyAuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {
    val show = true
    val useLocal = false

    @Test
    fun testRunVerifyClca() {
        val topdir = if (useLocal) "../core/src/test/data/testRunCli/clca"
            else "$testdataDir/persist/testRunCli/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(topdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyOA() {
        val topdir = if (useLocal) "../core/src/test/data/testRunCli/oneaudit"
            else "$testdataDir/persist/testRunCli/oneaudit"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(topdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyPolling() {
        val topdir = if (useLocal) "../core/src/test/data/testRunCli/polling"
            else "$testdataDir/persist/testRunCli/polling"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(topdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testRunVerifyRaire() {
        val topdir = if (useLocal) "../core/src/test/data/testRunCli/raire"
            else "$testdataDir/persist/testRunCli/raire"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(topdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }
}