package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.util.CloseableIterable

// TODO why in persist ??
// TODO why is ncards here ??
class SortedManifest(val cards: CloseableIterable<AuditableCard>, val ncards: Int) {

    companion object {
        fun createFromAList(cards: List<AuditableCard>) : SortedManifest {
            return SortedManifest(CloseableIterable { cards.iterator() }, cards.size)
        }
    }
}