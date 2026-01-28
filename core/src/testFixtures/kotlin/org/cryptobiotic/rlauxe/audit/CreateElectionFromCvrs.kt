package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
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
    override fun cardManifest() = createCardIterator()

    fun createCardIterator(): CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCardManifest(
            config.auditType,
            Closer(cvrs.iterator()),
            null,
            populations = cardPools ?: cardStyles,
        )
    }
}