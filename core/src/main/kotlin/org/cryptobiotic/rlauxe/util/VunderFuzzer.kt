package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.chooseNewCandidate
import org.cryptobiotic.rlauxe.estimate.switchCandidateRankings
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import kotlin.random.Random

////////////////////////////////////////////////////////////////////////////////
// OneAudit Estimation Sampling

class OneAuditVunderFuzzer(
    val pools: List<OneAuditPoolIF>,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
    cards: List<AuditableCard>
) {
    val isIRV = infos.mapValues { it.value.isIrv }
    var fuzzedPairs: List<Pair<AuditableCard, AuditableCard>>

    init {
        val vunderPools =  VunderPools(pools, infos)
        val mvrs = cards.map { card ->
            if (card.poolId != null) {
                vunderPools.simulatePooledCard(card, card.poolId)
            } else if (card.votes != null) {
                makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct)
            } else {
                throw RuntimeException("card must be pooled or have votes")
            }
        }
        fuzzedPairs = mvrs.zip(cards)
    }
}

// claims you can use IRV ?
private fun makeFuzzedCardFromCard(
    infos: Map<Int, ContestInfo>,
    isIRV: Map<Int, Boolean>,
    card: AuditableCard, // must have votes, ie have a Cvr
    fuzzPct: Double,
    undervotes: Boolean = true, // chooseNewCandidateWithUndervotes
) : AuditableCard {
    if (fuzzPct == 0.0 || card.phantom) return card
    val r = Random.nextDouble(1.0)
    if (r > fuzzPct) return card

    val cardb = CardBuilder.fromCard(card)
    cardb.possibleContests.forEach { contestId ->
        val info = infos[contestId]
        if (info != null) {
            if (isIRV[contestId] ?: false) {
                val currentVotes = cardb.votes[contestId]?.toList()?.toMutableList() ?: mutableListOf<Int>()
                switchCandidateRankings(currentVotes, info.candidateIds)
                cardb.replaceContestVotes(contestId, currentVotes.toIntArray())
            } else {
                val votes = cardb.votes[contestId]
                val currId: Int? = if (votes == null || votes.size == 0) null else votes[0] // only one vote allowed
                // choose a different candidate, or none.
                val ncandId = chooseNewCandidate(currId, info.candidateIds, undervotes)
                cardb.replaceContestVote(contestId, ncandId)
            }
        }
    }

    return cardb.build()
}

// simulate pooled data from the pool values; not for IRV
class VunderPools(pools: List<OneAuditPoolIF>, infos: Map<Int, ContestInfo>) {
    val vunderPools: Map<Int, VunderPool>

    init {
        vunderPools = pools.map { pool ->
            val vunders = pool.contests().associate { contestId ->
                Pair( contestId, pool.votesAndUndervotes(contestId, infos[contestId]?.voteForN ?: 1))
            }
            VunderPool(vunders, pool.poolName, pool.poolId)
        }.associateBy { it.poolId }
    }

    // for the given pooled card with no votes, simulate one with votes, staying within the pool vote totals.
    fun simulatePooledCard(card: AuditableCard, poolId: Int): AuditableCard {
        val vunderPool = vunderPools[poolId]!!
        return vunderPool.simulatePooledCard(card)
    }
}

// for one pool
// vunders: Contest id -> Vunder
class VunderPool(val vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int ) {
    val vunderPickers = vunders.mapValues { VunderPicker(it.value)}

    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        val cardb = CardBuilder.fromCard(card)
        card.contests().forEach { contestId ->
            val vunderPicker = vunderPickers[contestId]
            if (vunderPicker == null || vunderPicker.isEmpty())
                cardb.replaceContestVotes(contestId, intArrayOf())
            else {
                val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                cardb.replaceContestVotes(contestId, cands)
            }
        }
        return cardb.build()
    }
}
