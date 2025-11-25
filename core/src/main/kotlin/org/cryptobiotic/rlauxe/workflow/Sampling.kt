package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.random.Random

private val logger = KotlinLogging.logger("Sampling")

//// abstraction for creating a sequence of assort values
interface Sampling: Iterator<Double> {
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // population size TODO wtf?
    fun reset()   // start over again with different permutation (may be prohibited)
    fun maxSampleIndexUsed(): Int // the largest cvr index used in the sampling
    fun nmvrs(): Int // total number mvrs
}

// TODO we're stuffing sampling logic into card.hasContest(contestId)

//// For polling audits. Production runPollingAuditRound
class PollWithoutReplacement(
    val contestId: Int,
    val cvrPairs: List<Pair<CardIF, CardIF>>, // Pair(mvr, card)
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
        logger.error{"PollWithoutReplacement no samples left for contest ${contestId} and Assorter ${assorter}"}
        throw RuntimeException("PollWithoutReplacement no samples left for contest ${contestId} and assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error {"PollWithoutReplacement reset not allowed; contest ${contestId} assorter ${assorter}\""}
            throw RuntimeException("PollWithoutReplacement reset not allowed")
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
class ClcaWithoutReplacement(
    val contestId: Int,
    val cvrPairs: List<Pair<CardIF, CardIF>>, // Pair(mvr, card)
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
): Sampling, Iterator<Double> {
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
                val result = cassorter.bassort(mvr, card)
                count++
                return result
            }
        }
        logger.error{"ClcaWithoutReplacement no samples left for ${contestId} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("ClcaWithoutReplacement no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaWithoutReplacement reset not allowed"}
            throw RuntimeException("ClcaWithoutReplacement reset not allowed")
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
    return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, true) // TODO
}


