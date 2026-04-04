package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

interface AuditRecordIF {
    val location: String
    val electionInfo: ElectionInfo
    val config: Config
    val contests: List<ContestWithAssertions>
    val rounds: List<AuditRoundIF>

    fun readSortedManifest(): CardManifest
    fun readSortedManifest(batches: List<BatchIF>?): CardManifest
    fun readOneShotMvrs(): Map<Int, Int>
    fun readBatches(): List<BatchIF>?
}

class AuditRecord(
    override val location: String,
    override val electionInfo: ElectionInfo,
    val auditCreationConfig: AuditCreationConfig,
    val auditRoundConfig: AuditRoundConfig,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRound>,  // TODO do we need to replace AuditEst ??
    val nmvrs: Int // number of mvrs already sampled
): AuditRecordIF {
    val publisher = Publisher(location)

    override val config = Config(electionInfo, auditCreationConfig, auditRoundConfig)

    // for efficiency, batches can be read once and stored by the caller
    override fun readSortedManifest(batches: List<BatchIF>?): CardManifest {
        // merge batch references into the Card
        val mergedCards: CloseableIterable<AuditableCard> =
            MergeBatchesIntoCardManifestIterable(
                CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                batches ?: emptyList(),
            )
        return CardManifest(mergedCards, electionInfo.totalCardCount)
    }

    override fun readSortedManifest(): CardManifest {
        val batches = readCardPools() ?: readBatches() ?: emptyList() // pools are preferred
        // merge batch references into the Card
        val mergedCards =
            MergeBatchesIntoCardManifestIterable(
                CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                batches,
            )

        return CardManifest(mergedCards, electionInfo.totalCardCount)
    }

    override fun readBatches(): List<BatchIF>? {
        return if (!Files.exists(Path(publisher.batchesFile()))) null else {
            val batchesResult = readBatchesJsonFile(publisher.batchesFile())
            if (batchesResult.isOk) batchesResult.unwrap() else {
                logger.error{ "$batchesResult" }
                null
            }
        }
    }

    fun readCardPools(): List<CardPool>? {
        val infos = contests.map { it.contest.info() }.associateBy { it.id }
        return if (!Files.exists(Path(publisher.cardPoolsFile()))) null
               else readCardPoolCsvFile(publisher.cardPoolsFile(), infos)
    }

    // return contestId -> nmvrs
    override fun readOneShotMvrs(): Map<Int, Int> {
        if (!Files.exists(Path(publisher.privateOneshotFile()))) {
            return emptyMap()
        }
        val file = File(publisher.privateOneshotFile())

        val result = mutableMapOf<Int, Int>()
        file.useLines { lines ->
            lines.forEach { line ->
                val tokens = line.split(":")
                val id = tokens[0].trim().toInt()
                val nmvrs = tokens[1].trim().toInt()
                result[id] = nmvrs

            }
        }
        return result
    }

    fun showName(): String {
        return "audit $location election ${electionInfo.electionName}"
    }

    companion object {
        private val logger = KotlinLogging.logger("AuditRecord")

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
            val electionInfoResult = readElectionInfoJsonFile(publisher.electionInfoFile())
            val electionInfo = if (electionInfoResult.isOk) electionInfoResult.unwrap() else {
                errs.addNested(electionInfoResult.unwrapError())
                null
            }

            val auditCreationConfigResult = readAuditCreationConfigJsonFile(publisher.auditCreationConfigFile())
            val auditCreationConfig = if (auditCreationConfigResult.isOk) auditCreationConfigResult.unwrap() else {
                errs.addNested(auditCreationConfigResult.unwrapError())
                null
            }

            val auditRoundConfigResult = readAuditRoundConfigJsonFile(publisher.auditRoundProtoFile())
            val auditRoundProtoConfig = if (auditRoundConfigResult.isOk) auditRoundConfigResult.unwrap() else {
                errs.addNested(auditRoundConfigResult.unwrapError())
                null
            }

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults.isOk) contestsResults.unwrap()  else {
                errs.addNested(contestsResults.unwrapError())
                null
            }
            if (errs.hasErrors()) return Err(errs)

            var countMvrsUsed = 0

            var lastRoundConfig: AuditRoundConfig? = null
            var prevAuditRound : AuditRound? = null
            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.currentRound()) {
                val samplePrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx))
                val samplePrns = if (samplePrnsResult.isOk) samplePrnsResult.unwrap() else {
                    errs.addNested(samplePrnsResult.unwrapError())
                    null
                }
                if (errs.hasErrors()) return Err(errs)

                // AuditStateFile doesnt exist until audit is run
                val auditStateFile = publisher.auditFile(roundIdx)
                if (Files.exists(Path.of(auditStateFile))) {
                    val auditRound = readAuditRoundJsonFile(
                        auditStateFile,
                        contests!!,
                        samplePrns!!,
                        prevAuditRound,
                    )
                    if (auditRound.isOk) {
                        prevAuditRound = auditRound.unwrap() as AuditRound
                        rounds.add(prevAuditRound)
                        countMvrsUsed += prevAuditRound.newmvrs
                    } else {
                        errs.addNested(auditRound.unwrapError())
                    }
                } else {
                    val auditEstFile = publisher.auditEstFile(roundIdx)
                    if (Files.exists(Path.of(auditEstFile))) {
                        val auditEstRound = readAuditRoundJsonFile(
                            auditEstFile,
                            contests!!,
                            samplePrns!!,
                            prevAuditRound,
                        )
                        if (auditEstRound.isOk) {
                            prevAuditRound = auditEstRound.unwrap() as AuditRound
                            rounds.add(auditEstRound.unwrap() as AuditRound)
                            countMvrsUsed += prevAuditRound.newmvrs  // nonzero ?

                        } else {
                            errs.addNested(auditEstRound.unwrapError())
                        }
                    }
                }

                // new way of storing config
                // readAuditRoundConfigJsonFile(filename: String): Result<AuditRoundConfig, ErrorMessages>
                val auditRoundConfigResult = readAuditRoundConfigJsonFile(publisher.auditRoundConfigFile(roundIdx))
                val auditRoundConfig = if (auditRoundConfigResult.isOk) auditRoundConfigResult.unwrap() else {
                    errs.addNested(auditRoundConfigResult.unwrapError())
                    null
                }
                if (auditRoundConfig != null) lastRoundConfig = auditRoundConfig
            }
            val roundConfig = lastRoundConfig?: auditRoundProtoConfig!!

            // TODO AuditRecord or CompositeRecord ??
            return if (errs.hasErrors()) Err(errs) else {
                Ok(AuditRecord(location, electionInfo!!, auditCreationConfig!!, roundConfig, contests!!, rounds, countMvrsUsed))
            }
        }
    }
}
