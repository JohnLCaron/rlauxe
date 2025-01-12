package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

// this takes a list of cvrs and fuzzes them
class ComparisonFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val cassorter: ComparisonAssorterIF
): Sampler, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) }
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
            if (cvr.hasContest(contest.id)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                welford.update(result)
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples

    override fun hasNext(): Boolean = (idx < N)

    override fun next(): Double = sample()
}

class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterFunction
): Sampler {
    val maxSamples = cvrs.count { it.hasContest(contest.id) }
    val N = cvrs.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    var mvrs: List<Cvr>
    var idx = 0

    init {
        mvrs = remakeFuzzed()
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            if (mvr.hasContest(contest.id)) {
                val result = assorter.assort(mvr, usePhantoms = true)
                idx++
                welford.update(result)
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples
}

// TODO cant be used on raire, approval
fun makeFuzzedCvrsFrom(contests: List<Contest>, cvrs: List<Cvr>, fuzzPct: Double): List<Cvr> {
    var count = 0
    val cvrbs = CvrBuilders.convertCvrs(contests.map { it.info }, cvrs)
    cvrbs.filter { !it.phantom }.forEach { cvrb: CvrBuilder ->
        val r = Random.nextDouble(1.0)
        cvrb.contests.forEach { (_, cvb) ->
            if (r < fuzzPct) {
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
        val ncandIdx = Random.nextInt(size + 1)
        if (ncandIdx == size) return null // choose none
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}