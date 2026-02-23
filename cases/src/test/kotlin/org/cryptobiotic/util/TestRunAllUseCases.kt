package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.junit.jupiter.api.Test
import kotlin.test.fail

class TestRunAllUseCases {
    @Test
    fun runBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"
        runAllRoundsAndVerify(auditdir)
    }

    @Test
    fun runBoulder24clca() { // simulate CVRs
        val topdir = "$testdataDir/cases/boulder24/clca"
        runAllRoundsAndVerify("$topdir/audit")
    }

    @Test
    fun runColoradoOneAudit() {
        val topdir = "$testdataDir/cases/corla/oneaudit"
        runAllRoundsAndVerify("$topdir/audit")
    }

    @Test
    fun runColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"
        runAllRoundsAndVerify("$topdir/audit")
    }

    @Test
    fun runSFElectionOA() {
        val topdir = "$testdataDir/cases/sf2024/oa"
        runAllRoundsAndVerify("$topdir/audit")
    }

    @Test
    fun runSFElectionClca() {
        val topdir = "$testdataDir/cases/sf2024/clca"
        runAllRoundsAndVerify("$topdir/audit")
    }
}

fun runAllRoundsAndVerify(auditdir: String, maxRounds:Int=7) {
    println("============================================================")
    var done = false
    var lastRound: AuditRoundIF? = null

    while (!done) {
        lastRound = runRound(inputDir = auditdir)
        if (lastRound == null) fail()
        done = lastRound.auditIsComplete || lastRound.roundIdx > maxRounds
    }

    if (lastRound != null) {
        println("nrounds = ${lastRound.roundIdx} nmvrs = ${lastRound.nmvrs} auditdir=$auditdir")
    } else {
        println("failed in auditdir=$auditdir")
        fail()
    }

    println("============================================================")

    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(verifyResults)
    if (verifyResults.hasErrors) fail()

}