package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.random.Random

//// abstraction for creating a sequence of samples
interface Sampler {
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // population size
    fun reset()   // start over again with different permutation (may be prohibited)
}

//// For polling audits.

class PollWithReplacement(val contest: Contest, val mvrs : List<Cvr>, val assorter: AssorterFunction): Sampler {
    val maxSamples = mvrs.count { it.hasContest(contest.id) }

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(mvrs.size) // with Replacement
            val cvr = mvrs[idx]
            if (cvr.hasContest(contest.id)) return assorter.assort(cvr, usePhantoms = true)
        }
    }

    override fun reset() {}
    override fun maxSamples() = maxSamples
}

class PollWithoutReplacement(
    val contest: ContestIF,
    val mvrs : List<Cvr>,
    val assorter: AssorterFunction,
    val allowReset: Boolean = true,
): Sampler {
    val maxSamples = mvrs.count { it.hasContest(contest.id) }
    private val permutedIndex = MutableList(mvrs.size) { it }
    private var idx = 0

    init {
        if (allowReset) reset()
    }

    override fun sample(): Double {
        while (idx < mvrs.size) {
            val cvr = mvrs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contest.id)) {
                return assorter.assort(cvr, usePhantoms = true)
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("PollWithoutReplacement reset not allowed")
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun maxSamples() = maxSamples
}

//// For comparison audits
// the values produced here are the B assort values, SHANGRLA section 3.2.

class ComparisonWithoutReplacement(
    val contestUA: ContestIF,
    val cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    val cassorter: ClcaAssorterIF,
    val allowReset: Boolean,
    val trackStratum: Boolean = false,
): Sampler {
    val maxSamples = cvrPairs.count { it.first.hasContest(contestUA.id) }
    val permutedIndex = MutableList(cvrPairs.size) { it }
    var idx = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id)) {
                val result = cassorter.bassort(mvr, cvr)
                if (trackStratum) print("${sfn(cvr.id, 8)} ")
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("ComparisonWithoutReplacement no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) throw RuntimeException("ComparisonWithoutReplacement reset not allowed")
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun maxSamples() = maxSamples
}




