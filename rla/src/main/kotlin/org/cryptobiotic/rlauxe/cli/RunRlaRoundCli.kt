package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.cli.default
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.PersistentAudit
import org.cryptobiotic.rlauxe.audit.RlauxAuditIF
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Files.notExists
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Run one round of an RLA that has already been started. */
object RunRliRoundCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRound")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val quiet by parser.option(
            ArgType.Boolean,
            shortName = "quiet",
            description = "dont show progress messages"
        ).default(false)

        parser.parse(args)
        println("RunRound on $inputDir quiet=$quiet")
        runRound(inputDir, quiet)
        // println("  retval $retval")
    }
}


// Also called from rlaux-viewer
fun runRound(inputDir: String, quiet: Boolean): AuditRound? {
    if (notExists(Path.of(inputDir))) {
        println("RunRliRoundCli Audit Directory $inputDir does not exist")
        return null
    }

    var complete = false
    var roundIdx = 0
    val workflow = PersistentAudit(inputDir)

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

fun runChooseSamples(workflow: RlauxAuditIF, publish: Publisher): AuditRound {
    val round = workflow.startNewRound(quiet = false)
    if (round.samplePrns.isNotEmpty()) {
        writeSampleNumbersJsonFile(round.samplePrns, publish.sampleNumbersFile(round.roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.sampleNumbersFile(round.roundIdx)}")
    } else {
        println("*** FAILED TO GET ANY SAMPLES ***")
    }
    return round
}