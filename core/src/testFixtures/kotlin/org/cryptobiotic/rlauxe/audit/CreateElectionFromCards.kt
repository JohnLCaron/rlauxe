package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCards (
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<OneAuditPoolFromCvrs>? = null,
    val cardStyles: List<PopulationIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun populations() = cardPools
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return MergePopulationsIntoCardManifest(
            Closer(cards.iterator()),
            populations = cardPools ?: cardStyles,
        )
    }
}