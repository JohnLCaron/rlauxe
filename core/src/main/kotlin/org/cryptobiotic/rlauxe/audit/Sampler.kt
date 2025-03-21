package org.cryptobiotic.rlauxe.audit

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
    val mvrs : List<Cvr>, // TODO why not CvrUnderAudit ?
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
                return assorter.assort(mvr, usePhantoms = true)
            }
        }
        throw RuntimeException("no samples left for ${contestId} and Assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("PollWithoutReplacement reset not allowed")
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}

//// For clca audits
class ClcaWithoutReplacement(
    val contestId: Int,
    val hasStyles: Boolean,
    val cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr) why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
    val cassorter: ClcaAssorterIF,
    val allowReset: Boolean,
    val trackStratum: Boolean = false, // debugging for oneaudit
): Sampler, Iterator<Double> {
    val maxSamples = cvrPairs.count { it.first.hasContest(contestId) }
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
            if (mvr.hasContest(contestId)) {
                val result = cassorter.bassort(mvr, cvr)
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
}

fun makeClcaNoErrorSampler(contestId: Int, hasStyles: Boolean, cvrs : List<Cvr>, cassorter: ClcaAssorterIF): Sampler {
    val cvrPairs = cvrs.zip(cvrs)
    return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, true, false)
}




