package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.Sampling
import kotlin.random.Random

private const val debug = false

// for one contest, this takes a list of cvrs and fuzzes them.
// Only used for estimateClcaAssertionRound, estimateOneAuditAssertionRound, not auditing.
class ClcaCardFuzzSampler(
    val fuzzPct: Double,
    val cards: List<AuditableCard>,
    val contest: ContestIF,
    val cassorter: ClcaAssorter
): Sampling, Iterator<Double> {
    val maxSamples = cards.count { it.hasContest(contest.id) }
    val N = cards.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    var cvrPairs: List<Pair<AuditableCard, AuditableCard>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()  // TODO could do fuzzing on the fly ??
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
        return makeFuzzedCardsFrom(listOf(contest.info()), cards, fuzzPct)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

// for one contest, this takes a list of cvrs and fuzzes them
// Only used for estimatePollingAssertionRound, not auditing.
class PollingCardFuzzSampler(
    val fuzzPct: Double,
    val cards: List<AuditableCard>,
    val contest: Contest,
    val assorter: AssorterIF
): Sampling, Iterator<Double> {
    val maxSamples = cards.count { it.hasContest(contest.id) } // dont need this is its single contest
    val N = cards.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    private var mvrs: List<AuditableCard>
    private var idx = 0

    init {
        mvrs = remakeFuzzed() // TODO could do fuzzing on the fly ??
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contest.id)) {
                val result = assorter.assort(mvr, usePhantoms = true)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<AuditableCard> {
        return makeFuzzedCardsFrom(listOf(contest.info()), cards, fuzzPct) // single contest
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

// for one contest, this takes a list of cvrs and fuzzes them
class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterIF
): Sampling, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) } // dont need this is its single contest
    val N = cvrs.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    private var mvrs: List<Cvr>
    private var idx = 0

    init {
        mvrs = remakeFuzzed() // TODO could do fuzzing on the fly ??
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contest.id)) {
                val result = assorter.assort(mvr, usePhantoms = true)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest.info()), cvrs, fuzzPct) // single contest
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO can it be used with DHondt?
fun makeFuzzedCardsFrom(infoList: List<ContestInfo>,
                        cards: List<AuditableCard>,
                        fuzzPct: Double,
                        undervotes: Boolean = true, // chooseNewCandidateWithUndervotes
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
                    val ncandId = chooseNewCandidate(currId, info.candidateIds, undervotes)
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
fun chooseNewCandidate(currId: Int?, candidateIds: List<Int>, undervotes: Boolean): Int? {
    return if (undervotes) chooseNewCandidateWithUndervotes(currId, candidateIds) else
        chooseNewCandidateNoUndervotes(currId, candidateIds)
}

fun chooseNewCandidateWithUndervotes(currId: Int?, candidateIds: List<Int>): Int? {
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

fun chooseNewCandidateNoUndervotes(currId: Int?, candidateIds: List<Int>): Int? {
    val size = candidateIds.size
    while (true) {
        val ncandIdx = Random.nextInt(size)
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}

////////////////////////////////////////////////////////////////////////////////
// OneAudit Estimation Sampling

class OneAuditVunderBarFuzzer(
    val vunderBar: VunderBar,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
) {
    val isIRV = infos.mapValues { it.value.isIrv }

    fun reset() {
        vunderBar.reset()
    }

    fun makePairsFromCards(cards: List<AuditableCard>): List<Pair<AuditableCard, AuditableCard>> {
        val mvrs = cards.map { card ->
            if (card.poolId != null) {
                vunderBar.simulatePooledCard(card)
            } else if (card.votes != null) {
                makeFuzzedCvrFromCard(infos, isIRV, card, fuzzPct)
            } else {
                throw RuntimeException("card must be pooled or have votes")
            }
        }
        return mvrs.zip(cards)
    }
}

fun makeFuzzedCvrFromCard(
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