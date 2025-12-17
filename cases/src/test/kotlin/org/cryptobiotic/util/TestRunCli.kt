package org.cryptobiotic.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.cli.RunRlaCreateOneAudit
import org.cryptobiotic.rlauxe.cli.RunRlaStartFuzz
import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.writeMvrsForRound
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.test.Test
import kotlin.test.fail

class TestRunCli {

    @Test
    fun testCliRoundClca() {
        val topdir = "$testdataDir/persist/testRunCli/clca"
        val auditdir = "$topdir/audit"

        RunRlaStartFuzz.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.01",
                "-fuzzMvrs", ".0123",
                "-ncards", "10000",
                "-ncontests", "11",
            )
        )
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
        val writeMvrs = config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs

        println("============================================================")
        RunVerifyContests.main(arrayOf("-in", auditdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
            if (!done && writeMvrs) writeMvrsForRound(publisher, lastRound!!.roundIdx)
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
                "-isPolling",
                "-fuzzMvrs", ".0023",
                "-ncards", "20000",
                "-ncontests", "2",
            )
        )

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
        val writeMvrs = config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs

        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 7
            if (!done && writeMvrs) writeMvrsForRound(publisher, lastRound!!.roundIdx) // cabt use test for polling because cards dont have the votes
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
        val writeMvrs = config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs

        println("============================================================")
        RunVerifyContests.main(arrayOf("-in", auditdir))

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
            if (!done && writeMvrs) writeMvrsForRound(publisher, lastRound!!.roundIdx) // TODO add this to runRound; dont need when useTest = true?
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

        RunRlaCreateOneAudit.main(
            arrayOf(
                "-in", topdir,
                "-minMargin", "0.04",
                "-fuzzMvrs", "0.02",
                "-ncards", "50000",
                "-extraPct", "0.00"
            )
        )
        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        val writeMvrs = config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs

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
            if (!done && writeMvrs) writeMvrsForRound(publisher, lastRound!!.roundIdx) // RunRlaCreateOneAudit writes the mvrs
        }

        println("============================================================")
        val results = RunVerifyAuditRecord.runVerifyAuditRecord(inputDir = auditdir)
        println(results)

        if (results.hasErrors) fail()
    }

}