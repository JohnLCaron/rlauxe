package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

private val debugWantSampleSize = false

interface BallotOrCvr {
    fun hasContest(contestId: Int): Boolean
    fun sampleNumber(): Long
    fun index(): Int
}

interface BallotCards {
    fun nballotCards(): Int
    fun ballotCards() : Iterable<BallotOrCvr>
    fun setMvrs(mvrs: List<CvrUnderAudit>)
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    fun takeFirst(nmvrs: Int): List<BallotOrCvr> = ballotCards().take(nmvrs).toList()
}

interface BallotCardsClca : BallotCards {
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean = false): Sampler
}

interface BallotCardsClcaStart : BallotCardsClca {
    fun cvrs() : List<Cvr>
}

class StartBallotCardsClca(val cvrs: List<Cvr>, seed: Long) : BallotCardsClcaStart {
    val cvrsUA: List<CvrUnderAudit>
    private var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
    }

    override fun cvrs() = cvrs
    override fun nballotCards() = cvrs.size
    override fun ballotCards() : Iterable<BallotOrCvr> = cvrsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        TODO("Unimplemented")
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val sampleNumbers = mvrsForRound.map { it.sampleNum }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsForRound.size)
        val cvruaPairs: List<Pair<CvrUnderAudit, CvrUnderAudit>> = mvrsForRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.id == cvr.id)
            require(mvr.index == cvr.index)
            require(mvr.sampleNumber() == cvr.sampleNumber())
        }
        // why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
        val cvrPairs = mvrsForRound.map{ it.cvr }.zip(sampledCvrs.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, allowReset = allowReset)
    }
}

interface BallotCardsPolling : BallotCards {
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler
}

interface BallotCardsPollingStart : BallotCardsPolling {
    fun ballots(): List<Ballot>
}

class StartBallotCardsPolling(val ballots: List<Ballot>, seed: Long) : BallotCardsPollingStart {
    val ballotsUA: List<BallotUnderAudit>
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
    }

    override fun ballots() = ballots
    override fun nballotCards() = ballots.size
    override fun ballotCards() : Iterable<BallotOrCvr> = ballotsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        TODO("Unimplemented")
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        return PollWithoutReplacement(contestId, hasStyles, mvrsForRound.map { it.cvr }, assorter, allowReset=allowReset)
    }
}

// Iterate through sortedCvrUAs to find the cvrUAs that match the sampleNumbers
// sampleNumbers must in same order as sortedCvrUAs
// Note this iterates through sortedCvrUAs only until all sampleNumbers have been found
fun findSamples(sampleNumbers: List<Long>, sortedCvrUAs: Iterator<CvrUnderAudit>): List<CvrUnderAudit> {
    val result = mutableListOf<CvrUnderAudit>()
    sampleNumbers.forEach { sampleNum ->
        while (sortedCvrUAs.hasNext()) {
            val boc = sortedCvrUAs.next()
            if (boc.sampleNumber() == sampleNum) {
                result.add(boc)
                break
            }
        }
    }
    require(result.size == sampleNumbers.size)
    return result
}

//// TODO this is a lot of trouble to calculate prevContestCounts; we only need it if contest.auditorWantNewMvrs has been set
// for each contest, return map contestId -> wantSampleSize
fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedBorc : Iterable<BallotOrCvr>): Map<Int, Int> {
    //// count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    // Note this iterates through sortedBorc only until all previousSamples have been found and counted
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

    // we need prevContestCounts in order to calculate wantSampleSize if contest.auditorWantNewMvrs has been set
    val wantSampleSizeMap = prevContestCounts.entries.map { it.key.id to it.key.wantSampleSize(it.value) }.toMap()
    if (debugWantSampleSize) println("**wantSampleSize = $wantSampleSizeMap")

    return wantSampleSizeMap
}