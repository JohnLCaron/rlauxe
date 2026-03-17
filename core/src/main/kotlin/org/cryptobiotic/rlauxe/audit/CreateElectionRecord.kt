package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfoJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeBatchesJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.createZipFile
import kotlin.io.path.Path

interface CreateElectionIF {
    fun electionInfo(): ElectionInfo
    fun contestsUA(): List<ContestWithAssertions>

    // if you immediately write to disk, you only need one pass through the iterator
    fun cards() : CloseableIterator<AuditableCard> // TODO must/should? include batch name?
    fun ncards(): Int

    // probably implementations should put out both ? Let the auditor decide how to use ??
    fun batches(): List<BatchIF>?
    fun cardPools(): List<CardPool>?
    fun createUnsortedMvrsInternal(): List<Cvr>? // for in-memory case, poolId used also as batch name?
    fun createUnsortedMvrsExternal(): CloseableIterator<AuditableCard>? // for out-of-memory case // TODO must/should? include batch name?
}

private val logger = KotlinLogging.logger("CreateElectionRecord")

fun createElectionRecord(election: CreateElectionIF, auditDir: String, clear: Boolean = true) {
    if (clear) clearDirectory(Path(auditDir))

    val publisher = Publisher(auditDir)
    val electionInfo = election.electionInfo()
    writeElectionInfoJsonFile(electionInfo, publisher.electionInfoFile())
    logger.info{"createElectionRecord writeElectionInfoJsonFile to ${publisher.electionInfoFile()}\n  $electionInfo"}

    val batches = election.batches()
    if (!batches.isNullOrEmpty()) {
        writeBatchesJsonFile(batches, publisher.batchesFile())
        logger.info { "createElectionRecord write ${batches.size} batches to ${publisher.batchesFile()}" }
    }

    val cardPools = election.cardPools()
    if (!cardPools.isNullOrEmpty()) {
        writeCardPoolCsvFile(cardPools, publisher.cardPoolsFile())
        logger.info { "createElectionRecord ${cardPools.size} pools to ${publisher.cardPoolsFile()}" }
    }

    val cards = election.cards()
    val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
    // createZipFile(publisher.cardManifestFile(), delete = true)
    logger.info { "createElectionRecord write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

    // write contests
    val contestsUA = election.contestsUA()
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
}
