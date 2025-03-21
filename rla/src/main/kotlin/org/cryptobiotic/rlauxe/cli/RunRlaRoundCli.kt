package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.PersistentAudit
import org.cryptobiotic.rlauxe.audit.RlauxAuditIF
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
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

fun runRound(inputDir: String, mvrFile: String): AuditRound? {
    if (notExists(Path.of(inputDir))) {
        println("RunRoundFuzz Audit Directory $inputDir does not exist")
        return null
    }

    val workflow = PersistentAudit(inputDir)
    val auditRound = workflow.getLastRound()

    val publisher = Publisher(inputDir)

    println("Run audit round ${auditRound.roundIdx}")
    val allDone = runAuditStage(auditRound, workflow, mvrFile, publisher)

    if (!allDone) {
        // start next round and estimate sample sizes
        println("Start audit round ${auditRound.roundIdx + 1}")
        val nextRound = workflow.startNewRound(quiet = false)

        if (nextRound.sampleNumbers.isEmpty()) {
            println("*** FAILED TO GET ANY SAMPLES ***")
            nextRound.auditIsComplete = true
        } else {
            // write the partial election state to round+1
            writeAuditRoundJsonFile(nextRound, publisher.auditRoundFile(nextRound.roundIdx))
            println("   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound.roundIdx)}")

            writeSampleNumbersJsonFile(nextRound.sampleNumbers, publisher.sampleNumbersFile(nextRound.roundIdx))
            println("   writeSampleIndicesJsonFile ${publisher.sampleNumbersFile(nextRound.roundIdx)}")
        }

        return if (nextRound.auditIsComplete) null else nextRound
    }
    return null
}

fun runAuditStage(
    auditRound: AuditRound,
    workflow: PersistentAudit,
    mvrFile: String,
    publisher: Publisher,
): Boolean {
    val roundStopwatch = Stopwatch()
    val roundIdx = auditRound.roundIdx

    val sampledMvrs = workflow.auditRecord.getMvrsForRound(workflow.ballotCards, roundIdx, mvrFile)
    println("  added ${sampledMvrs.size} mvrs to ballotCards")

    val allDone = workflow.runAuditRound(auditRound)
    println("  allDone=$allDone took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")

    // heres the changed state now that the audit has been run.
    val updatedState = auditRound.copy(auditWasDone = true, auditIsComplete = allDone)

    // overwriting it with audit info, a bit messy TODO separate estimation and audit?
    writeAuditRoundJsonFile(updatedState, publisher.auditRoundFile(roundIdx))
    println("    writeAuditRoundJsonFile to '${publisher.auditRoundFile(roundIdx)}'")

    writeCvrsCsvFile(sampledMvrs , publisher.sampleMvrsFile(roundIdx)) // TODO
    println("    write sampledMvrs to '${publisher.sampleMvrsFile(roundIdx)}'")
    println()
    return allDone
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