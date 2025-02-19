package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditComparisonAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestUnderAudit
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

// this takes a list of cvrs and fuzzes them
class ClcaFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: ContestIF,
    val cassorter: ClcaAssorterIF
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

// used in simulateSampleSizePollingAssorter
class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterFunction
): Sampler, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) }
    val N = cvrs.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    private var mvrs: List<Cvr>
    private var idx = 0

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
    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

// used in simulateSampleSizeOneAuditAssorter
class OneAuditFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: OneAuditContestUnderAudit,
    val cassorter: OneAuditComparisonAssorter
): Sampler, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contestUA.id) }
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    val stratumNames : Set<String>
    var cvrPairs: List<Pair<Cvr, Cvr>> // (mvr, cvr)
    var idx = 0

    init {
        stratumNames = contestUA.contestOA.strata.map { it.strataName }.toSet()
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
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contestUA.contestOA), cvrs, fuzzPct) { !stratumNames.contains(it.id) }
    }

    override fun maxSamples() = maxSamples
    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO cant be used on approval

fun makeFuzzedCvrsFrom(contests: List<ContestIF>, cvrs: List<Cvr>, fuzzPct: Double, filter: ((CvrBuilder) -> Boolean)? = null): List<Cvr> {
    if (fuzzPct == 0.0) return cvrs
    val isIRV = contests.associate { it.info.name to (it.choiceFunction == SocialChoiceFunction.IRV) }.toMap()

    var count = 0
    val cvrbs = CvrBuilders.convertCvrs(contests.map { it.info }, cvrs)
    cvrbs.filter { !it.phantom && (filter == null || filter(it)) }.forEach { cvrb: CvrBuilder ->
        val r = Random.nextDouble(1.0)
        cvrb.contests.forEach { (_, cvb) ->
            if (r < fuzzPct) {
                val ccontest: CvrContest = cvb.contest
                if (isIRV[ccontest.name]!!) {
                    switchCandidateRankings(cvb, ccontest.candidateIds)
                } else {
                    val currId: Int? = if (cvb.votes.size == 0) null else cvb.votes[0] // TODO only one vote allowed
                    cvb.votes.clear()
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, ccontest.candidateIds)
                    if (ncandId != null) {
                        cvb.votes.add(ncandId)
                    }
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
        if (ncandIdx == size)
            return null // choose none
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}

// for IRV
fun switchCandidateRankings(cvb: ContestVoteBuilder, candidateIds: List<Int>) {
    val ncands = candidateIds.size
    val size = cvb.votes.size
    if (size == 0) { // no votes -> random one vote
        val candIdx = Random.nextInt(ncands)
        cvb.votes.add(candidateIds[candIdx])
    } else if (size == 1) { // one votes -> no votes
        cvb.votes.clear()
    } else { // switch two randomly selected votes
        val ncandIdx1 = Random.nextInt(size)
        val ncandIdx2 = Random.nextInt(size)
        val save = cvb.votes[ncandIdx1]
        cvb.votes[ncandIdx1] = cvb.votes[ncandIdx2]
        cvb.votes[ncandIdx2] = save
    }
}