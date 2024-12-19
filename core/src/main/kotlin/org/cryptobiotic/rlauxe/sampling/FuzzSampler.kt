package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

// this takes a list of cvrs and fuzzes them
class ComparisonFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter
): SampleGenerator, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contestUA.id) }
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    var cvrPairs: List<Pair<Cvr, Cvr>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                welford.update(result)
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contestUA.contest as Contest), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples

    override fun hasNext(): Boolean = (idx < N)

    override fun next(): Double = sample()
}

class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: ContestUnderAudit,
    val assorter: AssorterFunction
): SampleGenerator {
    val maxSamples = cvrs.count { it.hasContest(contestUA.id) }
    val N = cvrs.size
    val welford = Welford()
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
                welford.update(result)
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
        return makeFuzzedCvrsFrom(listOf(contestUA.contest as Contest), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples
}

fun makeFuzzedCvrsFrom(contests: List<Contest>, cvrs: List<Cvr>, fuzzPct: Double): List<Cvr> {
    var count = 0
    var countf = 0
    val cvrbs = CvrBuilders.convertCvrs(contests.map { it.info }, cvrs)
    cvrbs.forEach { cvrb: CvrBuilder ->
        val r = secureRandom.nextDouble(1.0)
        cvrb.contests.forEach { (_, cvb) ->
            if (r < fuzzPct) {
                countf++
                val ccontest: CvrContest = cvb.contest
                val currId: Int? = if (cvb.votes.size == 0) null else cvb.votes[0] // TODO only one vote allowed
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
    return cvrbs.map { it.build() }
}

fun chooseNewCandidate(currId: Int?, candidateIds: List<Int>): Int? {
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