package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.Sampler
import kotlin.random.Random

private const val debug = false

// for one contest, this takes a list of cards and fuzzes them to use as the mvrs.
// Only used for estimateClcaAssertionRound, estimateOneAuditAssertionRound, not auditing.
class ClcaCardFuzzSampler(
    val fuzzPct: Double,
    val cards: List<AuditableCard>,
    val contest: ContestIF,
    val cassorter: ClcaAssorter
): Sampler, Iterator<Double> {
    val maxSamples = cards.count { it.hasContest(contest.id) }
    val N = cards.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    var cvrPairs: List<Pair<AuditableCard, AuditableCard>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cards)
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contest.id)) {
                val result = cassorter.bassort(mvr.cvr(), card.cvr(), card.exactContests())
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed() // refuzz each time
        cvrPairs = mvrs.zip(cards)
        permutedIndex.shuffle(Random) // also, a new permutation....
        idx = 0
    }

    fun remakeFuzzed(): List<AuditableCard> {
        return makeFuzzedCardsForClca(listOf(contest.info()), cards, fuzzPct)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ok for CLCA IRV; TODO can this be used with DHondt?
fun makeFuzzedCardsForClca(infoList: List<ContestInfo>,
                           cards: List<AuditableCard>,
                           fuzzPct: Double,
) : List<AuditableCard> {
    if (fuzzPct == 0.0) return cards
    val infos = infoList.associate{ it.id to it }
    val isIRV = infoList.associate { it.id to it.isIrv}

    var count = 0
    val cardBuilders = cards.map { CardBuilder.fromCard(it) }

    cardBuilders.filter { !it.phantom }.forEach { cardb: CardBuilder ->
        val r = Random.nextDouble(1.0)
        cardb.possibleContests.forEach { contestId ->
            val info = infos[contestId]
            if (info != null && r < fuzzPct) {
                if (isIRV[contestId]?:false) {
                    val currentVotes = cardb.votes[contestId]?.toList()?.toMutableList() ?: mutableListOf<Int>()
                    switchCandidateRankings(currentVotes, info.candidateIds)
                    cardb.replaceContestVotes(contestId, currentVotes.toIntArray())
                } else {
                    val votes = cardb.votes[contestId]
                    val currId: Int? = if (votes == null || votes.size == 0)
                        null
                    else
                        votes[0] // TODO only one vote allowed, cant use on Raire
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, info.candidateIds)
                    cardb.replaceContestVote(contestId, ncandId)
                }
            }
        }
        if (r < fuzzPct) count++
    }
    if (debug) println("changed $count out of ${cards.size}")

    return cardBuilders.map { it.build() }
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

// randomly change a candidate to another
fun chooseNewCandidate(currId: Int?, candidateIds: List<Int>): Int? {
    val size = candidateIds.size
    while (true) {
        val ncandIdx = Random.nextInt(size + 1)
        if (ncandIdx == size)
            return null // choose none
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}