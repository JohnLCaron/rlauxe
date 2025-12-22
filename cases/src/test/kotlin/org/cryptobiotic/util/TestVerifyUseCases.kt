package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

class TestVerifyUseCases {
    val show = false

    @Test
    fun testRunVerifyBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24oaContest() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 20, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClca() {
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClcaContest() {
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 20, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaOneAudit() {
        val auditdir = "$testdataDir/cases/corla/oneaudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaOneAuditContest() {
        val auditdir = "$testdataDir/cases/corla/oneaudit/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaClca() {
        val auditdir = "$testdataDir/cases/corla/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaClcaContest() {
        val auditdir = "$testdataDir/cases/corla/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clca() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clcaContest() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 52, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oa() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oaContest() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 24, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024oans() {
        val auditdir = "$testdataDir/cases/sf2024/oans/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024oansContest() {
        val auditdir = "$testdataDir/cases/sf2024/oans/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024polling() {
        val auditdir = "$testdataDir/cases/sf2024/polling/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024pollingContest() {
        val auditdir = "$testdataDir/cases/sf2024/polling/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, 1, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyDHondt() {
        val auditdir = "$testdataDir/cases/belgium/2024/Hainaut/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}