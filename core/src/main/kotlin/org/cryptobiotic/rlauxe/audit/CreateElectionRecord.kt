package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCountyCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfoJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardStylesJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.verify.VerifyElectionCommitment
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import java.nio.file.Path
import kotlin.io.path.Path

interface ElectionBuilder {
    fun electionInfo(): ElectionInfo
    fun contestsUA(): List<ContestWithAssertions>

    // if you immediately write to disk, you only need one pass through the cards iterator
    fun cards() : CloseableIterator<AuditableCard> // not sorted, dont need styles added yet
    fun ncards(): Int

    // In EstimateAudit, we want to use pools to estimate with, if they exist. So the merging needs to merge pools, not the batches.
    // So dont write batches if there are pools. Also its up to the reader to prefer pools.
    fun cardStyles(): List<StyleIF>?
    fun cardPools(): List<CardPoolIF>?
    fun countyCardPools(): List<CountyPoolsIF>? = null

    // if (config.election.mvrSource == MvrSource.testPrivateMvrs), supply one or the other:
    fun unsortedMvrsInternal(): List<AuditableCard>? // for in-memory case, poolId used also as batch name?
    fun unsortedMvrsExternal(): CloseableIterator<AuditableCard>? // for out-of-memory case
}

private val logger = KotlinLogging.logger("CreateElectionRecord")

fun createElectionRecord(election: ElectionBuilder, auditDir: String, control: ContestSampleControl? = null, clear: Boolean = true, validate: Boolean = false) {
    if (clear) clearDirectory(Path(auditDir))

    val errs = validateOutputDir(Path.of(auditDir))
    if (errs.hasErrors()) {
        logger.error { errs.toString() }
        throw RuntimeException(errs.toString())
    }
    val publisher = Publisher(auditDir)
    val electionInfo = election.electionInfo()
    writeElectionInfoJsonFile(electionInfo, publisher.electionInfoFile())
    logger.info{"createElectionRecord writeElectionInfoJsonFile to ${publisher.electionInfoFile()}\n  $electionInfo"}

    val cardStyles = election.cardStyles()
    if (!cardStyles.isNullOrEmpty()) {
        writeCardStylesJsonFile(cardStyles, publisher.cardStylesFile())
        logger.info { "createElectionRecord write ${cardStyles.size} cardStyles to ${publisher.cardStylesFile()}" }
    }

    val cardPools = election.cardPools()
    if (!cardPools.isNullOrEmpty()) {
        writeCardPoolCsvFile(cardPools, publisher.cardPoolsFile())
        logger.info { "createElectionRecord ${cardPools.size} cardPools to ${publisher.cardPoolsFile()}" }
    }

    val countyCardPools = election.countyCardPools()
    if (!countyCardPools.isNullOrEmpty()) {
        writeCountyCardPoolCsvFile(countyCardPools, publisher.countyCardPoolsFile())
        logger.info { "createElectionRecord ${countyCardPools.size} countyCardPoolsFile to ${publisher.countyCardPoolsFile()}" }
    }

    val cards = election.cards()
    val countCvrs = writeCardCsvFile(cards, publisher.cardManifestFile())
    // createZipFile(publisher.cardManifestFile(), delete = true)
    logger.info { "createElectionRecord write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

    // by calling preAuditContestCheck here, we change the contest.preAuditStatus before the contests are written
    // but we dont have the ContestSampleControl yet.
    // TODO move minMargin checks to electionInfo? Or skip that here, but allow to change later ??
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $auditDir")
    preAuditContestCheck(contestsUA, control, results)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

    // taking forever - make optional
    if (validate) {
        val verifyECResults = VerifyElectionCommitment(auditDir).verify()
        if (verifyECResults.hasErrors) {
            logger.error { "createElectionRecord VerifyElectionCommitment failed: ${verifyECResults}" }
        }
    }
}
