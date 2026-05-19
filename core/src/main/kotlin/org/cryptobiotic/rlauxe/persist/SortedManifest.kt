package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.CardWithStyleName
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.util.CloseableIterable

// TODO why in persist ??
// TODO why is ncards here ??
class SortedManifest(val cards: CloseableIterable<AuditableCardIF>, val ncards: Int) {

    companion object {
        fun createFromAList(cards: List<AuditableCardIF>) : SortedManifest {
            return SortedManifest(CloseableIterable { cards.iterator() }, cards.size)
        }

        fun createFromList(cards: List<CardWithStyleName>, batches: List<StyleIF>) : SortedManifest {
            val cardIterable = CloseableIterable { cards.iterator() }
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cardIterable, batches)
            return SortedManifest(auditableCards, cards.size)
        }

        fun createFromIterable(cards: CloseableIterable<CardWithStyleName>, batches: List<StyleIF>, ncards: Int) : SortedManifest {
            val auditableCards: CloseableIterable<AuditableCard> = MergeBatchesIntoCardManifestIterable(cards, batches)
            return SortedManifest(auditableCards, ncards)
        }
    }
}