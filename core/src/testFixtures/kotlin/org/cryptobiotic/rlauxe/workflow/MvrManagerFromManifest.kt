package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.workflow.CardManifest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng

// used in OneAuditSingleRoundWithDilutedMargin and attacks
class MvrManagerFromManifest(
    cardManifest: List<AuditableCard>,
    mvrs: List<Cvr>,
    val infoList: List<ContestInfo>,
    seed:Long,
    val simFuzzPct: Double?,
    val pools: List<OneAuditPoolFromCvrs>? = null,
) : MvrManager {

    private var mvrsRound: List<AuditableCard> = emptyList()
    val sortedCards: List<AuditableCard>
    val sortedMvrs: List<Cvr>

    init {
        require(cardManifest.size == mvrs.size)

        val pairs = cardManifest.zip(mvrs)
        val prng = Prng(seed)
        val cardsWithPrn = mutableListOf<Pair<AuditableCard, Cvr>>()
        pairs.forEach { cardsWithPrn.add(Pair(it.first.copy(prn=prng.next()), it.second)) }
        val sortedPairs = cardsWithPrn.sortedBy { it.first.prn }
        sortedCards = sortedPairs.map { it.first }
        sortedMvrs = sortedPairs.map { it.second }
    }

    override fun cardManifest() :CardManifest {
        return CardManifest.createFromList(sortedCards, pools)
    }

    override fun oapools(): List<OneAuditPoolFromCvrs>? {
        return pools
    }

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  {
        if (mvrsRound.isEmpty()) {  // for SingleRoundAudit.
            val sampledMvrs = if (simFuzzPct == null) {
                sortedMvrs // use the mvrs as they are - ie, no errors
            } else {
                makeFuzzedCvrsForClca(infoList, sortedMvrs, simFuzzPct)
            }
            return sampledMvrs.map{ it }.zip(sortedCards.map{ it })
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, Closer(sortedCards.iterator()))

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.location == cvr.location)
            require(mvr.index == cvr.index)
            require(mvr.prn== cvr.prn)
        }

        return mvrsRound.zip(sampledCvrs)
    }

}
