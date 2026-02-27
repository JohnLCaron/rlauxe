package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import java.io.File
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("StartAudit")

// one could rerun this to overwrite config and sorted cards, using the same election record
fun createAuditRecord(config: AuditConfig, election: CreateElectionIF2, auditDir: String) {
    val publisher = Publisher(auditDir)

    writeAuditConfigJsonFile(config, publisher.auditConfigFile())
    logger.info{"createAuditRecord writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

    // cant write the sorted cards until after seed is generated, after commitment to cardManifest
    writeSortedCardsInternalSort(publisher, config.seed)

    // cant write the sorted mvrs until after sortedCards is written
    if (config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
        val unsortedMvrs = election.createUnsortedMvrs()
        writeUnsortedPrivateMvrs(publisher, unsortedMvrs, seed = config.seed)
        logger.info{"createAuditRecord writeUnsortedPrivateMvrs to ${publisher.privateMvrsFile()}"}
    }
}

fun startFirstRound(auditDir: String, onlyTask: String? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("startFirstRound")

    try {
        if (notExists(Path.of(auditDir))) {
            return errs.add( "audit Directory $auditDir does not exist" )
        }

        // delete any roundX subdirectories
        val auditDirDile = File(auditDir)
        auditDirDile.walkTopDown()
            .filter { it.isDirectory }
            .filter { it.name.startsWith("round") }
            .forEach {
                val ret = it.deleteRecursively()
                println("deleted $it = $ret")
                println()
            }

        val auditRecord = AuditRecord.readFrom(auditDir)
        if (auditRecord == null) {
            return errs.add("directory '$auditDir' does not contain an audit record")
        }
        require(auditRecord is AuditRecord)

        val workflow = PersistedWorkflow(auditRecord)
        val roundIdx = 1
        // probably should overwrite round1 and delete other rounds ??

        //// heres where we can remove contests as needed
        // this may change the auditStatus to misformed.
        val results = VerifyResults()
        checkContestsCorrectlyFormed(auditRecord.config, auditRecord.contests, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // start next round and estimate sample sizes
        logger.info { "startFirstRound using ${workflow}" }
        val roundStopwatch = Stopwatch()

        // this writes auditEstFile and samplePrns files
        val nextRound = workflow.startNewRound(quiet = false, onlyTask)

        // save testPrivateMvrs if needed
        if (auditRecord.config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
            val publisher = Publisher(auditDir)
            val ncards = writeMvrsForRound(publisher, roundIdx)
            logger.info{"writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(roundIdx)}"}
        }
        logger.info { "startFirstRound took ${roundStopwatch}: ${nextRound.show()}" }

        return Ok(nextRound)

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}