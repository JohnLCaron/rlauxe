package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
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

//// For polling audits. Production runPollingAuditRound
class PollWithoutReplacement(
    val contestId: Int,
    val mvrs : List<Cvr>,
    val assorter: AssorterIF,
    val allowReset: Boolean = true,
): Sampling {
    val maxSamples = mvrs.count { it.hasContest(contestId) }
    private val permutedIndex = MutableList(mvrs.size) { it }
    private var idx = 0
    private var count = 0

    init {
        if (allowReset) reset()
    }

    override fun sample(): Double {
        while (idx < mvrs.size) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contestId)) {
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
    override fun nmvrs() = mvrs.size

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}

//// For clca audits. Production RunClcaContestTask
// TODO in what circumstances do you filter by contest ?? or should it be possibleContests ??
class ClcaWithoutReplacement(
    val contestId: Int,
    val cvrPairs: List<Pair<Cvr, Cvr>>, // Pair(mvr, cvr) TODO List<Pair<Cvr, AuditableCard>> ??
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
    val trackStratum: Boolean = false, // debugging for oneaudit
): Sampling, Iterator<Double> {
    // TODO TIMING init taking 8%
    val maxSamples = cvrPairs.count { it.first.hasContest(contestId) } // TODO mvr vs cvr hasContest. should be cvr i think....??
    val permutedIndex = MutableList(cvrPairs.size) { it }
    private var idx = 0
    private var count = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contestId)) { // TODO mvr vs cvr hasContest. should be cvr i think....??
                val result = cassorter.bassort(mvr, cvr)
                if (trackStratum) print("${sfn(cvr.id, 8)} ")
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



