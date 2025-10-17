package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test
import kotlin.test.fail

class TestRunVerifyContests {

    val show = false

    @Test
    fun testRunVerifyClca1() {
        val auditdir = "../core/src/test/data/workflow/testCliRoundClca"
        // val auditdir = "/home/stormy/rla/persist/testCliRoundClca"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }
}