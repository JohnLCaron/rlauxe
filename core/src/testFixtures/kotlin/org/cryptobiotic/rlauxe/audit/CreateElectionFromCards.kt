package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCards (
    val contestsUA: List<ContestUnderAudit>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<OneAuditPoolIF>? = null,
    val cardStyles: List<PopulationIF>? = null,
    val config: AuditConfig,
): CreateElectionPIF {

    override fun populations() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return CardsWithPopulationsToCardManifest(
            config.auditType,
            Closer(cards.iterator()),
            populations = cardPools ?: cardStyles,
        )
    }
}