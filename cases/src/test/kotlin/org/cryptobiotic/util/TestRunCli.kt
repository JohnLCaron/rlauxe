package org.cryptobiotic.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.cli.RunRlaCreateOneAudit
import org.cryptobiotic.rlauxe.cli.RunRlaStartFuzz
import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.writeMvrsForRound
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import kotlin.test.Test
import kotlin.test.fail

class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topdir = "/home/stormy/rla/persist/testRunCli/clca"
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
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)
        println(status)
    }

    @Test
    fun testCliRoundPolling() {
        val topdir = "/home/stormy/rla/persist/testRunCli/polling"
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
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
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
    }

    @Test
    fun testCliRoundRaire() {
        val topdir = "/home/stormy/rla/persist/testRunCli/raire"
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
        RunVerifyContests.main(arrayOf("-in", auditdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)
        println(status)
    }

    @Test
    fun testCliOneAudit() {
        val topdir = "/home/stormy/rla/persist/testRunCli/oneaudit"
        val auditdir = "$topdir/audit"

        RunRlaCreateOneAudit.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.04",
                "-fuzzMvrs", "0.00",
                "-ncards", "50000",
                "-extraPct", "0.00"
            )
        )
        val publisher = Publisher(auditdir)
        // val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        // writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, false)
        println()
        print(resultsvc)
        if (resultsvc.hasErrors) fail()

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
            if (!done) writeMvrsForRound(publisher, lastRound!!.roundIdx)
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditdir)
        println(results)

        if (results.hasErrors) fail()
    }

}