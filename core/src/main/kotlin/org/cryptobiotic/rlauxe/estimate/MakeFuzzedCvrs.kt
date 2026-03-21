package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

private const val debug = false

private val logger = KotlinLogging.logger("ClcaFuzzSamplerTracker")

/////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO this is pretty crude, just randomly changing shit.

fun makeFuzzedCvrsForClca(infoList: List<ContestInfo>, cvrs: List<Cvr>, fuzzPct: Double?) : List<Cvr> {
    if (fuzzPct == null || fuzzPct == 0.0) return cvrs
    val infos = infoList.associate{ it.id to it }
    val isIRV = infoList.associate { it.id to it.isIrv }
    var countChanged = 0
    val result =  cvrs.mapIndexed { idx, cvr ->
        val card = AuditableCard.fromCvr( cvr, idx, Random.nextLong() )
        val fuzzedCard = makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct )
        val result = fuzzedCard.cvr()
        if (result != cvr)
            countChanged++
        result
    }
    // println("changed $countChanged cards pct = ${countChanged/cvrs.size.toDouble()}")
    return result
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ok for CLCA IRV; TODO can this be used with DHondt?
// ClcaFuzzSamplerTracker uses this for only one contest; so the other fuzzings are ignored
// PersistedMvrManagerTest uses this to fuzz all contests
fun makeFuzzedCardsForClca(infoList: List<ContestInfo>,
                           cards: List<AuditableCard>,
                           fuzzPct: Double,
) : List<AuditableCard> {
    if (fuzzPct == 0.0) return cards
    val infos = infoList.associate{ it.id to it }
    val isIRV = infoList.associate { it.id to it.isIrv}

    return cards.map { card -> makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct ) }
}

// used by VunderFuzzer
fun makeFuzzedCardFromCard(
    infos: Map<Int, ContestInfo>,
    isIRV: Map<Int, Boolean>,
    card: AuditableCard, // must have votes, ie have a Cvr
    fuzzPct: Double,
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
                // votes.size == 0 means an undervote
                // votes = null means it doesnt have this contest. perhaps one shouldnt add it ??
                val currCand: Int? = if (votes == null || votes.size == 0)
                    null
                else
                    votes[0] // TODO only one vote allowed

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
