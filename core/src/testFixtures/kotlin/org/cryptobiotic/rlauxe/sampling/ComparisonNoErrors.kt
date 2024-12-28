package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.random.Random


// the mvr and cvr always agree.
class ComparisonNoErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter): Sampler {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
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