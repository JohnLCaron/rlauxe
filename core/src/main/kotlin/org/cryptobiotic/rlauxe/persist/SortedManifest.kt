package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.util.CloseableIterable

// TODO why in persist ??
// TODO why is ncards here ??
class SortedManifest(val cards: CloseableIterable<AuditableCardIF>, val ncards: Int) {

    companion object {
        fun createFromAList(cards: List<AuditableCardIF>) : SortedManifest {
            return SortedManifest(CloseableIterable { cards.iterator() }, cards.size)
        }
    }
}