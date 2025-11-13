package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCards (
    val contestsUA: List<ContestUnderAudit>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<CardPoolIF>? = null,
    val cardStyles: List<CardStyleIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardIterator()

    fun createCardIterator(): CloseableIterator<AuditableCard> {
        return CardsWithStylesToCards(
            config.auditType, config.hasStyle,
            Closer(cards.iterator()),
            null,
            styles = cardPools ?: cardStyles,
        )
    }
}