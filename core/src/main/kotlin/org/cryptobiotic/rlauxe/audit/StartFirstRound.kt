package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import java.io.File
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("StartAudit")

fun startFirstRound(auditDir: String, onlyTask: OnlyTask? = null, auditorMaxNewMvrs: Int? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("startFirstRound")

    try {
        if (notExists(Path.of(auditDir))) {
            return errs.add( "audit Directory $auditDir does not exist" )
        }

        // delete any roundX subdirectories
        val auditDirFile = File(auditDir)
        auditDirFile.walkTopDown()
            .filter { it.isDirectory }
            .filter { it.name.startsWith("round") }
            .forEach {
                val ret = it.deleteRecursively()
                println("deleted $it = $ret")
                println()
            }

        val auditRecord = AuditRecord.read(auditDir)
        if (auditRecord == null) {
            return errs.add("directory '$auditDir' does not contain an audit record")
        }
        require(auditRecord is AuditRecord)

        val config = auditRecord.config
        val workflow = PersistedWorkflow(auditRecord)
        val roundIdx = 1

        // TODO not needed here ?? Done in CreateElectionRecord
        //// heres where we can remove contests as needed
        // this may change the auditStatus to misformed.
        val results = VerifyResults()
        preAuditContestCheck(auditRecord.contests,  config.sampling, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // start next round and estimate sample sizes
        logger.info { "startFirstRound using ${workflow}" }
        val roundStopwatch = Stopwatch()
        val nextRound = workflow.startNewRound(quiet = false, onlyTask, auditorMaxNewMvrs)

        // get matching mvrs if needed
        if (auditRecord.config.mvrSource == MvrSource.testPrivateMvrs) {
            val publisher = Publisher(auditDir)
            val ncards = workflow.writeMvrsForRound(roundIdx)
            logger.info{"writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(roundIdx)}"}
        }

        logger.info { "startFirstRound took ${roundStopwatch}: ${nextRound.show()}" }
        return Ok(nextRound)

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}