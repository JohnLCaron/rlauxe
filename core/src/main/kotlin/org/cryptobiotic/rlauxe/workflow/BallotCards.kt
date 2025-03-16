package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

private val debugWantSampleSize = true

interface BallotOrCvr {
    fun hasContest(contestId: Int): Boolean
    fun sampleNumber(): Long
    fun index(): Int
}

interface BallotCards {
    fun nballotCards(): Int
    fun ballotCards() : Iterable<BallotOrCvr>
    fun setMvrs(mvrs: List<CvrUnderAudit>)
    fun takeFirst(nmvrs: Int): List<BallotOrCvr> = ballotCards().take(nmvrs).toList()
}

interface BallotCardsClca : BallotCards {
    fun makeSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler
}

interface BallotCardsPolling : BallotCards {
    fun makeSampler(contestId: Int, assorter: AssorterIF, allowReset: Boolean): Sampler
}

//// in memory, simulated mvrs for testing

class BallotCardsClcaStart(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : BallotCardsClca {
    val cvrsUA: List<CvrUnderAudit>
    val mvrsUA: List<CvrUnderAudit>

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
        mvrsUA = cvrsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun nballotCards() = cvrs.size
    override fun ballotCards() : Iterable<BallotOrCvr> = cvrsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        TODO("Not used")
    }

    // perhaps we want to set a limit on the sampler size ?
    override fun makeSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        // why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
        val cvrPairs = mvrsUA.map{ it.cvr }.zip(cvrsUA.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, allowReset = false)
    }
}

class BallotCardsPollingStart(val ballots: List<Ballot>, mvrs: List<Cvr>, seed: Long) : BallotCardsPolling {
    val ballotsUA: List<BallotUnderAudit>
    val mvrsUA: List<CvrUnderAudit>

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
        mvrsUA = ballotsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun nballotCards() = ballots.size
    override fun ballotCards() : Iterable<BallotOrCvr> = ballotsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        TODO("Not used")
    }

    override fun makeSampler(contestId: Int, assorter: AssorterIF, allowReset: Boolean): Sampler {
        // why not List<CvrUnderAudit> ??
        return PollWithoutReplacement(contestId, mvrsUA.map{ it.cvr }, assorter, allowReset=allowReset)
    }
}

fun findSamples(sampleNumbers: List<Long>, sortedMvrs: Iterable<CvrUnderAudit>): List<CvrUnderAudit> {
    val result = mutableListOf<CvrUnderAudit>()
    val sortedIter = sortedMvrs.iterator()
    sampleNumbers.forEach { sampleNum ->
        while (sortedIter.hasNext()) {
            val boc = sortedIter.next() // sampleIndices must in same order as sortedMvrs
            if (boc.sampleNumber() == sampleNum) {
                result.add(boc)
                break
            }
        }
    }
    return result
}

fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedBorc : Iterable<BallotOrCvr>): Map<Int, Int> {
    // count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    val sortedBorcIter = sortedBorc.iterator()
    previousSamples.forEach { prevNumber ->
        while (sortedBorcIter.hasNext()) {
            val boc = sortedBorcIter.next() // previousSamples must be in same order as sortedBorc
            if (boc.sampleNumber() == prevNumber) {
                contestsNotDone.forEach { contest ->
                    if (boc.hasContest(contest.id)) {
                        prevContestCounts[contest] = prevContestCounts[contest]?.plus(1) ?: 1
                    }
                }
                break
            }
        }
    }
    if (debugWantSampleSize) {
        val prevContestCountsById = prevContestCounts.entries.map { it.key.id to it.value }.toMap()
        println("**wantSampleSize prevContestCountsById = ${prevContestCountsById}")
    }

    // we need that in order to calculate wantedSampleSizes
    val wantSampleSize = prevContestCounts.entries.map { it.key.id to it.key.sampleSize(it.value) }.toMap()
    if (debugWantSampleSize) println("**wantSampleSize = $wantSampleSize")

    return wantSampleSize
}