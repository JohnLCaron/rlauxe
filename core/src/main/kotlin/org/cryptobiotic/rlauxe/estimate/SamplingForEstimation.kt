package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.Sampling
import kotlin.random.Random

private const val debug = false

// for one contest, this takes a list of cvrs and fuzzes them.
// Only used for estimating the sample size, not auditing.
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
                val hasStyle = card.poolId == null // TODO may not be right
                val result = cassorter.bassort(mvr.cvr(), card.cvr(), hasStyle)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cards)
        permutedIndex.shuffle(Random)
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
// Only used for estimating the sample size, not auditing.
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
                val result = assorter.assort(mvr.cvr(), usePhantoms = true)
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

//////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO can it be used with DHondt?
fun makeFuzzedCardsFrom(infoList: List<ContestInfo>,
                        cards: List<AuditableCard>,
                        fuzzPct: Double,
                        undervotes: Boolean = true,
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
                    val currId: Int? = if (votes == null || votes.size == 0) null else votes[0] // TODO only one vote allowed, cant use on Raire
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