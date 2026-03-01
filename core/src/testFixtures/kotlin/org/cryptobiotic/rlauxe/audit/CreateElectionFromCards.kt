package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.util.CloseableIterator

class CreateElectionFromCards (
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<OneAuditPool>? = null,
    val cardStyles: List<PopulationIF>? = null,
    val auditType: AuditType,
): CreateElectionIF {

    override fun electionInfo() = ElectionInfo(
        auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null,
    )
    override fun createUnsortedMvrs() = emptyList<Cvr>() // TODO only needed for ONEAUDIT with private mvrs
    override fun populations() = cardPools
    override fun makeCardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = cards.size

    fun createCards(): CloseableIterator<AuditableCard> {
        return MergePopulationsIntoCards(
            cards,
            populations = cardPools ?: cardStyles,
        )
    }
}