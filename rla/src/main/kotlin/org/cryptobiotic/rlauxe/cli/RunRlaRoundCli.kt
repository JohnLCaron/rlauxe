package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
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
        val mvrFile by parser.option(
            ArgType.String,
            shortName = "mvrs",
            description = "File containing sampled Mvrs"
        ).required()

        parser.parse(args)
        println("RunRound on $inputDir mvrFile=$mvrFile")
        runRound(inputDir, mvrFile)
        // println("  retval $retval")
    }
}


// Also called from rlaux-viewer
fun runRound(inputDir: String, mvrFile: String): AuditRound? {
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

            // TODO the mvrFile might be the mvrs that were just audited. here we are assuming the auditRecord has testMvrs
            val enterMvrsOk = workflow.auditRecord.enterMvrs(mvrFile)

            complete = workflow.runAuditRound(auditRound)
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
    if (round.sampleNumbers.isNotEmpty()) {
        writeSampleNumbersJsonFile(round.sampleNumbers, publish.sampleNumbersFile(round.roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.sampleNumbersFile(round.roundIdx)}")
    } else {
        println("*** FAILED TO GET ANY SAMPLES ***")
    }
    return round
}