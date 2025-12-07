package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.cli.default
import org.cryptobiotic.rlauxe.audit.runAudit
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.String

/** Run one round of a PersistentAudit that has already been started. */

object RunRliRoundCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRound")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val useTest by parser.option(
            ArgType.Boolean,
            shortName = "test",
            description = "this is a test (uses MvrManagerTestFromRecord to set mvrs)"
        ).default(true)
        val quiet by parser.option(
            ArgType.Boolean,
            shortName = "quiet",
            description = "dont show progress messages"
        ).default(false)

        parser.parse(args)
        runRound(inputDir, useTest, quiet)
    }
}

object RunAuditCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunAudit")
        val auditDir by parser.option(
            ArgType.String,
            shortName = "auditDir",
            description = "Directory containing input election record"
        ).required()
        val roundIdx by parser.option(
            ArgType.Int,
            shortName = "round",
            description = "audit round index, last = -1"
        ).default(1)
        val contest by parser.option(
            ArgType.Int,
            shortName = "contest",
            description = "contest id"
        ).default(1)
        val assertionWinLose by parser.option(
            ArgType.String,
            shortName = "assertion",
            description = "assertion win/lose"
        ).default("first")

        parser.parse(args)

        val pflow = PersistedWorkflow(auditDir, false)
        val auditRecord = pflow.auditRecord
        val auditRound = if (roundIdx == -1) auditRecord.rounds.last() else auditRecord.rounds.find { it.roundIdx == roundIdx }
        if (auditRound == null) {
            println("AuditRound $auditRound not found")
            return
        }
        val contestRound = auditRound.contestRounds.find { it.id == contest }
        if (contestRound == null) {
            println("contestRound with contest id = $contest not found")
            return
        }
        val assertionRound = if (assertionWinLose == "first") contestRound.assertionRounds.first() else {
            contestRound.assertionRounds.find { it.assertion.assorter.winLose().contains(assertionWinLose) }
        }
        if (assertionRound == null) {
            println("assertionRound with assertion.assorter.winLose() containing '$assertionWinLose' not found")
            return
        }
        if (assertionRound.auditResult == null) {
            println("assertionRound.auditResult is null")
            return
        }

        // fun runAudit(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): String {
        val result = runAudit(auditDir, contestRound, assertionRound, assertionRound.auditResult!!)
        println(result)
    }
}
