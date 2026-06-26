package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.audit.runRound
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalPathApi::class)
// test RunCli in temp directory
class TestRunCliInTemp {

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
        RunVerifyContests.main(arrayOf("-in", topdir))

        println("============================================================")
        RunRlaRoundCli.main(
            arrayOf(
                "-in", topdir,
            )
        )

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.main(
            arrayOf(
                "-in", topdir,
            )
        )
        println(status)
        topPath.deleteRecursively()
    }

    @Test
    fun testCliRoundPolling() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "--auditType", "POLLING",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
                "-ncontests", "2",
            )
        )

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=topdir)
        println(results)

        println("============================================================")
        val results2 = RunVerifyContests.runVerifyContests(topdir, null, false)
        println()
        print(results2)

        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
        topPath.deleteRecursively()
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
        val results = RunVerifyContests.runVerifyContests(topdir, null, false)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results2 = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=topdir)

        topPath.deleteRecursively()
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testCliOneAudit() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-type", "ONEAUDIT",
                "-minMargin", "0.02",
                "-fuzzMvrs", "0.001",
                "-ncards", "10000",
            )
        )

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(topdir, null, false)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(results)

        if (results.hasErrors) fail()
        if (resultsvc.hasErrors) fail()
        topPath.deleteRecursively()
    }

}