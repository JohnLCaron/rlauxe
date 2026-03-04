package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardFromCard
import org.cryptobiotic.rlauxe.util.CardBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilder2

////////////////////////////////////////////////////////////////////////////////
// OneAudit Estimation Sampling

// OneAuditVunderFuzzer takes as input the actual cards of the contest.
// it simulates the pooled cards based on the pool totals
// it optionally fuzzes the Cvrs.
// the mvrCvrPairs are the (mvr, cvr) pairs suitable for CLCA audit
class OneAuditVunderFuzzer(
    pools: List<OneAuditPool>,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
    cards: List<AuditableCard>
) {
    val isIRV = infos.mapValues { it.value.isIrv }
    var mvrCvrPairs: List<Pair<AuditableCard, AuditableCard>>  // mvr, cvr pairs
    val vunderPools =  VunderPools(pools)

    init {
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
class VunderPools(pools: List<OneAuditPool>) {
    val vunderPools: Map<Int, VunderPool>  // poolId -> VunderPool

    init {
        vunderPools = pools.map { pool ->
            val vunders = pool.possibleContests().associate { contestId ->
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

    // used for implementing pools with hasSingleCardStyle
    fun simulatePooledCvr(cvb2: CvrBuilder2) {
        vunderPickers.forEach { (contestId, vunderPicker) ->
            if (vunderPicker.isEmpty())
                cvb2.replaceContestVotes(contestId, intArrayOf())
            else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                if (cands != null) {
                    cvb2.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                }
            }
        }
    }

    fun done() = vunderPickers.values.all { it.isEmpty() }
}
