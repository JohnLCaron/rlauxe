package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssorterIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.Prng

//// in memory, simulated mvrs for testing

class StartTestBallotCardsClca(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : BallotCardsClcaStart {
    val cvrsUA: List<CvrUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    private var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
        mvrsUA = cvrsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun cvrs() = cvrs
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

    // just use the entire cvrs/mvrs
    fun makeOneRoundSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val cvrPairs = mvrsUA.map{ it.cvr }.zip(cvrsUA.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, allowReset = allowReset)
    }
}

class StartTestBallotCardsPolling(val ballots: List<Ballot>, mvrs: List<Cvr>, seed: Long) : BallotCardsPollingStart {
    val ballotsUA: List<BallotUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
        mvrsUA = ballotsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    override fun ballots() = ballots
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
        return if (mvrsForRound.isEmpty())
            PollWithoutReplacement(contestId, mvrsUA.map { it.cvr } , assorter, allowReset=allowReset)
        else
            PollWithoutReplacement(contestId, mvrsForRound.map { it.cvr }, assorter, allowReset=allowReset)
    }
}