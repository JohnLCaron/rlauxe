package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CvrsToCardStylesIterator
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.mvrsToAuditableCardsList
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Closer

class CreateElectionFromCvrs (
    val electionName: String,
    val contestsUA: List<ContestWithAssertions>,
    val cvrs: List<Cvr>, // includes phantoms
    val auditType: AuditType,
    val cardPools: List<CardPool>? = null,
    val batches: List<StyleIF>? = null,
    val mvrSource: MvrSource,
): ElectionBuilder {

    override fun electionInfo() = ElectionInfo(
        electionName, auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        mvrSource = mvrSource, pollingMode = PollingMode.withBatches
    )
    override fun unsortedMvrsInternal() = mvrsToAuditableCardsList(cvrs, batches)
    override fun unsortedMvrsExternal() = null
    override fun cardStyles() = batches
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = cvrs.size

    fun createCards() = CvrsToCardStylesIterator(
            auditType,
            Closer(cvrs.iterator()),
            phantomCvrs = null,
            styles = cardPools ?: batches,
        )
}

class CreateElectionFromCards (
    val electionName: String,
    val auditType: AuditType,
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>, // includes phantoms
    val cardPools: List<CardPool>? = null,
    val batches: List<StyleIF>? = null,
    val mvrSource: MvrSource? = null
): ElectionBuilder {

    override fun electionInfo(): ElectionInfo {
        return if (mvrSource == null) ElectionInfo(
            electionName, auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
            pollingMode = PollingMode.withBatches
        ) else ElectionInfo(
                electionName, auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
                mvrSource = mvrSource,
                pollingMode = PollingMode.withBatches
            )
    }
    override fun unsortedMvrsInternal() = cards
    override fun unsortedMvrsExternal() = null // Closer(createCards().iterator()) // for out-of-memory case
    override fun cardStyles() = batches
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cards() = Closer(cards.iterator() )
    override fun ncards() = cards.size
}