package org.cryptobiotic.rlauxe.cli

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.cli.default
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Files.notExists
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger("RunRliRoundCli")

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

// Called from rlaux-viewer
fun runRound(inputDir: String, useTest: Boolean, quiet: Boolean): AuditRound? {
    try {
        if (notExists(Path.of(inputDir))) {
            logger.warn { "RunRliRoundCli Audit Directory $inputDir does not exist" }
            return null
        }
        logger.info { "runRound on Audit in $inputDir" }

        var complete = false
        var roundIdx = 0
        val workflow = PersistedWorkflow(inputDir, useTest)

        if (!workflow.auditRounds().isEmpty()) {
            val auditRound = workflow.auditRounds().last()
            roundIdx = auditRound.roundIdx

            if (!auditRound.auditWasDone) {
                logger.info { "Run audit round ${auditRound.roundIdx}" }
                val roundStopwatch = Stopwatch()

                // run the audit for this round
                complete = workflow.runAuditRound(auditRound, quiet)
                logger.info { "  complete=$complete took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms" }
            } else {
                complete = auditRound.auditIsComplete
            }
        }

        if (!complete) {
            roundIdx++
            // start next round and estimate sample sizes
            logger.info { "Start audit round $roundIdx using ${workflow}" }
            val nextRound = workflow.startNewRound(quiet = false)
            logger.info { "nextRound ${nextRound.show()}" }
            return if (nextRound.auditIsComplete) null else nextRound // TODO dont return null
        }

        logger.info { "runRound $roundIdx complete = $complete" }
        return null

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}


fun runRoundResult(inputDir: String, useTest: Boolean, quiet: Boolean): Result<AuditRound, ErrorMessages> {
    val errs = ErrorMessages("runRoundResult")

    try {
        if (notExists(Path.of(inputDir))) {
            return errs.add( "RunRliRoundCli Audit Directory $inputDir does not exist" )
        }
        logger.info { "runRound on Audit in $inputDir" }
        val rlauxAudit = PersistedWorkflow(inputDir, useTest)

        var roundIdx = 0
        var complete = false

        if (!rlauxAudit.auditRounds().isEmpty()) {
            val lastRound = rlauxAudit.auditRounds().last()
            roundIdx = lastRound.roundIdx

            if (!lastRound.auditWasDone) {
                logger.info { "Run audit round ${lastRound.roundIdx}" }
                val roundStopwatch = Stopwatch()
                complete = rlauxAudit.runAuditRound(lastRound, quiet)
                logger.info { "  complete=$complete took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms" }
            }
        }

        if (!complete) {
            roundIdx++
            // start next round and estimate sample sizes
            logger.info { "Start audit round $roundIdx using ${rlauxAudit}" }
            val nextRound = rlauxAudit.startNewRound(quiet = false)
            logger.info { "nextRound ${nextRound.show()}" }
            return Ok(nextRound)

        } else {
            val lastRound = rlauxAudit.auditRounds().last()
            logger.info { "runRound ${lastRound.roundIdx} complete = $complete" }
            return Ok(lastRound)
        }

    } catch (t: Throwable) {
        logger.error {t}
        return errs.add( t.message ?: t.toString())
    }
}

