package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
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
            VunderPool(vunders, pool.poolName, pool.poolId, pool.hasExactContests)
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
// set Vunder.missing to 0 for hasExactContests=true
// create it for each use or reset something
class VunderPool(val vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int, val hasExactContests: Boolean) {
    var vunderPickers = vunders.mapValues { VunderPicker(it.value) } // Contest id -> VunderPicker

    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        require (poolName == "all" || card.poolId() == poolId) // TODO
        val cardb = AuditableCardBuilder.fromCard(card)

        card.possibleContests().forEach { contestId ->
            val vunderPicker = vunderPickers[contestId]
            if (vunderPicker == null) {
                print("") // ignore
            } else if (vunderPicker.isEmpty()) {
                if (hasExactContests) cardb.replaceContestVotes(contestId, intArrayOf()) // missing not allowed
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
                if (hasExactContests) cvb2.replaceContestVotes(contestId, intArrayOf()) // cant be missing so add an undervote
            } else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                if (cands != null) {
                    cvb2.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                }
            }
        }
    }

    fun done() = vunderPickers.values.all { it.isEmpty() }

    fun reset() {
        vunderPickers = vunders.mapValues { VunderPicker(it.value) } // Contest id -> VunderPicker
    }

    companion object {
        fun fromContests(contests:List<ContestWithAssertions>, poolId: Int): VunderPool {
            val vunders = contests.associate { it.id to Vunder.fromContest(it, poolId) }
            return VunderPool(vunders, "all", poolId, true)
        }
    }
}

// for viewer
fun makeCvrsForVunderPool(pool: CardPool, vunderpool: VunderPool): List<Cvr> {
    vunderpool.reset()
    val rcvrs = mutableListOf<Cvr>()
    var count = 1
    while (!vunderpool.done()) {
        val cvrId = "${pool.name()}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = pool.poolId)
        vunderpool.simulatePooledCvr(cvb2)
        rcvrs.add(cvb2.build())
        count++
    }

    rcvrs.shuffle()
    return rcvrs
}

// for multiple batches, multiple contests and one "pool" of subtotaled votes
class VunderBatches(batches: List<StyleIF>, val onePool: VunderPool) {
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
                    if (batch.hasExactContests()) cardb.replaceContestVotes(
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