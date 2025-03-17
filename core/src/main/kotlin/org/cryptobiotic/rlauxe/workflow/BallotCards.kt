package org.cryptobiotic.rlauxe.workflow

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
    fun makeSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean = false): Sampler
}

interface BallotCardsPolling : BallotCards {
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, assorter: AssorterIF, allowReset: Boolean): Sampler
}

//// in memory, simulated mvrs for testing

class BallotCardsClcaStart(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : BallotCardsClca {
    val cvrsUA: List<CvrUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    private var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
        mvrsUA = cvrsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun nballotCards() = cvrs.size
    override fun ballotCards() : Iterable<BallotOrCvr> = cvrsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA)
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }

        setMvrs(sampledMvrs)
    }

    override fun makeSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        if (mvrsForRound.isEmpty()) return makeOneRoundSampler(contestId, cassorter, allowReset)
        val sampleNumbers = mvrsForRound.map { it.sampleNum }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA)

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
        return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, allowReset = allowReset)
    }

    // fust use the entire cvrs/mvrs
    fun makeOneRoundSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val cvrPairs = mvrsUA.map{ it.cvr }.zip(cvrsUA.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, allowReset = allowReset)
    }
}

class BallotCardsPollingStart(val ballots: List<Ballot>, mvrs: List<Cvr>, seed: Long) : BallotCardsPolling {
    val ballotsUA: List<BallotUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
        mvrsUA = ballotsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun nballotCards() = ballots.size
    override fun ballotCards() : Iterable<BallotOrCvr> = ballotsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA)
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }

        setMvrs(sampledMvrs)
    }

    override fun makeSampler(contestId: Int, assorter: AssorterIF, allowReset: Boolean): Sampler {
        // why not List<CvrUnderAudit> ??
        return PollWithoutReplacement(contestId, mvrsForRound.map { it.cvr } , assorter, allowReset=allowReset)
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