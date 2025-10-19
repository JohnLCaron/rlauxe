package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test
import kotlin.test.fail

class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topdir = "/home/stormy/rla/persist/testCliRoundClca"
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
        RunVerifyContests.main(arrayOf("-in", topdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=topdir)
        println(status)
    }

    @Test
    fun testCliRoundPolling() {
        val topdir = "/home/stormy/rla/persist/testCliRoundPolling"
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
                "-ncontests", "2",
            )
        )

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=topdir)
        println(results)

        println("============================================================")
        val results2 = RunVerifyContests.runVerifyContests(topdir, null, false)
        println()
        print(results2)

        if (results.fail) fail()
        if (results2.fail) fail()
    }

    @Test
    fun testCliRoundRaire() {
        val topdir = "/home/stormy/rla/persist/testCliRoundRaire"
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
        RunVerifyContests.main(arrayOf("-in", topdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=topdir)
        println(status)
    }

    @Test
    fun testCliOneAudit() {
        val topdir = "/home/stormy/rla/persist/testCliRoundOneAudit"

        RunRlaCreateOneAudit.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", "0.001",
                "-ncards", "10000",
                "-ncontests", "10", // ignored
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

        if (results.fail) fail()
        if (resultsvc.fail) fail()
    }

}