package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfoJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeBatchesJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.verify.VerifyElectionCommitment
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import kotlin.io.path.Path

interface ElectionBuilder {
    fun electionInfo(): ElectionInfo
    fun contestsUA(): List<ContestWithAssertions>

    // if you immediately write to disk, you only need one pass through the cards iterator
    fun cards() : CloseableIterator<CardWithBatchName>
    fun ncards(): Int

    // In EstimateAudit, we want to use pools to estimate with, if they exist. So the merging needs to merge pools, not the batches.
    // So dont write batches if there are pools. Also its up to the reader to prefer pools.
    fun batches(): List<BatchIF>?
    fun cardPools(): List<CardPoolIF>?

    fun createUnsortedMvrsInternal(): List<Cvr>? // for in-memory case, poolId used also as batch name?
    // TODO CloseableIterator<Cvr> ??
    fun createUnsortedMvrsExternal(): CloseableIterator<CardWithBatchName>? // for out-of-memory case
}

private val logger = KotlinLogging.logger("CreateElectionRecord")

fun createElectionRecord(election: ElectionBuilder, auditDir: String, clear: Boolean = true) {
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
    val countCvrs = writeCardCsvFile(cards, publisher.cardManifestFile())
    // createZipFile(publisher.cardManifestFile(), delete = true)
    logger.info { "createElectionRecord write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

    // by calling preAuditContestCheck here, we change the contest.preAuditStatus before the contests are written
    // but we dont have the ContestSampleControl yet. TODO ??
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $auditDir")
    preAuditContestCheck(contestsUA, results)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

    val verifyECResults = VerifyElectionCommitment(auditDir).verify()
    if (verifyECResults.hasErrors) {
        logger.error { "createElectionRecord VerifyElectionCommitment failed: ${verifyECResults}" }
    }
}
