package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.secureRandom

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
            if (cvr.hasContest(contestUA.id) && (cvr.sampleNum <= contestUA.sampleThreshold || contestUA.sampleThreshold == 0L)) {
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

class ComparisonSamplerGen(
    val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, // (mvr, cvr)
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter
): GenSampleFn {
    val N = cvrPairs.size
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    var idx = 0

    init {
        reset()
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id)  }
        sampleMean = cvrPairs.filter{it.first.hasContest(contestUA.id)}.map { (mvr, cvr) -> cassorter.bassort(mvr, cvr) }.average()
        sampleCount = cvrPairs.filter{it.first.hasContest(contestUA.id)}.map { (mvr, cvr) -> cassorter.bassort(mvr, cvr) }.sum() // wtf?
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id) && (cvr.sampleNum <= contestUA.sampleThreshold || contestUA.sampleThreshold == 0L)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    // TODO where needed ?
    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
}

