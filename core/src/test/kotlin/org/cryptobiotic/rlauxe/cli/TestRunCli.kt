package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalPathApi::class)
class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "25",
            )
        )

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(topdir, null, false)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(results)

        topPath.deleteRecursively()
        if (results.fail) fail()
        if (resultsvc.fail) fail()
    }

    @Test
    fun testCliRoundPolling() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
            )
        )

        // i think cant call runVerifyContests on polling, as there are no cvrs to compare
        // revistit when we merge with CheckAudit
        //println("============================================================")
        //val resultsvc = RunVerifyContests.runVerifyContests(topdir, null, false)
        //println()
        //print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(results)

        topPath.deleteRecursively()
        if (results.fail) fail()
        //if (resultsvc.fail) fail()
    }

    @Test
    fun testCliRoundRaire() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addRaireContest",
                "--addRaireCandidates", "5",
            )
        )

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(topdir, null, false)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(results)

        topPath.deleteRecursively()
        if (results.fail) fail()
        if (resultsvc.fail) fail()
    }

    @Test
    fun testCliOneAudit() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        // val topdir = "/home/stormy/rla/persist/testRlaOA"

        RunRlaCreateOneAudit.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", "0.001",
                "-ncards", "10000",
                "-ncontests", "10",
                "--addRaireContest",
                "--addRaireCandidates", "5",
            )
        )

        val auditDir = "$topdir/audit"
        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditDir, null, false)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditDir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditDir)
        println(results)

        topPath.deleteRecursively()
        if (results.fail) fail()
        if (resultsvc.fail) fail()
    }
}