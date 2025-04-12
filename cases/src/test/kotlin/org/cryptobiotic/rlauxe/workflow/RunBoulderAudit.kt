package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.boulder.createElectionFromDominionCvrs
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.junit.jupiter.api.Test

class RunBoulderAudit {
    val auditDirectory = "/home/stormy/temp/workflow/runBoulder24"
    val topdir = "/home/stormy/dev/github/rla/rlauxe/corla"

    @Test
    fun createBoulder24() {
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, riskLimit = .03, sampleLimit = 20000,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )

        // this sets the sampleNumbers and sorts.
        val stopwatch = Stopwatch()
        createElectionFromDominionCvrs(
            "$topdir/src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            auditDir = auditDirectory,
            "$topdir/src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditConfigIn = auditConfig,
        )
        println("that took $stopwatch")
    }

    @Test
    fun runAudit() {
        val stopwatch = Stopwatch()

        val workflow = PersistentAudit(auditDirectory)
        runAudit("readBoulder24", workflow, quiet=false)
        println("that took $stopwatch")

        workflow.auditRounds().forEach { println(it.show()) }
    }
}