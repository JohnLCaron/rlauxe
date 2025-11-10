package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import kotlin.collections.forEach
import kotlin.io.path.Path

interface CreateElectionIF {
    fun contestsUA(): List<ContestUnderAudit>
    fun cardPools(): List<CardPoolIF>? // only if OneAudit

    // if you immediately write to disk, you only need one pass through the iterator
    fun cardLocations() : CloseableIterator<AuditableCard>
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
            addOAClcaAssortersFromMargin(contestsUA, cardPools!!, config.hasStyle) // hmmm shouldnt these already be added ??
        }
        logger.info { "added ClcaAssertions from reported margin " }

        val cards = election.cardLocations()
        val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
        createZipFile(publisher.cardManifestFile(), delete = true)
        logger.info { "write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

        // this may change the auditStatus to misformed
        val results = VerifyResults()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // sf only writes these:
        // contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

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
fun createCardsFromPools(pools: List<CardPoolIF>, startIdx: Int) : List<AuditableCard> {
    var idx = startIdx
    val cards = mutableListOf<AuditableCard>()

    pools.forEach { pool ->
        val cleanName = cleanCsvString(pool.poolName)
        repeat(pool.ncards()) { poolIndex ->
            cards.add(
                //     val location: String, // info to find the card for a manual audit. Aka ballot identifier.
                //    val index: Int,  // index into the original, canonical list of cards
                //    val prn: Long,   // psuedo random number
                //    val phantom: Boolean,
                //    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle
                //    val votes: Map<Int, IntArray>?, // for CLCA, a map of contest -> the candidate ids voted; must include undervotes (??)
                //                                    // for IRV, ranked first to last; missing for pooled data or polling audits
                //    val poolId: Int?, // for OneAudit
                //    val cardStyle: String? = null,
                AuditableCard(
                    location = "pool${cleanName} card ${poolIndex + 1}",
                    index=idx++,
                    prn=0L,
                    phantom = false,
                    possibleContests=pool.contests(),
                    votes = null,
                    poolId = pool.poolId
                )
            )
        }
    }
    val totalCards = pools.sumOf { it.ncards() }
    require(cards.size == totalCards)
    return cards
}