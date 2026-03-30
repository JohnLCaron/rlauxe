package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.util.CloseableIterable

class CardManifest(val cards: CloseableIterable<AuditableCard>, val ncards: Int) {
    // val popMap = batches.associateBy{ it.name() }
    // fun batch(batchName: String) = popMap[batchName]

    companion object {
        fun createFromAList(cards: List<AuditableCard>) : CardManifest {
            return CardManifest(CloseableIterable { cards.iterator() }, cards.size)
        }

        fun createFromList(cards: List<CardWithBatchName>, batches: List<BatchIF>) : CardManifest {
            val cardIterable = CloseableIterable { cards.iterator() }
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cardIterable, batches)
            return CardManifest(auditableCards, cards.size)
        }

        fun createFromIterable(cards: CloseableIterable<CardWithBatchName>, batches: List<BatchIF>, ncards: Int) : CardManifest {
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cards, batches)
            return CardManifest(auditableCards, ncards)
        }
    }
}