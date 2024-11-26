package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CvrBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.CvrContest
import org.cryptobiotic.rlauxe.util.secureRandom

class ComparisonSamplerRegen(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter
): GenSampleFn {
    val N = cvrs.size
    val cvrsUA = cvrs.map { CvrUnderAudit(it, false) } // if you dont need sampleNum, you dont need CvrUnderAudit

    val permutedIndex = MutableList(N) { it }
    var cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrsUA)
    }

    override fun sample(): Double {
        while (idx < N) {
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
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrsUA)
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contestUA.contest), cvrs, fuzzPct)
    }

    // TODO where needed ?
    override fun sampleMean() = 0.0
    override fun sampleCount() = 0.0
    override fun N() = N
}

class PollingSamplerRegen(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: ContestUnderAudit,
    val assorter: AssorterFunction
): GenSampleFn {
    val N = cvrs.size
    val cvrsUA = cvrs.map { CvrUnderAudit(it, false) } // if you dont need sampleNum, you dont need CvrUnderAudit

    val permutedIndex = MutableList(N) { it }
    var mvrs: List<CvrIF>
    var idx = 0

    init {
        mvrs = remakeFuzzed()
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            if (mvr.hasContest(contestUA.id)) {
                val result = assorter.assort(mvr)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contestUA.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contestUA.contest), cvrs, fuzzPct)
    }

    // TODO where needed ?
    override fun sampleMean() = 0.0
    override fun sampleCount() = 0.0
    override fun N() = N
}

fun makeFuzzedCvrsFrom(contests: List<Contest>, cvrs: List<Cvr>, fuzzPct: Double): List<Cvr> {
    var count = 0
    var countf = 0
    val cvrbs = CvrBuilders.convertCvrs(contests.map { it.info }, cvrs)
    cvrbs.forEach { cvrb: CvrBuilder ->
        val r = secureRandom.nextDouble(1.0)
        cvrb.contests.forEach { (contestId, cvb) ->
            if (r < fuzzPct) {
                countf++
                val ccontest: CvrContest = cvb.contest
                val currId = cvb.votes[0] // TODO only one vote allowed
                cvb.votes.clear()

                // choose a different candidate, or none.
                val ncandId = chooseNewCandidate(currId, ccontest.candidateIds)
                if (ncandId != null) {
                    cvb.votes.add(ncandId)
                }
            }
        }
        count++
    }
    // println("makeFuzzedCvrsFrom $countf/ $count")
    return cvrbs.map { it.build() }
}

fun chooseNewCandidate(currId: Int, candidateIds: List<Int>): Int? {
    val size = candidateIds.size
    while (true) {
        val ncandIdx = secureRandom.nextInt(size + 1)
        if (ncandIdx == size) return null
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}