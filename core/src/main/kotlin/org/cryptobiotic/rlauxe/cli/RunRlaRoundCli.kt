package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.cli.default
import org.cryptobiotic.rlauxe.audit.runRoundAgain
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.String

/** Run one round of a PersistentAudit that has already been started. */

object RunRlaRoundCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRound")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val onlyTask by parser.option(
            ArgType.String,
            shortName = "estTaskName",
            description = "run only this estimate task"
        )
        val quiet by parser.option(
            ArgType.Boolean,
            shortName = "quiet",
            description = "dont show progress messages"
        ).default(false)

        parser.parse(args)
        runRound(inputDir, onlyTask)
    }
}

object RunRoundAgainCli {

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

        val auditRecord = AuditRecord.readFrom(auditDir)
        if (auditRecord == null) {
            println("auditRecord not found at $auditDir")
            return
        }
        val auditRound = if (roundIdx == -1) auditRecord.rounds.last() else auditRecord.rounds.find { it.roundIdx == roundIdx }
        if (auditRound == null) {
            println("AuditRound roundIdx $roundIdx not found at $auditDir")
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
        val result = runRoundAgain(auditDir, contestRound, assertionRound, assertionRound.auditResult!!)
        println(result)
    }
}
