package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker2
import org.cryptobiotic.rlauxe.betting.SampleErrorTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

private const val debug = false

private val logger = KotlinLogging.logger("ClcaFuzzSamplerTracker")

class ClcaFuzzSamplerTracker2(
    val fuzzPct: Double,
    cardSamples: CardSamples,
    val contest: ContestIF,
    val cassorter: ClcaAssorter
): SamplerTracker, SampleErrorTracker {
    val samples = cardSamples.extractSubsetByIndex(contest.id)
    val maxSamples = samples.size
    val permutedIndex = MutableList(samples.size) { it }
    val clcaErrorTracker = ClcaErrorTracker2(cassorter.noerror(), cassorter.assorter.upperBound())

    var welford = Welford()
    var cvrPairs: List<Pair<AuditableCard, AuditableCard>> // (mvr, cvr)
    var idx = 0

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
        permutedIndex.shuffle(Random) // also, a new permutation....
        idx = 0
        welford = Welford()
    }

    fun remakeFuzzed(): List<AuditableCard> {
        return makeFuzzedCardsForClca(listOf(contest.info()), samples, fuzzPct)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
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

    override fun measuredClcaErrorCounts(): ClcaErrorCounts = clcaErrorTracker.measuredClcaErrorCounts()
    override fun noerror(): Double = clcaErrorTracker.noerror

    ///////////////////////////////// temporary
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun last(): Double = lastVal!!
    override fun addSample(sample: Double) {
        TODO("Not implemented")
    }
}