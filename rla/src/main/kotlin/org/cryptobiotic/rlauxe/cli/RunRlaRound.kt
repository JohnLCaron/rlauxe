package org.cryptobiotic.rlauxe.cli


import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.AuditRecord
import org.cryptobiotic.rlauxe.core.TestH0Status
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

    fun runRound(inputDir: String, mvrFile: String): Int {
        val auditRecord = AuditRecord.readFrom(inputDir)
        val auditConfig = auditRecord.auditConfig
        val auditRound = auditRecord.rounds.last()
        val state = auditRound.state
        val round = auditRecord.nrounds
        require(round == state.roundIdx)

        val workflow = PersistentWorkflow(auditConfig, state.contests, emptyList(), auditRecord.cvrs) // TODO other auditTypes

        /*
        val workflow = if (auditConfig.auditType == AuditType.CLCA) {
            PersistentWorkflow(auditConfig, auditState.contests, emptyList(), cvrs)
        } else {
            PersistentWorkflow(auditConfig, auditState.contests, ballotManifest.ballots, emptyList())
        }

        // read last state
        val publisher = Publisher(inputDir)
        val round = publisher.rounds()
        val (auditState, workflow) = readPersistentWorkflow(round, publisher)
        require(round == auditState.roundIdx)
        require((workflow == null) == auditState.auditIsComplete)

        if (workflow == null) {
            println("***No more rounds, all done")
        } else {
        */

        val publisher = Publisher(inputDir)

        val (allDone, prevSamples) = runAuditStage(state, workflow, mvrFile, publisher)
        if (!allDone) {
            val nextRound = round + 1
            // get the next round of samples wanted
            val indices = workflow.chooseSamples(nextRound, show = true)

            val nextState = if (indices.size == 0) {
                println("*** NO SAMPLES: audit is done ***") // TODO just skip it?
                AuditState("Round${nextRound}", nextRound, indices.size, false, true, emptyList())
            } else {
                // we want FailMaxSamplesAllowed to get recorded in the persistent state, even though its done TODO review
                val contestsNotDone = workflow.getContests().filter { !it.done || it.status == TestH0Status.FailMaxSamplesAllowed }
                AuditState("Round${nextRound}", round + 1, indices.size,  false, false, contestsNotDone)
            }
            // write the partial election state to round+1
            writeAuditStateJsonFile(nextState, publisher.auditRoundFile(nextRound))
            println("   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound)}")

            writeSampleIndicesJsonFile(indices, publisher.sampleIndicesFile(nextRound))
            println("   writeSampleIndicesJsonFile ${publisher.sampleIndicesFile(nextRound)}")
        }

        return 0
    }
}


fun runAuditStage(
    auditState: AuditState,
    workflow: RlauxWorkflowIF,
    mvrFile: String,
    publisher: Publisher,
): Pair<Boolean, List<Int>> {

    // the only place privy to private data
    val resultMvrs = readCvrsJsonFile(mvrFile)
    if (resultMvrs is Err) println(resultMvrs)
    require(resultMvrs is Ok)
    val testMvrs = resultMvrs.unwrap()

    val roundStopwatch = Stopwatch()
    var allDone = false
    val roundIdx = auditState.roundIdx

    val resultIndices = readSampleIndicesJsonFile(publisher.sampleIndicesFile(roundIdx))
    if (resultIndices is Err) println(resultIndices)
    require(resultIndices is Ok)
    val indices = resultIndices.unwrap()

    if (indices.isEmpty()) {
        println("***Error sampled Indices are empty for round $roundIdx")
        allDone = true

    } else {
        println("runAudit $roundIdx samples=${indices.size}")
        val sampledMvrs = indices.map {
            testMvrs[it]
        }

        // TODO why not runAudit on CvrUnderAudit?
        allDone = workflow.runAudit(indices, sampledMvrs.map { it.cvr }, roundIdx)
        println("  allDone=$allDone took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        // heres the state now that the audit has been run
        val updateStated = AuditState(
            auditState.name,
            auditState.roundIdx,
            auditState.nmvrs,
            true,
            allDone,
            workflow.getContests(),
        )
        // overwriting it with audit info, a bit messy TODO separate estimation and audit?
        writeAuditStateJsonFile(updateStated, publisher.auditRoundFile(roundIdx))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(roundIdx)}")

        writeCvrsJsonFile(sampledMvrs, publisher.sampleMvrsFile(roundIdx))
        println("   write sampledMvrs ${publisher.sampleMvrsFile(roundIdx)}")

        workflow.showResults(indices.size)
    }

    return Pair(allDone, indices)
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
