package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

class TestVerifyUseCases {
    val show = false

    @Test
    fun testRunVerifyCorlaOneAudit() {
        val auditdir = "/home/stormy/rla/cases/corla/oneaudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaOneAuditContest() {
        val auditdir = "/home/stormy/rla/cases/corla/oneaudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaClca() {
        val auditdir = "/home/stormy/rla/cases/corla/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaClcaContest() {
        val auditdir = "/home/stormy/rla/cases/corla/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clca() {
        val auditdir = "/home/stormy/rla/cases/sf2024/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oa() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oaContest() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 18, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oans() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oans/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oansContest() {
        val auditdir = "/home/stormy/rla/cases/sf2024/oans/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 18, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24oa() {
        val auditdir = "/home/stormy/rla/cases/boulder24/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24oaContest() {
        val auditdir = "/home/stormy/rla/cases/boulder24/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 20, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClca() {
        val auditdir = "/home/stormy/rla/cases/boulder24/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

}