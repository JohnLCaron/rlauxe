package org.cryptobiotic.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.cli.RunRlaStartFuzz
import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import kotlin.test.Test
import kotlin.test.fail

class TestRunRlaStartFuzz {

    @Test
    fun testCliRoundClca() {
        val topdir = "$testdataDir/persist/testRunCli/clca"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-simFuzz", "0.001",
                "-fuzzMvrs", "0.001",
                "-quantile", "0.5",
                "-ncards", "10000",
                "-ncontests", "11",
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
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)
        println(status)
    }

    @Test
    fun testCliRoundPolling() {
        val topdir = "$testdataDir/persist/testRunCli/polling"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "--auditType", "POLLING",
                "-simFuzz", "0.001",
                "-fuzzMvrs", "0.001",
                "-quantile", "0.5",
                "-ncards", "20000",
                "-ncontests", "5",
            )
        )

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
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
        val topdir = "$testdataDir/persist/testRunCli/raire"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-simFuzz", "0.001",
                "-fuzzMvrs", "0.001",
                "-quantile", "0.5",
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
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val status = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir=auditdir)
        println(status)
    }

    //         val Nc = 50000
    //        val margin = .04
    //        val fuzzPct = 0.0
    //        val underVotePct = 0.0
    //        val phantomPct = 0.00
    //        val cvrPercent = 0.80

    @Test
    fun testCliOneAudit() {
        val topdir = "$testdataDir/persist/testRunCli/oneaudit"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "--auditType", "ONEAUDIT",
                "-minMargin", "0.04",
                "-simFuzz", "0.001",
                "-fuzzMvrs", "0.001",
                "-quantile", "0.5",
                "-cvrFraction", "0.95",
                "-ncards", "50000",
                "-extraPct", "0.01"
            )
        )
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, false)
        println()
        print(resultsvc)
        if (resultsvc.hasErrors) fail()

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditdir)
        println(results)

        if (results.hasErrors) fail()
    }

    @Test
    fun testCliOneAuditCalc() {
        val topdir = "$testdataDir/persist/testRunCli/oneauditcalc"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "--auditType", "ONEAUDIT",
                "-minMargin", "0.04",
                "-simFuzz", "0.001",
                "-fuzzMvrs", "0.001",
                "-quantile", "0.5",
                "-cvrFraction", "0.95",
                "-ncards", "50000",
                "-extraPct", "0.01",
                "-oaStrategy", "calcMvrsNeeded",
            )
        )

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, false)
        println()
        print(resultsvc)
        if (resultsvc.hasErrors) fail()

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditdir)
        println(results)

        if (results.hasErrors) fail()
    }

}