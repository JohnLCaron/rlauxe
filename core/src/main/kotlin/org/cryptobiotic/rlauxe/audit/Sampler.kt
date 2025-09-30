package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.random.Random

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
    val hasStyles: Boolean,
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
        throw RuntimeException("no samples left for contest ${contestId} and Assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("PollWithoutReplacement reset not allowed")
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
    val hasStyles: Boolean,
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
        throw RuntimeException("ClcaWithoutReplacement no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("ClcaWithoutReplacement reset not allowed")
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

fun makeClcaNoErrorSampler(contestId: Int, hasStyles: Boolean, cvrs : List<Cvr>, cassorter: ClcaAssorter): Sampler {
    val cvrPairs = cvrs.zip(cvrs)
    return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, true, false)
}

//// For clca audits with styles and no errors
// use iterator for efficiency
class ClcaNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val cvrIter: Iterator<Cvr>,
    val cassorter: ClcaAssorter,
): Sampler, Iterator<Double> {
    private var idx = 0
    private var count = 0
    private var done = false

    override fun sample(): Double {
        while (cvrIter.hasNext()) {
            val cvr = cvrIter.next()
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
        throw RuntimeException("ClcaNoErrorIterator reset not allowed")
    }

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && (count < contestNc)
    override fun next() = sample()

    companion object {
        private val logger = KotlinLogging.logger("ClcaNoErrorIterator")
        var warned = false
    }
}

class OneAuditNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val cassorter: ClcaAssorter,
    val sampleLimit: Int,
    cvrIter: Iterator<Cvr>,
): Sampler, Iterator<Double> {
    val cvrs = mutableListOf<Cvr>()
    var permutedIndex = mutableListOf<Int>()

    private var idx = 0
    private var count = 0
    private var done = false

    init {
        while ((sampleLimit == -1 || cvrs.size < sampleLimit) && cvrIter.hasNext()) {
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
        private val logger = KotlinLogging.logger("OneAuditNoErrorIterator")
        var warned = false
    }
}





