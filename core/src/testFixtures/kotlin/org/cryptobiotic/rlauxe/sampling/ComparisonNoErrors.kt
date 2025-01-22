package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ClcaAssorterIF
import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.random.Random

// the mvr and cvr always agree.
class ComparisonNoErrors(val contestId: Int, val cvrs : List<Cvr>, val cassorter: ClcaAssorterIF): Sampler {
    val maxSamples = cvrs.count { it.hasContest(contestId) }
    val permutedIndex = MutableList(cvrs.size) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        sampleMean = cvrs.map { cassorter.bassort(it, it)}.average()
        sampleCount = cvrs.sumOf { cassorter.bassort(it, it) }
    }

    override fun sample(): Double {
        require (idx < cvrs.size)
        val curr = cvrs[permutedIndex[idx++]]
        return cassorter.bassort(curr, curr) // mvr == cvr, no errors. could just return cassorter.noerror
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples
}