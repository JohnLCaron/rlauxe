package org.cryptobiotic.rlauxe.cli


import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
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
        // read last state
        val publisher = Publisher(inputDir)
        val round = publisher.rounds()
        val (auditState, workflow) = readPersistentWorkflow(round, publisher)
        require(round == auditState.roundIdx)
        require((workflow == null) == auditState.auditIsComplete)

        if (workflow == null) {
            println("***No more rounds, all done")
        } else {
            val resultMvrs = readCvrsJsonFile(mvrFile)
            if (resultMvrs is Err) println(resultMvrs)
            require(resultMvrs is Ok)
            val mvrs = resultMvrs.unwrap()

            val (allDone, prevSamples) = runAuditStage(auditState, workflow, mvrs, publisher)
            if (!allDone) {
                // get the next round of samples wanted
                val samples = runChooseSamples(round + 1, workflow, publisher)
                val state = if (samples.size == 0) {
                    println("*** NO SAMPLES: audit is done ***")
                    AuditState("Round${round + 1}", round + 1, samples.size, 0, false, true, emptyList())
                } else {
                    val roundSet = RoundIndexSet(round + 1, samples, prevSamples)
                    println("  newSamplesNeeded=${roundSet.newSamples}, total samples=${samples.size}, ready to audit")

                    // write the partial election state to round+1
                    // we want FailMaxSamplesAllowed to get recorded in the persistent state, even though its done
                    val contestsNotDone = workflow.getContests().filter { !it.done || it.status == TestH0Status.FailMaxSamplesAllowed }
                    AuditState("Round${round + 1}", round + 1, samples.size, roundSet.newSamples, false, false, contestsNotDone)
                }
                writeAuditStateJsonFile(state, publisher.auditRoundFile(round + 1))
                println("   writeAuditStateJsonFile ${publisher.auditRoundFile(round + 1)}")
            }
        }
        return 0
    }
}

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

fun runAuditStage(
    auditState: AuditState,
    workflow: RlauxWorkflowIF,
    testMvrs: List<CvrUnderAudit>,
    publish: Publisher,
): Pair<Boolean, List<Int>> {
    val roundStopwatch = Stopwatch()
    var allDone = false
    val roundIdx = auditState.roundIdx

    val resultIndices = readSampleIndicesJsonFile(publish.sampleIndicesFile(roundIdx))
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
            auditState.newMvrs,
            true,
            allDone,
            workflow.getContests(),
        )
        writeAuditStateJsonFile(updateStated, publish.auditRoundFile(roundIdx))
        println("   writeAuditStateJsonFile ${publish.auditRoundFile(roundIdx)}")

        writeCvrsJsonFile(sampledMvrs, publish.sampleMvrsFile(roundIdx))
        println("   write sampledMvrs ${publish.sampleMvrsFile(roundIdx)}")

        workflow.showResults(indices.size)
    }

    return Pair(allDone, indices)
}

class RoundIndexSet(val round: Int, sampledIndices: List<Int>, previousSamples: List<Int>) {
    val previousSet = previousSamples.toSet()
    val newSamples: Int = sampledIndices.count { it !in previousSet }

    /*
    init {
        val indexSet = sampledIndices.toSet()
        require(sampledIndices.size == indexSet.size)
        previousSamples.forEach {
            if (it !in indexSet)
                println("previousSample $it not in sampledIndices")
        }

        require(previousSamples.size == previousSet.size)
        println("${sampledIndices.size} should equal ${(newSamples +  previousSet.size)}")
        require(sampledIndices.size == (newSamples +  previousSet.size))
    } */
}

