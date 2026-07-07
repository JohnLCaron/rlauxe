package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import java.io.File
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("StartAudit")

fun startFirstRound(topdir: String, onlyTask: OnlyTask? = null, auditorMaxNewMvrs: Int? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("startFirstRound")

    try {
        if (notExists(Path.of(topdir))) {
            return errs.add( "audit Directory $topdir does not exist" )
        }

        // delete any roundX subdirectories
        val topdirFile = File(topdir)
        topdirFile.walkTopDown()
            .filter { it.isDirectory }
            .filter { it.name.startsWith("round") }
            .forEach {
                val ret = it.deleteRecursively()
                println("deleted $it = $ret")
                println()
            }

        val auditRecord = AuditRecord.read(topdir)
        if (auditRecord == null) {
            return errs.add("directory '$topdir' does not contain an audit record")
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

        // in case it changed TODO is this ok ??
        val publisher = auditRecord.publisher
        writeContestsJsonFile(auditRecord.contests, publisher.contestsFile())
        logger.info{"startFirstRound write ${auditRecord.contests.size} contests to ${publisher.contestsFile()}"}

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
            val publisher = Publisher(topdir)
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

