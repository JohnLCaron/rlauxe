package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
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

    // pass in a card from the manifest, and generate an Mvr from it with votes constrained by the contest tabs in Vunder
    // used in Estimation
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

    fun simulatePooledCard(cvb2: AuditableCardBuilder) {
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

    fun makeCardsForOneAuditPool(makeCard:() -> AuditableCardBuilder): List<AuditableCard> {
        this.reset()
        val cards = mutableListOf<AuditableCard>()
        while (!this.done()) {
            val cvb2 = makeCard()
            this.simulatePooledCard(cvb2)
            val card = cvb2.build()
            cards.add(card)
        }
        return cards
    }

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

// class AuditableCardBuilder(
//    val id: String,
//    val location: String?,
//    val index: Int,
//    val prn: Long,
//    val phantom: Boolean,
//    val styleId: Int,
//    val poolId: Int? = null,
//    votesIn: Map<Int, IntArray>?,
//    val style: StyleIF? = null,
//)



// for viewer: ContestPoolsTable.showSimulatedCards.
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

