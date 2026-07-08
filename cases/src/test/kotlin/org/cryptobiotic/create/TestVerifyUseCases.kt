package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.belgium.belgiumJsonInputResource
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.fail

// slow - keep out of unit tests
class TestVerifyUseCases {
    val show = false

    @Test
    fun testRunVerifyBoulder24oa() {
        val topdir = "${cases}/boulder/boulder2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24oaContest() {
        val topdir = "${cases}/boulder/boulder2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, 17, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClca() {
        val topdir = "${cases}/boulder/boulder2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulderClcaContest() {
        val topdir = "${cases}/boulder/boulder2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, 20, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaConsistent() {
        val topdir = "${cases}/corla/corla2020/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorlaUniform() {
        val topdir = "${cases}/corla/corla2020/uniform"
        val results = RunVerifyContests.runVerifyContests(topdir, 1, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyCorla2024() {
        val topdir = "${cases}/corla/corla2020/uniform"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clca() {
        val topdir = "${cases}/sf/sf2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024clcaContest() {
        val topdir = "${cases}/sf/sf2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, 52, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oa() {
        val topdir = "${cases}/sf/sf2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifySf2024oaContest() {
        val topdir = "${cases}/sf/sf2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, 16, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyDHondt() {
        val topdir = "${cases}/belgium/belgium2024"
        belgiumJsonInputResource.keys.forEach {
            val subdir = "$topdir/$it"
            println("-------------------verify $it")
            val results = RunVerifyContests.runVerifyContests(subdir, null, show = show)
            println()
            print(results)
            if (results.hasErrors) fail()
        }
    }

    @Test
    fun testRunVerifyGa() {
        val topdir = "${cases}/ga/ga2026"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyGaPoll() {
        val topdir = "${cases}/ga/ga2026poll"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = show)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}