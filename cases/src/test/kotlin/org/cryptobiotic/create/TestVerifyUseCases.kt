package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

class TestVerifyUseCases {
    val show = false

    @Test
    fun testRunVerifyBoulder24oa() {
        val topdir = "$testdataDir/cases/boulder24/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24oaContest() {
        val topdir = "$testdataDir/cases/boulder24/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, 17, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClca() {
        val topdir = "$testdataDir/cases/boulder24/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClcaContest() {
        val topdir = "$testdataDir/cases/boulder24/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, 20, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaConsistent() {
        val topdir = "$testdataDir/cases/corla/consistent"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaUniform() {
        val topdir = "$testdataDir/cases/corla/uniform"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    //@Test
    fun testRunVerifyCorlaPolling() {
        val topdir = "$testdataDir/cases/corla/polling"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    //@Test
    fun testRunVerifyCorlaPollingContest() {
        val topdir = "$testdataDir/cases/corla/polling"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clca() {
        val topdir = "$testdataDir/cases/sf2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clcaContest() {
        val topdir = "$testdataDir/cases/sf2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, 52, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oa() {
        val topdir = "$testdataDir/cases/sf2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oaContest() {
        val topdir = "$testdataDir/cases/sf2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, 16, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oaps() {
        val topdir = "$testdataDir/cases/sf2024/oaps"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oapsContest() {
        val topdir = "$testdataDir/cases/sf2024/oaps"
        val results = RunVerifyContests.runVerifyContests(topdir, 16, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024oans() {
        val topdir = "$testdataDir/cases/sf2024/oans"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024oansContest() {
        val topdir = "$testdataDir/cases/sf2024/oans"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024polling() {
        val topdir = "$testdataDir/cases/sf2024/polling"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    // @Test
    fun testRunVerifySf2024pollingContest() {
        val topdir = "$testdataDir/cases/sf2024/polling"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyDHondt() {
        val topdir = "$testdataDir/cases/belgium/2024/Hainaut"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}