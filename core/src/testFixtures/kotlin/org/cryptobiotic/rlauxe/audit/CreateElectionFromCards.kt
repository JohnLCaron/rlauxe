package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCards (
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<OneAuditPoolFromCvrs>? = null,
    val cardStyles: List<PopulationIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CardManifest {
        val mergedCards = MergePopulationsIntoCards(
            cards,
            populations = cardPools ?: cardStyles,
        )
        return CardManifest.createFromIterator(mergedCards.iterator(), cards.size, cardPools)
    }
}