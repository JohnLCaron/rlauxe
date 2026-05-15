package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.util.CloseableIterable

// TODO could this return
class CardManifest(val cards: CloseableIterable<AuditableCardIF>, val ncards: Int) {

    companion object {
        fun createFromAList(cards: List<AuditableCardIF>) : CardManifest {
            return CardManifest(CloseableIterable { cards.iterator() }, cards.size)
        }

        fun createFromList(cards: List<CardWithBatchName>, batches: List<StyleIF>) : CardManifest {
            val cardIterable = CloseableIterable { cards.iterator() }
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cardIterable, batches)
            return CardManifest(auditableCards, cards.size)
        }

        fun createFromIterable(cards: CloseableIterable<CardWithBatchName>, batches: List<StyleIF>, ncards: Int) : CardManifest {
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cards, batches)
            return CardManifest(auditableCards, ncards)
        }
    }
}