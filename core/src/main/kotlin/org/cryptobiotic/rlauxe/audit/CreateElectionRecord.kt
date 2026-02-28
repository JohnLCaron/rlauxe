package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfo2JsonFile
import org.cryptobiotic.rlauxe.persist.json.writePopulationsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.createZipFile
import kotlin.io.path.Path

interface CreateElectionIF2 {
    fun electionInfo(): ElectionInfo2
    fun contestsUA(): List<ContestWithAssertions>
    // if you immediately write to disk, you only need one pass through the iterator
    fun cards() : CloseableIterator<AuditableCard> // doesnt need merged populations i think?
    fun ncards(): Int
    fun populations(): List<PopulationIF>?
    fun makeCardPools(): List<OneAuditPool>?
    fun createUnsortedMvrs(): List<Cvr>  // TODO this should be CloseableIterator<Cvr> for out-of-memory case
}

private val logger = KotlinLogging.logger("CreateElectionRecord")

fun createElectionRecord(name: String, election: CreateElectionIF2, auditDir: String, clear: Boolean = true) {
    if (clear) clearDirectory(Path(auditDir))

    val publisher = Publisher(auditDir)
    val electionInfo = election.electionInfo()
    writeElectionInfo2JsonFile(electionInfo, publisher.electionInfoFile())
    logger.info{"CreateAuditRecord writeElectionInfoJsonFile to ${publisher.electionInfoFile()}\n  $electionInfo"}

    val populations = election.populations()
    if (!populations.isNullOrEmpty()) {
        writePopulationsJsonFile(populations, publisher.populationsFile())
        logger.info { "CreateAuditRecord write ${populations.size} populations, to ${publisher.populationsFile()}" }
    }

    val cardPools = election.makeCardPools()
    if (!cardPools.isNullOrEmpty()) {
        writeCardPoolCsvFile(cardPools, publisher.oneauditPoolsFile())
        logger.info { "writeCardPoolCsvFile ${cardPools.size} pools to ${publisher.oneauditPoolsFile()}" }
    }

    val cards = election.cards()
    val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
    createZipFile(publisher.cardManifestFile(), delete = true)
    logger.info { "CreateAuditRecord write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

    // write contests
    val contestsUA = election.contestsUA()
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"CreateAuditRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
}
