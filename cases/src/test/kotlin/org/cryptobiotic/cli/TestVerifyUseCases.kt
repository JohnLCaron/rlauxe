package org.cryptobiotic.cli

import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

class TestVerifyUseCases {
    val show = false
    @Test
    fun testRunVerifyCorlaOneAudit() {
        val auditdir = "/home/stormy/rla/cases/corla/oneaudit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifyCorlaOneAuditNew() {
        val auditdir = "/home/stormy/rla/cases/corla/oneauditnew"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifyCorlaClca() {
        val auditdir = "/home/stormy/rla/cases/corla/clca"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifySf2024() {
        val auditdir = "/home/stormy/rla/cases/sf2024/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifySf2024oa() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifySf2024oaNew() {
        val auditdir = "/home/stormy/rla/cases/sf2024oanew/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifySf2024oaContest1() {
        val auditdir = "/home/stormy/rla/cases/sf2024oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.fail()){
            fail()
        }
    }

    @Test
    fun testRunVerifyBoulder24oanew() {
        val auditdir = "/home/stormy/rla/cases/boulder24oanew/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifyBoulderClcanew() {
        val auditdir = "/home/stormy/rla/cases/boulder24clcanew/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }

    @Test
    fun testRunVerifyBoulder24oaContest16() {
        val auditdir = "/home/stormy/rla/cases/boulder24oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 16, show = show)
        println()
        print(results)
        if (results.fail()) fail()
    }
}