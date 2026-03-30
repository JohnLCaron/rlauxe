package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import kotlin.collections.toList
import kotlin.random.Random

// VunderPoolsFuzzer takes as input the actual cards of the contest.
// it simulates the pooled cards based on the pool totals
// it optionally fuzzes the Cvrs.
// the mvrCvrPairs are the (mvr, cvr) pairs suitable for CLCA audit
class VunderPoolsFuzzer(
    pools: List<CardPool>,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
    cards: List<AuditableCard>
) {
    val isIRV = infos.mapValues { it.value.isIrv }
    var mvrCvrPairs: List<Pair<AuditableCard, AuditableCard>>  // mvr, cvr pairs
    val vunderPools =  VunderPools(pools)

    init {
        val mvrs = cards.map { card ->
            val onecard = if (card.poolId() != null) {
                vunderPools.simulatePooledCard(card)
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

/////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO this is pretty crude, just randomly changing shit.
// TODO cvrsContainUndervotes
// vunderPools.simulatePooledCard(card) carefully counts votes, undervotes, and missing votes
// here we are fuzzing votes: Map<contestId, cands>. undervotes have contestId -> intArray(). missing are not in votes.
//    so we are not changing the missing votes, ie not testing what happens if missing differs between CVR and MVR.
//    we could do that if we could assume batch.possibleContests(), ie batch != null
//    we only need to do that if cvrsContainUndervotes == false
fun makeFuzzedCardFromCard(
    infos: Map<Int, ContestInfo>,
    isIRV: Map<Int, Boolean>, // contestId -> isIRV
    card: AuditableCard, // must have votes, ie have a Cvr
    fuzzPct: Double,
) : AuditableCard {
    if (fuzzPct == 0.0 || card.phantom) return card
    val r = Random.nextDouble(1.0)
    if (r > fuzzPct) return card

    require(card.votes != null)
    val cardb = AuditableCardBuilder.fromCard(card)
    // TODO ?? cardb.possibleContests().forEach { contestId ->
    cardb.votes.forEach { (contestId, cands) ->
        val info = infos[contestId]
        if (info != null) {
            if (isIRV[contestId] ?: false) {
                val currentVotes = cardb.votes[contestId]?.toList()?.toMutableList() ?: mutableListOf()
                switchCandidateRankings(currentVotes, info.candidateIds)
                cardb.replaceContestVotes(contestId, currentVotes.toIntArray())
            } else {
                // votes.size == 0 means an undervote
                // votes = null means it doesnt have this contest. perhaps one shouldnt add it ??
                val currCand: Int? = if (cands == null || cands.size == 0)
                    null
                else
                    cands[0] // TODO only one vote allowed

                // choose a different candidate, or none.
                val newCand = chooseNewCandidate(currCand, info.candidateIds)
                cardb.replaceContestVote(contestId, newCand)
            }
        }
    }

    return cardb.build()
}

fun switchCandidateRankings(votes: MutableList<Int>, candidateIds: List<Int>) {
    val ncands = candidateIds.size
    val size = votes.size
    if (size == 0) { // no votes -> random one vote
        val candIdx = Random.nextInt(ncands)
        votes.add(candidateIds[candIdx])
    } else if (size == 1) { // one vote -> no votes
        votes.clear()
    } else { // switch two randomly selected votes
        val ncandIdx1 = Random.nextInt(size)
        val ncandIdx2 = Random.nextInt(size)
        val save = votes[ncandIdx1]
        votes[ncandIdx1] = votes[ncandIdx2]
        votes[ncandIdx2] = save
    }
}

// randomly change a candidate to another; doesnt deal with voteForN > 1
fun chooseNewCandidate(currId: Int?, candidateIds: List<Int>): Int? {
    val size = candidateIds.size
    while (true) {
        val ncandIdx = Random.nextInt(size + 1)
        if (ncandIdx == size)
            return null // choose none, so turn it into an undervote
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}
