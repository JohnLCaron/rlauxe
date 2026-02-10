package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker2
import org.cryptobiotic.rlauxe.betting.ErrorTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

private const val debug = false

private val logger = KotlinLogging.logger("ClcaFuzzSamplerTracker")

// used by estimateClcaAssertionRound
class ClcaFuzzSamplerTracker(
    val fuzzPct: Double,
    cardSamples: CardSamples,
    val contestUA: ContestWithAssertions,
    val cassorter: ClcaAssorter,
): SamplerTracker, ErrorTracker {
    val contest = contestUA.contest
    val samples = cardSamples.extractSubsetByIndex(contest.id)
    val maxSamples = samples.size
    val permutedIndex = MutableList(samples.size) { it }
    val clcaErrorTracker = ClcaErrorTracker2(cassorter.noerror(), cassorter.assorter.upperBound())

    var welford = Welford()
    var cvrPairs: List<Pair<AuditableCard, AuditableCard>> // (mvr, cvr)
    var idx = 0
    var simulation = 0

    init {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(samples)
    }

    override fun sample(): Double {
        while (idx < maxSamples) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contest.id)) { // should always be true
                val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
                clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
                if (lastVal != null) welford.update(lastVal!!)
                lastVal = nextVal
                return nextVal
            } else {
                logger.error{"cardSamples for contest ${contest.id} list card does not contain the contest at index ${permutedIndex[idx-1]}"}
            }
        }
        logger.error{"no samples left for ${contest.id} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed() // refuzz each time
        cvrPairs = mvrs.zip(samples)
        // testFuzzed()

        permutedIndex.shuffle(Random) // also, a new permutation....
        idx = 0
        welford = Welford()
        clcaErrorTracker.reset()
    }

    fun remakeFuzzed(): List<AuditableCard> {
        return makeFuzzedCardsForClca(listOf(contest.info()), samples, fuzzPct)
    }

    fun testFuzzed() {
        val testErrors = ClcaErrorTracker2(cassorter.noerror(), cassorter.assorter.upperBound())
        cvrPairs.forEach { (mvr, card) ->
            if (card.hasContest(contest.id)) { // should always be true
                val nextVal = cassorter.bassort(mvr, card, hasStyle = card.exactContests())
                testErrors.addSample(nextVal, card.poolId == null)
            }
        }
        val counts = testErrors.measuredClcaErrorCounts()

        val (est, bet) = cassorter.estWithOptimalBet(contestUA, .9, 0.5, counts)
        val sumCounts = counts.errorCounts().map { it.value }.sum()
        val pct = sumCounts/cvrPairs.size.toDouble()
        if (pct > 10 * fuzzPct) {
            val logt = counts.expectedValueLogt(bet)
            println("simulation $simulation has $fuzzPct % errors bet=$bet est=$est counts = ${counts.errorCounts()} logt=$logt")
        }
        simulation++
    }

    override fun maxSamples() = maxSamples
    override fun countCvrsUsedInAudit() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun welford() = welford

    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }

    //// ErrorTracker
    override fun measuredClcaErrorCounts(): ClcaErrorCounts = clcaErrorTracker.measuredClcaErrorCounts()
    override fun noerror(): Double = clcaErrorTracker.noerror
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO this is pretty crude, just randomly changing shit.

fun makeFuzzedCvrsForClca(infoList: List<ContestInfo>,
                          cvrs: List<Cvr>,
                          fuzzPct: Double?,
) : List<Cvr> {
    if (fuzzPct == null || fuzzPct == 0.0) return cvrs
    val infos = infoList.associate{ it.id to it }
    val isIRV = infoList.associate { it.id to it.isIrv}
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
