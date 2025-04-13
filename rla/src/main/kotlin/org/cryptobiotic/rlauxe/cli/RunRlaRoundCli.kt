package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.cli.default
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Files.notExists
import java.nio.file.Path
import java.util.concurrent.TimeUnit

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
            description = "use MvrManagerTestFromRecord"
        ).default(false)
        val quiet by parser.option(
            ArgType.Boolean,
            shortName = "quiet",
            description = "dont show progress messages"
        ).default(false)

        parser.parse(args)
        println("RunRound on $inputDir quiet=$quiet")
        runRound(inputDir, useTest, quiet)
    }
}

// Also called from rlaux-viewer
fun runRound(inputDir: String, useTest: Boolean, quiet: Boolean): AuditRound? {
    if (notExists(Path.of(inputDir))) {
        println("RunRliRoundCli Audit Directory $inputDir does not exist")
        return null
    }

    var complete = false
    var roundIdx = 0
    val workflow = PersistentAudit(inputDir, useTest)

    if (!workflow.auditRounds().isEmpty()) {
        val auditRound = workflow.auditRounds().last()
        roundIdx = auditRound.roundIdx

        if (!auditRound.auditWasDone) {
            println("Run audit round ${auditRound.roundIdx}")
            val roundStopwatch = Stopwatch()

            complete = workflow.runAuditRound(auditRound, quiet)
            println("  complete=$complete took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
        } else {
            complete = auditRound.auditIsComplete
        }
    }

    if (!complete) {
        roundIdx++
        // start next round and estimate sample sizes
        println("Start audit round $roundIdx")
        val nextRound = workflow.startNewRound(quiet = false)
        return if (nextRound.auditIsComplete) null else nextRound // TODO dont return null
    }

    return null
}
