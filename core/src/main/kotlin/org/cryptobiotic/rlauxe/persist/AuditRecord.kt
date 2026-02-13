package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
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

interface AuditRecordIF {
    val location: String
    val config: AuditConfig
    val contests: List<ContestWithAssertions>
    val rounds: List<AuditRoundIF>
}

class AuditRecord(
    override val location: String,
    override val config: AuditConfig,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRound>,  // TODO do we need to replace AuditEst ??
    mvrs: List<AuditableCard> // mvrs already sampled
): AuditRecordIF {
    val previousMvrs = mutableMapOf<Long, AuditableCard>()

    init {
        mvrs.forEach { previousMvrs[it.prn] = it } // cumulative
    }

    // TODO new mvrs vs mvrs may confuse people. Build interface to manage this process?
    fun enterMvrs(mvrs: CloseableIterable<AuditableCard>, errs: ErrorMessages): Boolean {
        val publisher = Publisher(location)
        val lastRoundIdx = if (rounds.isEmpty()) 1 else rounds.last().roundIdx

        // get complete match with sampleNums in last round
        val sampledPrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(lastRoundIdx))
        if (sampledPrnsResult.isErr) {
            logger.error{ "$sampledPrnsResult" } // needed?
            errs.addNested(sampledPrnsResult.component2()!!)
            return false
        }
        val sampledPrns = sampledPrnsResult.unwrap()

        val sampledMvrs = findSamples(sampledPrns, mvrs.iterator())

        // TODO NEXTASK is this all prns or just new? humans want just new
        writeAuditableCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        logger.info{"enterMvrs write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx"}

        sampledMvrs.forEach { previousMvrs[it.prn] = it } // cumulative
        return true
    }

    companion object {

        // used by viewer
        fun readFrom(location: String): AuditRecordIF? {
            val compositeRecord = CompositeRecord.readFrom(location)
            if (compositeRecord != null) return compositeRecord

            val auditRecordResult = readFromResult(location)
            if (auditRecordResult.isOk) {
                return auditRecordResult.unwrap()
            } else {
                logger.error { auditRecordResult.toString() }
                return null
            }
        }

        fun readFromResult(location: String): Result<AuditRecordIF, ErrorMessages> {
            val errs = ErrorMessages("readAuditRecord from '${location}'")

            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val config = if (auditConfigResult.isOk) auditConfigResult.unwrap() else {
                errs.addNested(auditConfigResult.unwrapError())
                null
            }

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults.isOk) contestsResults.unwrap()  else {
                errs.addNested(contestsResults.unwrapError())
                null
            }
            if (errs.hasErrors()) return Err(errs)

            val sampledMvrsAll = mutableListOf<AuditableCard>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.currentRound()) {
                val samplePrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx))
                val samplePrns = if (samplePrnsResult.isOk) samplePrnsResult.unwrap() else {
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

                // AuditStateFile doesnt exist until audit is run
                val auditStateFile = publisher.auditFile(roundIdx)
                if (Files.exists(Path.of(auditStateFile))) {
                    val auditRound = readAuditRoundJsonFile(
                        auditStateFile,
                        contests!!,
                        samplePrns!!,
                    )
                    if (auditRound.isOk) rounds.add(auditRound.unwrap() as AuditRound) else {
                        errs.addNested(auditRound.unwrapError())
                    }
                } else {
                    // TODO if read in AuditEst, replace with AuditState when audit is done....
                    val auditEstFile = publisher.auditEstFile(roundIdx)
                    if (Files.exists(Path.of(auditEstFile))) {
                        val auditEstRound = readAuditRoundJsonFile(
                            auditEstFile,
                            contests!!,
                            samplePrns!!,
                        )
                        if (auditEstRound.isOk) rounds.add(auditEstRound.unwrap() as AuditRound) else {
                            errs.addNested(auditEstRound.unwrapError())
                        }
                    }
                }
            }
            // TODO AuditRecord or CompositeRecord ??
            return if (errs.hasErrors()) Err(errs) else
                Ok(AuditRecord(location, config!!, contests!!, rounds, sampledMvrsAll))
        }
    }
}
