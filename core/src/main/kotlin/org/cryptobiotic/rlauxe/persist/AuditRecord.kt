package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.findSamples
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("AuditRecord")
private val showMissing = true

class AuditRecord(
    val location: String,
    val config: AuditConfig,
    val contests: List<ContestUnderAudit>,
    val rounds: List<AuditRound>,
    mvrs: List<AuditableCard> // mvrs already sampled
) {
    val previousMvrs = mutableMapOf<Long, AuditableCard>() // TODO not used ??

    init {
        mvrs.forEach { previousMvrs[it.prn] = it } // cumulative
    }

    // TODO new mvrs vs mvrs may confuse people. Build interface to manage this process?
    fun enterMvrs(mvrs: CloseableIterable<AuditableCard>, errs: ErrorMessages): Boolean {
        // val mvrMap = mvrs.associateBy { it.prn }.toMap()

        val publisher = Publisher(location)
        val lastRoundIdx = if (rounds.isEmpty()) 1 else rounds.last().roundIdx

        // get complete match with sampleNums in last round
        val sampledPrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(lastRoundIdx))
        if (sampledPrnsResult is Err) {
            logger.error{ "$sampledPrnsResult" } // needed?
            errs.addNested(sampledPrnsResult.error)
            return false
        }
        val sampledPrns = sampledPrnsResult.unwrap()

        val sampledMvrs = findSamples(sampledPrns, mvrs.iterator())
        // wantedMvrs.forEach { previousMvrs[it.prn] = it }
/*
        val missingMvrs = mutableListOf<Long>()
        sampledPrns.forEach { sampleNumber ->
            var mvr = previousMvrs[sampleNumber]
            if (mvr == null) {
                mvr = mvrMap[sampleNumber]
                if (mvr == null) {
                    missingMvrs.add(sampleNumber)
                } else {
                    previousMvrs[sampleNumber] = mvr
                }
            }
        }
        if (missingMvrs.isNotEmpty()) {
            errs.add("Cant find MVRs that match ${publisher.samplePrnsFile(lastRoundIdx)} ")
            val useMissing = if (missingMvrs.size > 10) missingMvrs.subList(0, 10) else missingMvrs
            errs.add(" missingMvs =  ${useMissing} ")
            return false
        } */

        // val sampledMvrs = sampledPrns.map{ sampleNumber -> previousMvrs[sampleNumber]!! }
        // TODO only writing the mvrs that match samplePrnsFile for this round
        writeAuditableCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        logger.info{"enterMvrs write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx"}

        sampledMvrs.forEach { previousMvrs[it.prn] = it } // cumulative
        return true
    }

    companion object {

        fun readFromResult(location: String): Result<AuditRecord, ErrorMessages> {
            val errs = ErrorMessages("readAuditRecord from '${location}'")

            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val config = if (auditConfigResult is Ok) auditConfigResult.unwrap() else {
                errs.addNested(auditConfigResult.unwrapError())
                null
            }

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()  else {
                errs.addNested(contestsResults.unwrapError())
                null
            }
            if (errs.hasErrors()) return Err(errs)

            val sampledMvrsAll = mutableListOf<AuditableCard>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.currentRound()) {
                val samplePrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx))
                val samplePrns = if (samplePrnsResult is Ok) samplePrnsResult.unwrap() else {
                    errs.addNested(samplePrnsResult.unwrapError())
                    null
                }
                if (errs.hasErrors()) return Err(errs)

                // may not exist yet
                val mvrsForRoundFile = publisher.sampleMvrsFile(roundIdx)
                val sampledMvrs = if (Files.exists(Path.of(mvrsForRoundFile))) {
                    readAuditableCardCsvFile(mvrsForRoundFile)
                } else {
                    emptyList()
                }
                sampledMvrsAll.addAll(sampledMvrs) // cumulative

                // may not exist yet
                val auditRoundFile = publisher.auditRoundFile(roundIdx)
                if (Files.exists(Path.of(auditRoundFile))) {
                    val auditRoundResult = readAuditRoundJsonFile(
                        auditRoundFile,
                        contests!!,
                        samplePrns!!,
                    )
                    if (auditRoundResult is Ok) rounds.add(auditRoundResult.unwrap()) else {
                        errs.addNested(auditRoundResult.unwrapError())
                    }
                }
            }
            return if (errs.hasErrors()) Err(errs) else
                Ok(AuditRecord(location, config!!, contests!!, rounds, sampledMvrsAll))
        }
    }
}
