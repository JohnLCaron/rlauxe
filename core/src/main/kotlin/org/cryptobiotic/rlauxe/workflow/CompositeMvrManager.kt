package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readPopulationsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("PersistedMvrManager")
private val checkValidity = true

// TODO generalize using just first component
open class CompositeMvrManager(
    val auditRecord: CompositeRecord,
    val config: AuditConfig,
    val contestsUA: List<ContestWithAssertions>,
    val mvrWrite: Boolean = true): MvrManager {

    val publisher = Publisher(auditRecord.componentRecords.first().location)

    override fun sortedCards() = readCardManifestComposite(publisher).cards

    override fun populations(): List<PopulationIF>? {
        return readPopulationsComposite(publisher)
    }

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

        if (Files.exists(Path(publisher.populationsFile()))) {
            val populations = readPopulationsJsonFileUnwrapped(publisher.populationsFile())
            if (populations.isNotEmpty()) {
                // merge population references into the Card
                val mergedCards = CloseableIterable {
                    MergePopulationsIntoCardManifest(
                        readCardsCsvIterator(publisher.sortedCardsFile()),
                        populations,
                    )
                }
                return CardManifest(mergedCards, populations)
            }
        }

        val sortedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) }
        return CardManifest(CloseableIterable { sortedCards.iterator() }, emptyList())
    }

    private fun readPopulationsComposite(publisher: Publisher): List<PopulationIF>? {
        return if (!Files.exists(Path(publisher.populationsFile()))) null else
            readPopulationsJsonFileUnwrapped(publisher.populationsFile())
    }

}