package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
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
    val config: Config
    val contests: List<ContestWithAssertions>
    val rounds: List<AuditRoundIF>

    fun readSortedManifest(): CardManifest
    fun readSortedManifest(batches: List<StyleIF>?): CardManifest
    fun readOneShotMvrs(): Map<Int, Int>
    fun readCardStyles(): List<StyleIF>?
    fun name(): String
}

open class AuditRecord(
    override val location: String,
    override val config: Config,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRound>,  // TODO do we need to replace AuditEst ??
    val nmvrs: Int // number of mvrs already sampled
): AuditRecordIF {
    val publisher = Publisher(location)
    val electionInfo = config.election

    override fun name() = electionInfo.electionName // it.location.substring(stateRecord.location.length)

    // for efficiency, batches can be read once and stored by the caller
    override fun readSortedManifest(batches: List<StyleIF>?): CardManifest {
        // merge batch references into the Card
        val mergedCards: CloseableIterable<AuditableCard> =
            MergeBatchesIntoCardManifestIterable(
                CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                batches ?: emptyList(),
            )
        return CardManifest(mergedCards, electionInfo.totalCardCount)
    }

    override fun readSortedManifest(): CardManifest {
        val batches = readCardPools() ?: readCardStyles() ?: emptyList() // pools are preferred
        // merge batch references into the Card
        val mergedCards =
            MergeBatchesIntoCardManifestIterable(
                CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                batches,
            )

        return CardManifest(mergedCards, electionInfo.totalCardCount)
    }

    override fun readCardStyles(): List<StyleIF>? {
        return if (!Files.exists(Path(publisher.cardStylesFile()))) null else {
            val batchesResult = readCardStylesJsonFile(publisher.cardStylesFile())
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

    override fun toString() = buildString {
        append("AuditRecord ${name()} location='$location'\n$config")
        appendLine("contests")
        contests.forEach{ appendLine("  $it")}
        appendLine("rounds")
        rounds.forEach{ appendLine(it)}
    }

    companion object {
        private val logger = KotlinLogging.logger("AuditRecord")

        // checks all types of AuditRecordIF
        fun checkExists(location: String?): Boolean {
            if (location == null) return false
            if (CountyComposite.checkExists(location)) return true
            if (CompositeRecord.checkExists(location)) return true
            if (checkAuditRecordExists(location)) return true
            return false
        }

        // check for just an AuditRecord
        fun checkAuditRecordExists(location: String?): Boolean {
            if (location == null) return false
            val publisher = Publisher(location)
            if (!exists(publisher.electionInfoFile())) return false
            if (!exists(publisher.cardManifestFile())) return false
            if (!exists(publisher.contestsFile())) return false
            return true
        }

        // reads all types of AuditRecordIF
        fun read(location: String): AuditRecordIF? {

            if (CountyComposite.checkExists(location)) {
                val countyComposite = CountyComposite.readFrom(location)
                if (countyComposite != null) return countyComposite
            }

            if (CompositeRecord.checkExists(location)) {
                val compositeRecord = CompositeRecord.readFrom(location)
                if (compositeRecord != null) return compositeRecord
            }

            val auditRecordResult = readWithResult(location)
            if (auditRecordResult.isOk) {
                return auditRecordResult.unwrap()
            } else {
                logger.error { auditRecordResult.toString() }
                return null
            }
        }

        // reads an AuditRecord
        fun readWithResult(location: String): Result<AuditRecord, ErrorMessages> {
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

            return if (errs.hasErrors()) Err(errs) else {
                val config = Config(electionInfo!!, auditCreationConfig!!, roundConfig)
                Ok(AuditRecord(location, config, contests!!, rounds, countMvrsUsed))
            }
        }
    }
}
