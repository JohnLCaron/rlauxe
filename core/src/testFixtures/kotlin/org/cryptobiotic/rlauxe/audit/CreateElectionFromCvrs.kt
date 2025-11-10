package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCvrs (
    val contestsUA: List<ContestUnderAudit>,
    val cvrs: List<Cvr>, // includes phantoms
    val cardPools: List<CardPoolIF>? = null,
    val cardStyles: List<CardStyleIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardLocations() = createCardIterator()

    fun createCardIterator(): CloseableIterator<AuditableCard> {
        return CvrsWithStylesToCards(
            config.auditType, config.hasStyle,
            Closer(cvrs.iterator()),
            null,
            styles = cardPools ?: cardStyles,
        )
    }
}