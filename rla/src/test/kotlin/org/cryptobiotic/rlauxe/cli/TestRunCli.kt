package org.cryptobiotic.rlauxe.cli

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.005",
                "-fuzzMvrs", ".0123",
                "-ncards", "50000",
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
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
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
                "-isPolling",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
            )
        )

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(status)

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
        RunVerifyContests.main(arrayOf("-in", topdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = topdir)
        println(status)

        topPath.deleteRecursively()
    }
}