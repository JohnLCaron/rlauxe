package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.MvrManagerClcaIF
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable

class MvrManagerClcaSingleRound(val sortedCards: CloseableIterable<AuditableCard>, val maxSamples: Int = -1) :
    MvrManagerClcaIF {

    override fun Nballots(contestUA: ContestUnderAudit) = contestUA.Nc - contestUA.Np // TODO wtf ??

    override fun sortedCards() = sortedCards

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
        val cvrs = mutableListOf<Cvr>()
        var count = 0
        var countPool = 0
        sortedCards().iterator().use { cardIter ->
            while (cardIter.hasNext() && (maxSamples < 0 || count < maxSamples)) {
                val cvr = cardIter.next().cvr()
                cvrs.add(cvr)
                count++
                if (cvr.poolId != null) countPool++
            }
        }
        println("makeCvrPairsForRound: count=$count poolCount=$countPool")
        return cvrs.zip(cvrs)
    }

}