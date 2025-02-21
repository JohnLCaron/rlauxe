package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.workflow.Sampler
import kotlin.random.Random

// generate mvr by starting with cvrs and flipping (N * p2) votes (type 2 errors) and (N * p1) votes (type 1 errors)
// this can be used to create an "attack" where outcome of election has been flipped
data class ClcaAttackSampler(val cvrs : List<Cvr>, val cassorter: ClcaAssorter,
                             val p2: Double, val p1: Double = 0.0,
                             val withoutReplacement: Boolean = true): Sampler {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
    val N = cvrs.size
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes2: Int
    val flippedVotes1: Int

    private var idx = 0
    private var count = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
        mmvrs.addAll(cvrs)
        flippedVotes2 = add2voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p2).toInt())
        flippedVotes1 =  if (p1 == 0.0) 0 else {
            add1voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p1).toInt())
        }
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / N
    }

    // TODO why not filtering by contest?
    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = Random.nextInt(N) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        count++
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = maxSamples
    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}


// generate mvr by starting with cvrs and flipping exact # votes (type 2 errors only) to make mvrs have mvrMean.
// only used by compareAlphaPaperMasses
data class ClcaFlipErrorsSampler(val cvrs : List<Cvr>, val cassorter: ClcaAssorter, val mvrMean: Double,
                                 val withoutReplacement: Boolean = true): Sampler {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(cvrs.size) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes: Int

    private var idx = 0
    private var count = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
        mmvrs.addAll(cvrs)
        flippedVotes = flipExactVotes(mmvrs, mvrMean)
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / cvrs.size
    }

    // TODO why not filtering by contest?
    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = Random.nextInt(cvrs.size) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        count++
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples
    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()
}
