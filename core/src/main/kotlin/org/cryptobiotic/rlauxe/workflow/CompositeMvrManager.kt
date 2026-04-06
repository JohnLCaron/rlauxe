package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.readCardStylesJsonFileUnwrapped
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

    override fun batches(): List<CardStyleIF>? {
        return readBatchesComposite(publisher)
    }

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>> {
        TODO("Not yet implemented")
    }

    override fun writeMvrsForRound(round: Int): Int {
        TODO("Not yet implemented")
    }

    override fun pools(): List<CardPool>? {
        return readPoolsComposite(publisher)
    }

    /*
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
                    MergeBatchesIntoCardIterable(
                        sortedCards,
                        populations,
                    )

                // TODO ncards ??
                return CardManifest(mergedCards, 0, populations)
            }
        }

        // TODO ncards ??
        return CardManifest(CloseableIterable { sortedCards.iterator() }, 0, emptyList())
    } */

    private fun readBatchesComposite(publisher: Publisher): List<CardStyleIF>? {
        return if (!Files.exists(Path(publisher.cardStylesFile()))) null else
            readCardStylesJsonFileUnwrapped(publisher.cardStylesFile())
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