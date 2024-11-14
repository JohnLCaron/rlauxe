package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford

interface SampleFn { // TODO could be an Iterator
    fun sample(): Double // get next in sample
    fun N(): Int  // population size
}

// the cvr pairs are in sample order. used in the real audit
class ComparisonSampler(
    val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>,
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter): SampleFn {

    val N = cvrPairs.size
    val welford = Welford()
    var idx = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
    }
    override fun N() = N

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[idx]
            if (cvr.hasContest(contestUA.id) && cvr.sampleNum <= contestUA.sampleThreshold!!) {
                val result = cassorter.bassort(mvr, cvr) // not sure of cvr vs cvr.cvr, w/re raire
                welford.update(result)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    // running values, not population values
    fun runningMean() = welford.mean
    fun runningCount() = welford.count
}

