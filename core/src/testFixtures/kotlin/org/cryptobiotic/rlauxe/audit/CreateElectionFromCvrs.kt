package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCvrs (
    val contestsUA: List<ContestWithAssertions>,
    val cvrs: List<Cvr>, // includes phantoms
    val cardPools: List<OneAuditPoolFromCvrs>? = null,
    val cardStyles: List<PopulationIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun populations() = cardPools
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = cvrs.size

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCards(
            config.auditType,
            Closer(cvrs.iterator()),
            phantomCvrs = null,
            populations = cardPools ?: cardStyles,
        )
    }
}