package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCvrs (
    val contestsUA: List<ContestWithAssertions>,
    val cvrs: List<Cvr>, // includes phantoms
    val auditType: AuditType,
    val cardPools: List<OneAuditPool>? = null,
    val cardStyles: List<PopulationIF>? = null,
): CreateElectionIF {

    override fun electionInfo() = ElectionInfo(
        auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null,
    )
    override fun createUnsortedMvrsInternal() = cvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun populations() = cardPools
    override fun makeCardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = cvrs.size

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCards(
            auditType,
            Closer(cvrs.iterator()),
            phantomCvrs = null,
            populations = cardPools ?: cardStyles,
        )
    }
}