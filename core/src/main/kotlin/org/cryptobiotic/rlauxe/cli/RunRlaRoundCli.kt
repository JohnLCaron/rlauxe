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
// TODO break into initial estimate and the real run round ?

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
        val round by parser.option(
            ArgType.Int,
            shortName = "round",
            description = "audit round index"
        ).default(1)
        val contest by parser.option(
            ArgType.Int,
            shortName = "contest id",
            description = ""
        ).default(1)
        val assertion by parser.option(
            ArgType.String,
            shortName = "assertion win/lose",
            description = ""
        ).default("first")

        parser.parse(args)

        val pflow = PersistedWorkflow(auditDir, false)
        val auditRecord = pflow.auditRecord
        val auditRound = auditRecord.rounds.first()
        val contestRound = auditRound.contestRounds.first()
        val assertionRound = contestRound.assertionRounds.first() //  { it.assertion.assorter.winLose() == contest }!!

        // fun runAudit(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): String {
        val result = runAudit(auditDir, contestRound, assertionRound, assertionRound.auditResult!!)
        println(result)
    }
}
