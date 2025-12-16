package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CvrIF
import org.cryptobiotic.rlauxe.core.*
import kotlin.random.Random

private val logger = KotlinLogging.logger("Sampling")

//// abstraction for creating a sequence of assort values
interface Sampling: Iterator<Double> {
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // max samples available, needed by testFn
    fun reset()   // start over again with different permutation (may be prohibited)
    fun maxSampleIndexUsed(): Int // the largest cvr index used in the sampling
    fun nmvrs(): Int // total number mvrs
}

// Note that we are stuffing the sampling logic into card.hasContest(contestId)

//// For polling audits. Production runPollingAuditRound
class PollingSampling(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
    val assorter: AssorterIF,
    val allowReset: Boolean = true,
): Sampling {
    val maxSamples = cvrPairs.count { it.second.hasContest(contestId) }
    val permutedIndex = MutableList(cvrPairs.size) { it }
    private var idx = 0
    private var count = 0

    init {
        cvrPairs.forEach { (mvr, card) -> require(mvr.location() == card.location())  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                count++
                return assorter.assort(mvr, usePhantoms = true)
            }
        }
        logger.error{"PollingSampling no samples left for contest ${contestId} and Assorter ${assorter}"}
        throw RuntimeException("PollingSampling no samples left for contest ${contestId} and assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error {"PollingSampling reset not allowed; contest ${contestId} assorter ${assorter}\""}
            throw RuntimeException("PollingSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = count
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}

//// For clca audits. Production RunClcaContestTask
class ClcaSampling(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
): Sampling, Iterator<Double> {
    val maxSamples = cvrPairs.count { it.second.hasContest(contestId) }
    val permutedIndex = MutableList(cvrPairs.size) { it }
    private var idx = 0
    private var count = 0

    init {
        // TODO this may not be true ??
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                val result = cassorter.bassort(mvr, card)
                count++
                return result
            }
        }
        logger.error{"ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaSampling reset not allowed"}
            throw RuntimeException("ClcaSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}

fun makeClcaNoErrorSampler(contestId: Int, cvrs : List<Cvr>, cassorter: ClcaAssorter): Sampling {
    val cvrPairs = cvrs.zip(cvrs)
    return ClcaSampling(contestId, cvrPairs, cassorter, true)
}


