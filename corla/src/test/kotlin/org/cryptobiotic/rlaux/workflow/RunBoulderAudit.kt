package org.cryptobiotic.rlaux.workflow

import org.cryptobiotic.rlaux.corla.createElectionFromDominionCvrs
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.util.Stopwatch

object RunBoulderAudit {
    val auditDirectory = "/home/stormy/temp/workflow/runBoulder24"
    val mvrFile = "/home/stormy/temp/workflow/runBoulder24/private/testMvrs.csv" // TODO store in record ??
    val topdir = "/home/stormy/dev/github/rla/rlauxe/corla"

    @JvmStatic
    fun main2(args: Array<String>) {
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, riskLimit = .03, sampleLimit = 20000,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )

        // this sets the sampleNumbers and sorts.
        val stopwatch = Stopwatch()
        createElectionFromDominionCvrs(
            "$topdir/src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.csv",
            auditDir = auditDirectory,
            "$topdir/src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditConfigIn = auditConfig ,
            runEstimation = false
        )
        println("that took $stopwatch")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val stopwatch = Stopwatch()

        val workflow = PersistentAudit(auditDirectory)
        runAudit("readBoulder24", workflow, quiet=false)
        println("that took $stopwatch")

        workflow.auditRounds().forEach { println(it.show()) }
    }
}