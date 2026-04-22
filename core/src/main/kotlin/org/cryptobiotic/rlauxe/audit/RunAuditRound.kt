package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyAuditRoundCommitment
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("RunAuditRound")

// called from cli and rlauxe-viewer
fun runRound(inputDir: String, onlyTask: OnlyTask? = null): AuditRoundIF? {
    val roundResult = runRoundResult(inputDir, onlyTask)
    if (roundResult.isErr) {
        logger.error{"runRoundResult failed ${roundResult.component2()}"}
        return null
    }
    return roundResult.unwrap()
}

// run one round and get ready to run the next round; or get ready to run the first round.
fun runRoundResult(auditDir: String, onlyTask: OnlyTask? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("runRoundResult")

    try {
        if (notExists(Path.of(auditDir))) {
            return errs.add( "audit Directory $auditDir does not exist" )
        }
        val auditRecord = AuditRecord.read(auditDir)
        if (auditRecord == null) {
            return errs.add("directory '$auditDir' does not contain an audit record")
        }
        require(auditRecord is AuditRecord)

        val workflow = PersistedWorkflow(auditRecord)
        var roundIdx = 0
        var complete = false
        var auditWasRun = false

        // run the audit on the last round, if there is one
        if (!workflow.auditRounds().isEmpty()) {
            val lastRound = workflow.auditRounds().last()
            roundIdx = lastRound.roundIdx

            if (!lastRound.auditWasDone) {
                logger.info { "Start runAuditRound ${lastRound.roundIdx}" }
                val roundStopwatch = Stopwatch()
                // run the audit for this round
                complete = workflow.runAuditRound(lastRound as AuditRound, onlyTask)
                auditWasRun = true
                logger.info { "End runAuditRound ${lastRound.roundIdx} complete=$complete took ${roundStopwatch}" }

            } else {
                complete = lastRound.auditIsComplete
            }
        }

        // on a risk measureing audit, we dont want to do the next estimation round automatically, wait for explicit call.
        val waitOnRisk = auditRecord.config.creation.isRiskMeasuringAudit() && auditWasRun

        // start a new round by estimating the mvrs needed
        if (!complete && !waitOnRisk ) {
            roundIdx++
            // start next round and estimate sample sizes
            logger.info { "Start startNewRound $roundIdx using ${workflow}" }
            val roundStopwatch = Stopwatch()

            if (roundIdx == 1) {
                //// heres where we can remove contests as needed
                // this may change the auditStatus to misformed.
                val results = VerifyResults()
                preAuditContestCheck(auditRecord.contests, results)
                if (results.hasErrors) {
                    logger.warn{ results.toString() }
                } else {
                    logger.info{ results.toString() }
                }
            }
            val nextRound = workflow.startNewRound(quiet = false, onlyTask)

            // get matching mvrs if needed
            if (!nextRound.auditIsComplete && auditRecord.config.election.mvrSource == MvrSource.testPrivateMvrs) {
                val publisher = Publisher(auditDir)
                val ncards = workflow.writeMvrsForRound(roundIdx)
                logger.info{"writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(roundIdx)}"}
            }
            logger.info { "End startNewRound $roundIdx took ${roundStopwatch}: ${nextRound.show()}" }

            return Ok(nextRound)

        } else {
            val lastRound = workflow.auditRounds().last()
            logger.info { "runRound ${lastRound.roundIdx} complete = $complete" }
            return Ok(lastRound)
        }

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}

fun runAllRoundsAndVerify(auditdir: String, maxRounds:Int=7, verify:Boolean = true): Boolean {
    println("============================================================")
    var done = false
    var lastRound: AuditRoundIF? = null

    while (!done) {
        lastRound = runRound(inputDir = auditdir)
        if (lastRound == null) return false
        done = lastRound.auditIsComplete || lastRound.roundIdx > maxRounds
        GeneralAdaptiveBetting.showCounts("round $lastRound")
    }

    if (lastRound != null) {
        println("nrounds = ${lastRound.roundIdx} nmvrs = ${lastRound.nmvrs} auditdir=$auditdir")
    } else {
        println("failed in auditdir=$auditdir")
        return false
    }

    println("============================================================")

    if (verify) {
        val verifyRound = VerifyAuditRoundCommitment(auditdir).verify()
        if (verifyRound.hasErrors) {
            println()
            print(verifyRound)
            logger.error { "runAllRoundsAndVerify VerifyAuditRoundCommitment failed: ${verifyRound}" }
        }
        return (!verifyRound.hasErrors)
    }
    return true
}

// for viewer
fun resampleAndRun(auditdir: String, lastRound: AuditRound): Boolean {
    try {
        val auditRecord = AuditRecord.read(auditdir)!!

        // resample
        val previousSamples = auditRecord.rounds.previousSamplePrns(lastRound.roundIdx)
        // TODO removeContestsAndSample or consistentSampling??
        // removeContestsAndSample(auditRecord.config.round.sampling, auditRecord.readSortedManifest(), lastRound, previousSamples)
        consistentSampling(lastRound, auditRecord.readSortedManifest(), previousSamples)

        // writeAuditState
        val publisher = Publisher(auditdir)
        writeAuditRoundJsonFile(lastRound, publisher.auditEstFile(lastRound.roundIdx))
        logger.info {"resampleAndRun writeAuditEstimation to ${publisher.auditEstFile(lastRound.roundIdx)}"}

        writeSamplePrnsJsonFile(lastRound.samplePrns, publisher.samplePrnsFile(lastRound.roundIdx))
        logger.info {"resampleAndRun ${lastRound.samplePrns.size} samplePrns written to ${publisher.samplePrnsFile(lastRound.roundIdx)}"}

        val workflow = PersistedWorkflow(auditRecord)

        // write matching mvrs if needed
        if (auditRecord.config.election.mvrSource == MvrSource.testPrivateMvrs) {
            val ncards = workflow.writeMvrsForRound(lastRound.roundIdx)
            logger.info{"resampleAndRun writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(lastRound.roundIdx)}"}
        }

        workflow.runAuditRound(lastRound)
        return true

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return false
    }
}