package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ToAuditableCardPolling
import org.cryptobiotic.rlauxe.util.ToAuditableCardPooled
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import kotlin.collections.forEach
import kotlin.io.path.Path

interface CreateElectionIF {
    fun contestsUA(): List<ContestUnderAudit>
    fun cardPools(): List<CardPoolIF>? // only if OneAudit

    fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?>  // (cvrs, mvrs) including phantoms
}

private val logger = KotlinLogging.logger("CreateAudit")

class CreateAudit(val name: String, val topdir: String, val config: AuditConfig, election: CreateElectionIF, auditdir: String? = null, clear: Boolean = true) {

    val auditDir = auditdir ?: "$topdir/audit"
    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        logger.info{"writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

        val cardPools = if (config.isOA) {
                val pools = election.cardPools()!!
                writeCardPoolsJsonFile(pools, publisher.cardPoolsFile())
                logger.info { "write ${pools.size} cardPools, to ${publisher.cardPoolsFile()}" }
                pools
            } else null

        val contestsUA = election.contestsUA()
        if (config.isOA) {
            addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, cardPools!!)
        } else {
            contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
        }
        logger.info { "added ClcaAssertions from reported margin " }

        val (cards, mvrs) = election.allCvrs()
        require (cards != null || mvrs != null)
        if (cards != null) {
            val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
            createZipFile(publisher.cardManifestFile(), delete = true)
            logger.info { "write ${countCvrs} cards to ${publisher.cardManifestFile()}" }
        }

        if (mvrs != null) {
            validateOutputDirOfFile(publisher.testMvrsFile())
            val countMvrs = writeAuditableCardCsvFile(mvrs, publisher.testMvrsFile())
            createZipFile(publisher.testMvrsFile(), delete = true)
            logger.info { "write ${countMvrs} cards to ${publisher.testMvrsFile()}" }

            // mvrs may be created randomly, so cant be reproduced, ie can get a new iterator.
            // if cards arent supplied, we have to read the mvrs we just wrote
            val mvrIter = if (cards == null) readCardsCsvIterator(publisher.testMvrsFile()) else null

            if (cards == null && config.isClca) { // just make a copy
                val countCvrs = writeAuditableCardCsvFile(mvrIter!!, publisher.cardManifestFile())
                createZipFile(publisher.cardManifestFile(), delete = true)
                logger.info { "copy ${countCvrs} cards to ${publisher.cardManifestFile()}" }
            }

            if (cards == null && config.isOA) { // remove pooled votes
                val countCvrs = writeAuditableCardCsvFile(ToAuditableCardPooled(mvrIter!!), publisher.cardManifestFile())
                createZipFile(publisher.cardManifestFile(), delete = true)
                logger.info { "copy ${countCvrs} cards to ${publisher.cardManifestFile()} remove pooled votes" }
            }

            if (cards == null && config.isPolling) { // remove all votes
                val countCvrs = writeAuditableCardCsvFile(ToAuditableCardPolling(mvrIter!!), publisher.cardManifestFile())
                createZipFile(publisher.cardManifestFile(), delete = true)
                logger.info { "copy ${countCvrs} cards to ${publisher.cardManifestFile()} remove all votes" }
            }
        }

        // this may change the auditStatus to misformed
        val results = VerifyResults()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // sf only writes these:
        // val auditableContests: List<OAContestUnderAudit> = contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    }
}

fun writeSortedCardsInternalSort(publisher: Publisher, seed: Long) {
    val cvrs = readCardsCsvIterator(publisher.cardManifestFile())
    val sortedCvrs = createSortedCards(cvrs, seed)
    val countCvrs = writeAuditableCardCsvFile(Closer(sortedCvrs.iterator()), publisher.sortedCardsFile())
    createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"write ${countCvrs} cards to ${publisher.sortedCardsFile()}"}

    if (existsOrZip(publisher.testMvrsFile())) {
        val mvrs = readCardsCsvIterator(publisher.testMvrsFile())
        val sortedMvrs = createSortedCards(mvrs, seed)
        val countMvrs = writeAuditableCardCsvFile(Closer(sortedMvrs.iterator()), publisher.sortedMvrsFile())
        createZipFile(publisher.sortedMvrsFile(), delete = true)
        logger.info{"write ${countMvrs} cards to ${publisher.sortedMvrsFile()}"}
    }
}

fun createSortedCards(unsortedCards: CloseableIterator<AuditableCard>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    unsortedCards.use { cardIter ->
        while (cardIter.hasNext()) {
            cards.add( cardIter.next().copy(prn = prng.next()))
        }
    }
    return cards.sortedBy { it.prn }
}

fun writeSortedCardsExternalSort(topdir: String, publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIterator(publisher.cardManifestFile())
    writeExternalSortedCards(topdir, publisher.sortedCardsFile(), unsortedCards, seed)
    // logger.info{"write ${unsortedCards.size} cards to ${publisher.sortedCardsFile()}"}

    if (existsOrZip(publisher.testMvrsFile())) {
        val unsortedMvrs = readCardsCsvIterator(publisher.testMvrsFile())
        writeExternalSortedCards(topdir, publisher.sortedMvrsFile(), unsortedMvrs, seed)
        // logger.info{"write ${countMvrs} cards to ${publisher.sortedMvrsFile()}"}
    }
}

fun writeExternalSortedCards(topdir: String, outputFile: String, unsortedCards: CloseableIterator<AuditableCard>, seed: Long) {
    val sorter = SortMerge<AuditableCard>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        cvrs = emptyList(),
        toAuditableCard = { from: AuditableCard, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
    createZipFile(outputFile, delete = true)
}

// The pooled cvrs dont have votes associated with them, used to make the Card Manifest
fun createCvrsFromPools(pools: List<CardPoolIF>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()

    pools.forEach { pool ->
        val cleanName = cleanCsvString(pool.poolName)
        repeat(pool.ncards()) { poolIndex ->
            cvrs.add(
                Cvr(
                    id = "pool${cleanName} card ${poolIndex + 1}",
                    votes = pool.contests().associate{ it to IntArray(0) },
                    phantom = false,
                    poolId = pool.poolId
                )
            )
        }
    }
    val totalRedactedBallots = pools.sumOf { it.ncards() }
    require(cvrs.size == totalRedactedBallots)
    return cvrs
}