package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CvrsToCardsWithBatchNameIterator
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
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
): ElectionBuilder {

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

    fun createCards(): CloseableIterator<CardWithBatchName> {
        return CvrsToCardsWithBatchNameIterator(
            auditType,
            Closer(cvrs.iterator()),
            phantomCvrs = null,
            batches = cardPools ?: batches,
        )
    }
}

class CreateElectionFromCards (
    val electionName: String,
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<CardPool>? = null,
    val cardStyles: List<BatchIF>? = null,
    val auditType: AuditType,
    val mvrSource: MvrSource,
): ElectionBuilder {

    override fun electionInfo() = ElectionInfo(
        electionName, auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        poolsHaveOneCardStyle = null, mvrSource = mvrSource
    )
    override fun createUnsortedMvrsInternal() = null // for in-memory case
    override fun createUnsortedMvrsExternal() = Closer(createCards().iterator()) // for out-of-memory case
    override fun batches() = cardPools
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = Closer(createCards().iterator())
    override fun ncards() = cards.size

    fun createCards(): List<CardWithBatchName> {
        return cards.map { CardWithBatchName(it) }
    }
}