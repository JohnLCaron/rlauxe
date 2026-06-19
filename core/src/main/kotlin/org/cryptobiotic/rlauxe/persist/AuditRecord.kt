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
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.persist.bin.FastSamplingCardIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readCountyPoolsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
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

    // fun readSortedManifest(): CardManifest
    fun readSortedManifest(styles: List<StyleIF>?): SortedManifest
    fun readSamplingCards(styles: List<StyleIF>?): CloseableIterable<SamplingCardIF>?

    fun readOneShotMvrs(): Map<Int, Int>
    fun readCardStyles(): List<StyleIF>?
    fun name(): String
    fun auditdir() : String
}

open class AuditRecord(
    override val location: String,
    override val config: Config,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRound>,
    val nmvrs: Int // number of mvrs already sampled
): AuditRecordIF {
    val publisher = Publisher(location)
    val electionInfo = config.election

    override fun auditdir() = location // it.location.substring(stateRecord.location.length)
    override fun name() = electionInfo.electionName // it.location.substring(stateRecord.location.length)

    override fun readSamplingCards(styles: List<StyleIF>?): CloseableIterable<SamplingCardIF>? {
        if (styles == null || !Files.exists(Path(publisher.fastSamplingFile()))) return null
        return CloseableIterable {
            logger.info{"readSamplingCards at ${publisher.fastSamplingFile()}"}
            FastSamplingCardIterator(publisher.fastSamplingFile(), styles, bufferSize = 100_000)
        }
    }

    // TODO should styles be optional ?
    override fun readSortedManifest(styles: List<StyleIF>?): SortedManifest {
        // first look for sortedCardsProtoFile, use if present, else use sortedCardsFile
        if ( Files.exists(Path(publisher.sortedCardsProtoFile()))) {
            val sortedCardsIter = CloseableIterable { ProtoCardIterator(publisher.sortedCardsProtoFile(), styles = styles) }
            return SortedManifest(sortedCardsIter, electionInfo.totalCardCount)
        }

        val mergedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile(), styles) }
        return SortedManifest(mergedCards, electionInfo.totalCardCount)
    }

    override fun readCardStyles(): List<StyleIF>? {
        return if (!Files.exists(Path(publisher.cardStylesFile()))) null else {
            val stylesResult = readCardStylesJsonFile(publisher.cardStylesFile())
            if (stylesResult.isOk) stylesResult.unwrap() else {
                logger.error{ "$stylesResult" }
                null
            }
        }
    }

    fun readCardPools(): List<CardPool>? {
        val infos = contests.map { it.contest.info() }.associateBy { it.id }
        return if (!Files.exists(Path(publisher.cardPoolsFile()))) null
        else readCardPoolCsvFile(publisher.cardPoolsFile(), infos)
    }

    fun readCountyCardPools(styles: List<StyleIF>): List<CountyPools>? {
        return if (!Files.exists(Path(publisher.countyCardPoolsFile()))) null
        else readCountyPoolsCsvFile(publisher.countyCardPoolsFile(), styles)
    }

    fun readCountyCvrPools(styles: List<StyleIF>): List<CountyPools>? {
        return if (!Files.exists(Path(publisher.countyCvrPoolsFile()))) null
        else readCountyPoolsCsvFile(publisher.countyCvrPoolsFile(), styles)
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
            if (CountyAudit.checkExists(location)) return true
            if (CountyComposite.checkExists(location)) return true
            if (CompositeAuditRecord.checkExists(location)) return true
            if (checkAuditRecordExists(location)) return true
            return false
        }

        // check for just an AuditRecord
        fun checkAuditRecordExists(location: String?): Boolean {
            if (location == null) return false
            val publisher = Publisher(location)
            if (!exists(publisher.electionInfoFile())) return false
            if (!exists(publisher.sortedCardsProtoFile()) && !exists(publisher.cardManifestFile())) return false
            if (!exists(publisher.contestsFile())) return false
            return true
        }

        // reads all types of AuditRecordIF
        fun read(location: String): AuditRecordIF? {

            if (CountyAudit.checkExists(location)) {
                val countyAudit = CountyAudit.readFrom(location)
                if (countyAudit != null) return countyAudit
            }

            if (CountyComposite.checkExists(location)) {
                val countyComposite = CountyComposite.readFrom(location)
                if (countyComposite != null) return countyComposite
            }

            if (CompositeAuditRecord.checkExists(location)) {
                val compositeRecord = CompositeAuditRecord.readFrom(location)
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
