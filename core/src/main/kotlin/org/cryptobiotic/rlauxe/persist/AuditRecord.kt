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
import org.cryptobiotic.rlauxe.audit.Batch
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCards
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.CardManifest
import org.cryptobiotic.rlauxe.workflow.findSamples
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
    mvrs: List<AuditableCard> // mvrs already sampled
): AuditRecordIF {
    override val config = Config(electionInfo, auditCreationConfig, auditRoundConfig)

    val previousMvrs = mutableMapOf<Long, AuditableCard>() // cumulative
    val publisher = Publisher(location)

    init {
        mvrs.forEach { previousMvrs[it.prn] = it }
    }

    // TODO maybe better on workflow ??
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

    // for efficiency
    override fun readSortedManifest(batches: List<BatchIF>?): CardManifest {
        if (batches != null && batches.isNotEmpty()) {
            // merge batch references into the Card
            val mergedCards =
                MergeBatchesIntoCards(
                    CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                    batches,
                )

            return CardManifest(mergedCards, electionInfo.totalCardCount, batches)
        }
        // no batches so you dont need to merge
        val sortedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) }
        return CardManifest(sortedCards, electionInfo.totalCardCount, emptyList())
    }

    override fun readSortedManifest(): CardManifest {
        val batches = readBatches() ?: readCardPools() // TODO which  is preferred ??
        if (batches != null && batches.isNotEmpty()) {
            // merge batch references into the Card
            val mergedCards =
                MergeBatchesIntoCards(
                    CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                    batches,
                )

            return CardManifest(mergedCards, electionInfo.totalCardCount, batches)
        }
        // no batches so you dont need to merge
        val sortedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) }
        return CardManifest(sortedCards, electionInfo.totalCardCount, emptyList())
    }

    override fun readBatches(): List<Batch>? {
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

    // contestId -> nmvrs
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

            /* val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val config = if (auditConfigResult.isOk) auditConfigResult.unwrap() else {
                errs.addNested(auditConfigResult.unwrapError())
                null
            } */

            // new way of storing config
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

            val sampledMvrsAll = mutableListOf<AuditableCard>()

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
                        prevAuditRound,
                    )
                    if (auditRound.isOk) {
                        prevAuditRound = auditRound.unwrap() as AuditRound
                        rounds.add(prevAuditRound)
                    } else {
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
                            prevAuditRound,
                        )
                        if (auditEstRound.isOk) {
                            prevAuditRound = auditEstRound.unwrap() as AuditRound
                            rounds.add(auditEstRound.unwrap() as AuditRound)
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

                /* if (auditCreationConfig != null && auditRoundConfig != null) {
                    val configNew = Config(electionInfo!!, auditCreationConfig, auditRoundConfig)
                    val auditConfigNew = configNew.toAuditConfig()
                    if (auditConfigNew != config) {
                        println("readAuditConfigJsonFile= $config")
                        println("configNew= ${configNew}")
                        println("configNew.toAuditConfig()= $auditConfigNew")
                        logger.error{ "configNew != auditConfig "}
                        throw RuntimeException("configNew != auditConfig")
                    }
                } */
            }
            val roundConfig = lastRoundConfig?: auditRoundProtoConfig!!

            // TODO AuditRecord or CompositeRecord ??
            return if (errs.hasErrors()) Err(errs) else {
                Ok(AuditRecord(location, electionInfo!!, auditCreationConfig!!, roundConfig, contests!!, rounds, sampledMvrsAll))
            }
        }
    }
}
