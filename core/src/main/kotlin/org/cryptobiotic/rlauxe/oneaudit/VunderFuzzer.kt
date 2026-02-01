package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardFromCard
import org.cryptobiotic.rlauxe.util.CardBuilder
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.VunderPicker

////////////////////////////////////////////////////////////////////////////////
// OneAudit Estimation Sampling

// OneAuditVunderFuzzer creates fuzzed mvrs (non-pooled) and simulated mvrs (pooled), including IRV
class OneAuditVunderFuzzer(
    pools: List<OneAuditPoolFromCvrs>,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
    cards: List<AuditableCard>
) {
    val isIRV = infos.mapValues { it.value.isIrv }
    var mvrCvrPairs: List<Pair<AuditableCard, AuditableCard>>  // mvr, cvr pairs

    init {
        val vunderPools =  VunderPools(pools)
        val mvrs = cards.map { card ->
            val onecard = if (card.poolId != null) {
                vunderPools.simulatePooledCard(card, card.poolId)
            } else if (card.votes != null) {
                makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct)  // in ClcaFuzzSamplerTracker
            } else {
                throw RuntimeException("card must be pooled or have votes")
            }
            onecard
        }
        mvrCvrPairs = mvrs.zip(cards)
    }
}

// for all pools
class VunderPools(pools: List<OneAuditPoolFromCvrs>) {
    val vunderPools: Map<Int, VunderPool>  // poolId -> VunderPool

    init {
        vunderPools = pools.map { pool ->
            val vunders = pool.contests().associate { contestId ->
                Pair( contestId, pool.votesAndUndervotes(contestId))
            }
            VunderPool(vunders, pool.poolName, pool.poolId)
        }.associateBy { it.poolId }
    }

    // for the given pooled card with no votes, simulate one with votes, staying within the pool vote totals.
    fun simulatePooledCard(card: AuditableCard, poolId: Int): AuditableCard {
        val vunderPool = vunderPools[poolId]
        return vunderPool!!.simulatePooledCard(card)
    }
}

// for one pool
// vunders: Contest id -> Vunder
class VunderPool(vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int ) {
    val vunderPickers = vunders.mapValues { VunderPicker(it.value) } // Contest id -> VunderPicker

    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        require (card.poolId == poolId)
        val cardb = CardBuilder.fromCard(card)
        card.contests().forEach { contestId ->
            val vunderPicker = vunderPickers[contestId]
            if (vunderPicker == null || vunderPicker.isEmpty())
                cardb.replaceContestVotes(contestId, intArrayOf())
            else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                if (cands != null) {
                    cardb.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                }
            }
        }
        return cardb.build()
    }
}
