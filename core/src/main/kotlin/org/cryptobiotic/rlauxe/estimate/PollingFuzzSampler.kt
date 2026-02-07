package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.ContestVoteBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.CvrContest
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.random.Random

// could try to create a subclass of PollingSamplerTracker ??

class PollingFuzzSamplerTracker(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterIF,
): SamplerTracker {
    var maxSamples = cvrs.count { it.hasContest(contest.id) } // dont need this if its single contest
    var welford = Welford()
    val permutedIndex = MutableList(cvrs.size) { it }
    private var mvrs: List<Cvr>
    private var idx = 0

    init {
        mvrs = remakeFuzzed() // TODO could do fuzzing on the fly ??
        maxSamples = cvrs.count { it.hasContest(contest.id) }
    }

    override fun sample(): Double {
        while (idx < cvrs.size) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contest.id)) {
                if (lastVal != null) welford.update(lastVal!!)
                lastVal =  assorter.assort(mvr, usePhantoms = true)
                return lastVal!!
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
        maxSamples = cvrs.count { it.hasContest(contest.id) }
        welford = Welford()
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsForPolling(listOf(contest.info()), cvrs, fuzzPct) // single contest
    }

    override fun maxSamples() = maxSamples
    override fun countCvrsUsedInAudit() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun welford() = welford
    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }
}

// includes undervotes i think, IRV ok
fun makeFuzzedCvrsForPolling(infoList: List<ContestInfo>,
                             cvrs: List<Cvr>,
                             fuzzPct: Double,
                             welford: Welford? = null,
                             filter: ((CvrBuilder) -> Boolean)? = null,
): List<Cvr> {
    if (fuzzPct == 0.0) return cvrs

    val isIRV = infoList.associate { it.name to it.isIrv }.toMap()
    var count = 0
    val cvrbs: List<CvrBuilder> = CvrBuilders.convertCvrsToBuilders(infoList, cvrs)

    cvrbs.filter { !it.phantom && (filter == null || filter(it)) }.forEach { cvrb: CvrBuilder ->
        val r = Random.nextDouble(1.0)
        cvrb.contests.forEach { (_, cvb) ->
        if (r < fuzzPct) {
                val ccontest: CvrContest = cvb.contest
                if (isIRV[ccontest.name]!!) {
                    switchCandidateRankings(cvb, ccontest.candidateIds)
                } else {
                    val currId: Int? = if (cvb.votes.size == 0) null else cvb.votes[0] // TODO only one vote allowed, cant use on Raire
                    cvb.votes.clear()
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, ccontest.candidateIds) // from ClcaFuzzSamplerTracker
                    if (ncandId != null) {
                        cvb.votes.add(ncandId)
                    }
                }
            }
        }
        if (r < fuzzPct) count++
    }

    val expect = (cvrs.size * fuzzPct).toInt()
    val got = (count / cvrs.size.toDouble())
    if (welford != null) { welford.update(fuzzPct - got) }
    // println("   fuzzPct=$fuzzPct expect=$expect count: $count")
    return cvrbs.map { it.build() }
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
