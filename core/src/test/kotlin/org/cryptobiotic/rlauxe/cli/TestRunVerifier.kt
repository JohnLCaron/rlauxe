package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {

    val show = true

    @Test
    fun testRunVerifyClca1() {
        val auditdir = "../core/src/test/data/workflow/testCliRoundClca"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundClca"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyOA() {
        val auditdir = "../core/src/test/data/workflow/testCliRoundOneAudit/audit"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundClca"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }


    @Test
    fun testRunVerifyPolling() {
        val auditdir = "../core/src/test/data/workflow/testCliRoundPolling/audit"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundClca"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}