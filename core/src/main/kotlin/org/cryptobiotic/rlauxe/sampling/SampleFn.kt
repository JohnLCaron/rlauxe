package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df

interface SampleFn { // TODO could be an Iterator
    fun sample(): Double // get next in sample
    fun N(): Int  // population size
}

// the cvr pairs are in sample order. used in the real audit
class ComparisonSampler(
    val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, // (mvr, cvr)
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter): SampleFn {

    val welford = Welford()
    var idx = 0

    init {
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
        /* val diff = cvrPairs.filter { (mvr, cvr) -> mvr.votes != cvr.votes }.count()
        println(" ${cassorter.name()} diff = $diff out of ${cvrPairs.size} = ${df(diff.toDouble()/cvrPairs.size)}")
        println("   mvrs= ${cvrPairs.map { (mvr, cvr) -> cassorter.assorter.assort(mvr) }.average()}")
        println("   cvrs= ${cvrPairs.map { (mvr, cvr) -> cassorter.assorter.assort(cvr) }.average()}") */
    }
    override fun N() = cvrPairs.size

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[idx]
            if (cvr.hasContest(contestUA.id) && cvr.sampleNum <= contestUA.sampleThreshold!!) {
                val result = cassorter.bassort(mvr, cvr)
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

