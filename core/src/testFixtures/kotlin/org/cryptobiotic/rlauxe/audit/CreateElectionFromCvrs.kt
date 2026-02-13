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

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CardManifest {
        val mergedCards = CvrsWithPopulationsToCards(
            config.auditType,
            Closer(cvrs.iterator()),
            null,
            populations = cardPools ?: cardStyles,
        )
        return CardManifest.createFromIterator(mergedCards.iterator(), cvrs.size, cardPools ?: cardStyles)
    }
}