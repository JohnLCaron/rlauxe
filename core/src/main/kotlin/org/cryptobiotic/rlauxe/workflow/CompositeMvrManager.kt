package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readBatchesJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import java.nio.file.Files
import kotlin.io.path.Path

// TODO generalize using more than just first component
open class CompositeMvrManager(
    val auditRecord: CompositeRecord,
    val config: Config,
    val contestsUA: List<ContestWithAssertions>,
    val mvrWrite: Boolean = true): MvrManager {

    val publisher = Publisher(auditRecord.componentRecords.first().location)

    // override fun sortedManifest() = readCardManifestComposite(publisher)
    override fun sortedManifest() = auditRecord.readSortedManifest()

    override fun batches(): List<BatchIF>? {
        return readBatchesComposite(publisher)
    }

    override fun pools(): List<CardPool>? {
        return readPoolsComposite(publisher)
    }

    // wtf ??
    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>> {
        val mvrsForRound = readMvrsForRound(round)
        val sampleNumbers = mvrsForRound.map { it.prn }

        val sampledCards = findSamples(sampleNumbers, auditableCards())
        require(sampledCards.size == mvrsForRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrsForRound
            mvrsForRound.forEachIndexed { index, mvr ->
                val card = sampledCards[index]
                require(mvr.location == card.location) { "mvr location ${mvr.location} != card.location ${card.location}" }
                require(mvr.prn == card.prn) { "mvr prn ${mvr.prn} != card.prn ${card.prn}" }
                require(mvr.index == card.index) { "mvr index ${mvr.index} != card.index ${card.index}" }
            }
        }

        if (mvrWrite) {
            val countCards = writeAuditableCardCsvFile(
                Closer(sampledCards.iterator()),
                publisher.sampleCardsFile(round)
            ) // sampleCards
            logger.info { "write ${countCards} cards to ${publisher.sampleCardsFile(round)}" }
        }

        return mvrsForRound.zip(sampledCards)
    }

    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(round))
    }

    private fun auditableCards(): CloseableIterator<AuditableCard> {
        val cardManifest = readCardManifestComposite(publisher)
        return cardManifest.cards.iterator()
    }

    private fun readCardManifestComposite(publisher: Publisher): CardManifest {
        val sortedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) }

        if (Files.exists(Path(publisher.batchesFile()))) {
            val populations = readBatchesJsonFileUnwrapped(publisher.batchesFile())
            if (populations.isNotEmpty()) {
                // merge population references into the Card
                val mergedCards =
                    MergeBatchesIntoCards(
                        sortedCards,
                        populations,
                    )

                // TODO ncards ??
                return CardManifest(mergedCards, 0, populations)
            }
        }

        // TODO ncards ??
        return CardManifest(CloseableIterable { sortedCards.iterator() }, 0, emptyList())
    }

    private fun readBatchesComposite(publisher: Publisher): List<BatchIF>? {
        return if (!Files.exists(Path(publisher.batchesFile()))) null else
            readBatchesJsonFileUnwrapped(publisher.batchesFile())
    }

    private fun readPoolsComposite(publisher: Publisher): List<CardPool>? {
        return if (!Files.exists(Path(publisher.cardPoolsFile()))) null else {
            val infos = contestsUA.associate { it.id to it.contest.info() }
            readCardPoolCsvFile(publisher.cardPoolsFile(), infos)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("CompositeMvrManager")
        private val checkValidity = true
    }
}