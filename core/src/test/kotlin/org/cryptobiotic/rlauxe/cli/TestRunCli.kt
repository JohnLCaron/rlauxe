package org.cryptobiotic.rlauxe.cli

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.writeMvrsForRound
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
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
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "25",
            )
        )
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        RunVerifyContests.main(arrayOf("-in", auditdir))

        println("============================================================")
        RunRliRoundCli.main(
            arrayOf(
                "-in", auditdir,
                "-test",
            )
        )

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.main(
            arrayOf(
                "-in", auditdir,
            )
        )
        println(status)
        topPath.deleteRecursively()
    }

    @Test
    fun testCliRoundPolling() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-isPolling",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
                "-ncontests", "2",
            )
        )

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
            if (!done) writeMvrsForRound(publisher, lastRound!!.roundIdx) // cant use test for polling because cards dont have the votes
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)
        println(results)

        println("============================================================")
        val results2 = RunVerifyContests.runVerifyContests(auditdir, null, false)
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
        val auditdir = "$topdir/audit"

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
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        val results = RunVerifyContests.runVerifyContests(auditdir, null, false)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results2 = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)

        topPath.deleteRecursively()
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    @Test
    fun testCliOneAudit() {
        val topPath = createTempDirectory()
        val topdir = topPath.toString()
        val auditdir = "$topdir/audit"

        RunRlaCreateOneAudit.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", "0.001",
                "-ncards", "10000",
            )
        )
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, false)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditdir)
        println(results)

        if (results.hasErrors) fail()
        if (resultsvc.hasErrors) fail()
        topPath.deleteRecursively()
    }

}