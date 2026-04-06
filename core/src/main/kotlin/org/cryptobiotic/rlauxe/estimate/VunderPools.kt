package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import kotlin.collections.get

// for all pools, multiple contests
class VunderPools(pools: List<CardPool>) {
    val vunderPools: Map<Int, VunderPool>  // poolId -> VunderPool

    init {
        vunderPools = pools.map { pool ->
            val vunders = pool.possibleContests().associate { contestId ->
                Pair( contestId, pool.votesAndUndervotes(contestId))
            }
            VunderPool(vunders, pool.poolName, pool.poolId, pool.hasSingleCardStyle)
        }.associateBy { it.poolId }
    }

    // for the given pooled card with no votes, simulate one with votes, staying within the pool vote totals.
    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        val vunderPool = vunderPools[card.poolId()]
        return vunderPool!!.simulatePooledCard(card)
    }
}

// for one pool, multiple contests
// vunders: Contest id -> Vunder
// set Vunder.missing to 0 for hasSingleCardStyle=true
class VunderPool(vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int, val hasSingleCardStyle: Boolean) {
    val vunderPickers = vunders.mapValues { VunderPicker(it.value) } // Contest id -> VunderPicker

    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        require (poolName == "all" || card.poolId() == poolId) // TODO
        val cardb = AuditableCardBuilder.fromCard(card)

        card.possibleContests().forEach { contestId ->
            val vunderPicker = vunderPickers[contestId]
            if (vunderPicker == null) {
                print("") // ignore
            } else if (vunderPicker.isEmpty()) {
                if (hasSingleCardStyle) cardb.replaceContestVotes(contestId, intArrayOf()) // missing not allowed
            } else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                if (cands != null) {
                    cardb.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                }
            }
        }
        return cardb.build()
    }

    fun simulatePooledCvr(cvb2: CvrBuilder2) {
        vunderPickers.forEach { (contestId, vunderPicker) ->
            if (vunderPicker.isEmpty()) {
                if (hasSingleCardStyle) cvb2.replaceContestVotes(contestId, intArrayOf()) // cant be missing so add an undervote
            } else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                if (cands != null) {
                    cvb2.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                }
            }
        }
    }

    fun done() = vunderPickers.values.all { it.isEmpty() }

    companion object {
        fun fromContests(contests:List<ContestWithAssertions>, poolId: Int): VunderPool {
            val vunders = contests.associate { it.id to Vunder.fromContest(it, poolId) }
            return VunderPool(vunders, "all", poolId, true)
        }
    }
}

// for multiple batches, multiple contests and one "pool" of subtotaled votes
class VunderBatches(batches: List<CardStyleIF>, val onePool: VunderPool) {
    val batchMap = batches.associateBy { it.name() }

    // for the given pooled card with no votes, simulate one with votes, staying within the onePool vote totals.
    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        if (card.isPhantom()) return card

        val batch = batchMap[card.styleName()]
        val cardb = AuditableCardBuilder.fromCard(card)

        if (batch == null) {
            println("batch ${card.styleName()} not found")
            return cardb.build()
        }

        batch.possibleContests().forEach { contestId ->
            val vunderPicker = onePool.vunderPickers[contestId]
            // only contests still needed to audit are in OnePool
            if (vunderPicker != null) {
                if (vunderPicker.isEmpty()) {
                    if (batch.hasSingleCardStyle()) cardb.replaceContestVotes(
                        contestId,
                        intArrayOf()
                    ) // missing not allowed
                } else {
                    val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                    if (cands != null) {
                        cardb.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                    }
                }
            }
        }
        return cardb.build()
    }
}