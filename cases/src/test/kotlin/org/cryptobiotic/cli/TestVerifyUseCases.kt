package org.cryptobiotic.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

class TestVerifyUseCases {

    @Test
    fun testRunVerifyCorlaOneAudit() {
        val auditdir = "/home/stormy/rla/cases/corla/oneaudit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifyCorlaClca() {
        val auditdir = "/home/stormy/rla/cases/corla/clca"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifySf2024() {
        val auditdir = "/home/stormy/rla/cases/sf2024/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifySf2024oa() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifySf2024oaContest1() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifyBoulder24oa() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(results)
        if (results.fail()) {
            fail()
        }
    }

    @Test
    fun testRunVerifyBoulder24oaContest16() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 16, true)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }
}