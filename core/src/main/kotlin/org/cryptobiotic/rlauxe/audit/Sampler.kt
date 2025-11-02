package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.random.Random

private val logger = KotlinLogging.logger("Sampler")

//// abstraction for creating a sequence of samples
interface Sampler: Iterator<Double> {
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // population size
    fun reset()   // start over again with different permutation (may be prohibited)
    fun maxSampleIndexUsed(): Int // the largest cvr index used in the sampling
    fun nmvrs(): Int // total number mvrs
}

//// For polling audits.
class PollWithoutReplacement(
    val contestId: Int,
    val hasStyle: Boolean,
    val mvrs : List<Cvr>,
    val assorter: AssorterIF,
    val allowReset: Boolean = true,
): Sampler {
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

//// For clca audits
class ClcaWithoutReplacement(
    val contestId: Int,
    val hasStyle: Boolean,
    val cvrPairs: List<Pair<Cvr, Cvr>>,
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
    val trackStratum: Boolean = false, // debugging for oneaudit
): Sampler, Iterator<Double> {
    // TODO TIMING init taking 8%
    val maxSamples = cvrPairs.count { it.first.hasContest(contestId) }
    val permutedIndex = MutableList(cvrPairs.size) { it }
    private var idx = 0
    private var count = 0
    private var poolCount = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contestId)) {
                val result = cassorter.bassort(mvr, cvr)
                if (cvr.poolId != null) poolCount++
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

    fun poolCount() = poolCount
}

fun makeClcaNoErrorSampler(contestId: Int, hasStyle: Boolean, cvrs : List<Cvr>, cassorter: ClcaAssorter): Sampler {
    val cvrPairs = cvrs.zip(cvrs)
    return ClcaWithoutReplacement(contestId, hasStyle, cvrPairs, cassorter, true, false)
}

//// For clca audits with styles and no errors
// use iterator for efficiency
class ClcaNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val cassorter: ClcaAssorter,
    val cvrIterator: Iterator<Cvr>,
): Sampler, Iterator<Double> {
    private var idx = 0
    private var count = 0
    private var done = false

    override fun sample(): Double {
        while (cvrIterator.hasNext()) {
            val cvr = cvrIterator.next()
            idx++
            if (cvr.hasContest(contestId)) {
                val result = cassorter.bassort(cvr, cvr)
                count++
                return result
            }
        }
        done = true
        if (!warned) {
            logger.warn { "ClcaNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        logger.error{"ClcaNoErrorIterator reset not allowed"}
        throw RuntimeException("ClcaNoErrorIterator reset not allowed")
    }

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && (count < contestNc)
    override fun next() = sample()

    companion object {
        var warned = false
    }
}

class OneAuditNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val contestSampleCutoff: Int?,
    val cassorter: ClcaAssorter,
    cvrIter: Iterator<Cvr>,
): Sampler, Iterator<Double> {
    val cvrs = mutableListOf<Cvr>()
    var permutedIndex = mutableListOf<Int>()

    private var idx = 0
    private var count = 0
    private var done = false

    init {
        while ((contestSampleCutoff == null || cvrs.size < contestSampleCutoff) && cvrIter.hasNext()) {
            val cvr = cvrIter.next()
            if (cvr.hasContest(contestId)) cvrs.add(cvr)
        }
        permutedIndex = MutableList(cvrs.size) { it }
    }

    override fun sample(): Double {
        while (idx < cvrs.size) {
            val cvr = cvrs[permutedIndex[idx]]
            idx++
            val result = cassorter.bassort(cvr, cvr)
            count++
            return result
        }
        if (!warned) {
            logger.warn { "OneAuditNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && (count < contestNc)
    override fun next() = sample()

    companion object {
        var warned = false
    }
}





