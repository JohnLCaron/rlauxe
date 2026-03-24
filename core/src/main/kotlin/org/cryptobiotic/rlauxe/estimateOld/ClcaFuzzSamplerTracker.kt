package org.cryptobiotic.rlauxe.estimateOld

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.ErrorTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsForClca
import org.cryptobiotic.rlauxe.estimateOld.CardSamples
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

private const val debug = false

private val logger = KotlinLogging.logger("ClcaFuzzSamplerTracker")

// used by estimateClcaAssertionRound
class ClcaFuzzSamplerTracker(
    val simFuzzPct: Double,
    cardSamples: CardSamples, // these are new each round and need to be fuzzed
    val contestUA: ContestWithAssertions,
    val cassorter: ClcaAssorter,
    val clcaErrorTracker: ClcaErrorTracker,
): SamplerTracker, ErrorTracker {

    val contest = contestUA.contest
    val samples = cardSamples.extractSubsetByIndex(contest.id)
    val maxSamples = samples.size
    val permutedIndex = MutableList(samples.size) { it }

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
                val nextVal = cassorter.bassort(mvr, card, hasStyle=card.hasStyle())
                clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
                welford.update(nextVal)
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

        permutedIndex.shuffle(Random) // also, a new permutation....
        idx = 0
        welford = Welford()
        clcaErrorTracker.reset()
    }

    fun remakeFuzzed(): List<AuditableCard> {
        return makeFuzzedCardsForClca(listOf(contest.info()), samples, simFuzzPct)
    }

    override fun nmvrs() = maxSamples
    override fun countCvrsUsedInAudit() = idx

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    override fun numberOfSamples() = welford.count
    override fun welford() = welford

    //// ErrorTracker
    override fun measuredClcaErrorCounts(): ClcaErrorCounts = clcaErrorTracker.measuredClcaErrorCounts()
    override fun noerror(): Double = clcaErrorTracker.noerror
}