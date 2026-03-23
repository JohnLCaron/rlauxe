package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCvrs (
    val electionName: String,
    val contestsUA: List<ContestWithAssertions>,
    val cvrs: List<Cvr>, // includes phantoms
    val auditType: AuditType,
    val cardPools: List<CardPool>? = null,
    val batches: List<BatchIF>? = null,
    val mvrSource: MvrSource,
): CreateElectionIF {

    override fun electionInfo() = ElectionInfo(
        electionName, auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        poolsHaveOneCardStyle = null, mvrSource = mvrSource
    )
    override fun createUnsortedMvrsInternal() = cvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun batches() = batches
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = cvrs.size

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsAndBatchesToCards(
            auditType,
            Closer(cvrs.iterator()),
            phantomCvrs = null,
            batches = cardPools ?: batches,
        )
    }
}