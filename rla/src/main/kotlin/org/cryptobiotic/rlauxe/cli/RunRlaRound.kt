package org.cryptobiotic.rlauxe.cli


import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.PersistentWorkflow
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import java.util.concurrent.TimeUnit

/** Run one round of the RLA. */
object RunRound {

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
    val workflow = PersistentWorkflow(inputDir)
    val auditRound = workflow.getLastRound()

    val publisher = Publisher(inputDir)

    val allDone = runAuditStage(auditRound, workflow, mvrFile, publisher)
    if (!allDone) {
        // start next round and get default sample indices
        val nextRound = workflow.startNewRound(quiet = false)

        if (nextRound.sampledIndices.isEmpty()) {
            println("*** FAILED TO GET ANY SAMPLES ***")
            nextRound.auditIsComplete = true
        }

        // write the partial election state to round+1
        writeAuditRoundJsonFile(nextRound, publisher.auditRoundFile(nextRound.roundIdx))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound.roundIdx)}")

        writeSampleIndicesJsonFile(nextRound.sampledIndices, publisher.sampleIndicesFile(nextRound.roundIdx))
        println("   writeSampleIndicesJsonFile ${publisher.sampleIndicesFile(nextRound.roundIdx)}")

        return if (nextRound.auditIsComplete) null else nextRound
    }
    return null
}

fun runAuditStage(
    auditRound: AuditRound,
    workflow: PersistentWorkflow,
    mvrFile: String,
    publisher: Publisher,
): Boolean {

    // the only place privy to private data
    val resultMvrs = readCvrsJsonFile(mvrFile)
    if (resultMvrs is Err) println(resultMvrs)
    require(resultMvrs is Ok)
    val testMvrs = resultMvrs.unwrap()

    val roundStopwatch = Stopwatch()
    var allDone = false
    val roundIdx = auditRound.roundIdx

    val resultIndices = readSampleIndicesJsonFile(publisher.sampleIndicesFile(roundIdx))
    if (resultIndices is Err) println(resultIndices)
    require(resultIndices is Ok)
    val sampleIndices = resultIndices.unwrap() // these are the samples we are going to audit.

    if (sampleIndices.isEmpty()) {
        println("***Error sampled Indices are empty for round $roundIdx")
        return true

    } else {
        println("runAudit $roundIdx samples=${sampleIndices.size}")
        val sampledMvrs = sampleIndices.map {
            testMvrs[it]
        }

        allDone = workflow.runAudit(auditRound, sampledMvrs.map { it.cvr })
        println("  allDone=$allDone took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        // heres the changed state now that the audit has been run.
        val updatedState = auditRound.copy(auditWasDone = true, auditIsComplete = allDone)

        // overwriting it with audit info, a bit messy TODO separate estimation and audit?
        writeAuditRoundJsonFile(updatedState, publisher.auditRoundFile(roundIdx))
        println("   writeAuditRoundJsonFile ${publisher.auditRoundFile(roundIdx)}")

        writeCvrsJsonFile(sampledMvrs, publisher.sampleMvrsFile(roundIdx))
        println("   write sampledMvrs ${publisher.sampleMvrsFile(roundIdx)}")
        return allDone
    }

}


/*
fun readPersistentWorkflow(round: Int, publish: Publisher): Pair<AuditState, PersistentWorkflow?> {
    println("readPersistentWorkflow from round $round")
    val resultAuditConfig = readAuditConfigJsonFile(publish.auditConfigFile())
    if (resultAuditConfig is Err) println(resultAuditConfig)
    require(resultAuditConfig is Ok)
    val auditConfig = resultAuditConfig.unwrap()

    val resultAuditResult: Result<AuditState, ErrorMessages> = readAuditStateJsonFile(publish.auditRoundFile(round))
    if (resultAuditResult is Err) println(resultAuditResult)
    require(resultAuditResult is Ok)
    val auditState = resultAuditResult.unwrap()
    if (auditState.auditIsComplete) {
        return Pair(auditState, null)
    }

    if (auditConfig.auditType == AuditType.CLCA) {
        val resultCvrs = readCvrsJsonFile(publish.cvrsFile())
        if (resultCvrs is Err) println(resultCvrs)
        require(resultCvrs is Ok)
        val cvrs = resultCvrs.unwrap()
        return Pair(auditState, PersistentWorkflow(auditConfig, auditState.contests, emptyList(), cvrs))

    } else {
        val resultBallotManifest = readBallotManifestJsonFile(publish.ballotManifestFile())
        if (resultBallotManifest is Err) println(resultBallotManifest)
        require(resultBallotManifest is Ok)
        val ballotManifest = resultBallotManifest.unwrap()
        return Pair(
            auditState,
            PersistentWorkflow(auditConfig, auditState.contests, ballotManifest.ballots, emptyList())
        )
    }
}
 */
