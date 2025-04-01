package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

//// in memory, simulated mvrs for testing

class MvrManagerClcaForTesting(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : MvrManagerClcaIF, MvrManagerTest {
    val cvrsUA: List<CvrUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    private var mvrsRound: List<CvrUnderAudit> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
        mvrsUA = cvrsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    fun cvrs() = cvrs
    override fun Nballots(contestUA: ContestUnderAudit) = cvrs.size
    override fun ballotCards() : Iterator<BallotOrCvr> = cvrsUA.iterator()
    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<CvrUnderAudit> {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }

        setMvrsForRound(sampledMvrs)
        return sampledMvrs
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit> {
        TODO("Not yet implemented")
    }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>  {
        val sampleNumbers = mvrsRound.map { it.sampleNum }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA.iterator())

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<CvrUnderAudit, CvrUnderAudit>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.id == cvr.id)
            require(mvr.index == cvr.index)
            require(mvr.sampleNumber() == cvr.sampleNumber())
        }
        // why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
        return mvrsRound.map{ it.cvr }.zip(sampledCvrs.map{ it.cvr })
    }

    fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler  {
        if (mvrsRound.isEmpty()) return makeOneRoundSampler(contestId, hasStyles, cassorter, allowReset) // TODO ???
        val cvrPairs = makeCvrPairsForRound()
        return ClcaWithoutReplacement(contestId, hasStyles,cvrPairs, cassorter, allowReset = allowReset)
    }

    // just use the entire cvrs/mvrs
    fun makeOneRoundSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val cvrPairs = mvrsUA.map{ it.cvr }.zip(cvrsUA.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, allowReset = allowReset)
    }
}

class MvrManagerPollingForTesting(val ballots: List<Ballot>, mvrs: List<Cvr>, seed: Long) : MvrManagerPollingIF, MvrManagerTest {
    val ballotsUA: List<BallotUnderAudit>
    val mvrsUA: List<CvrUnderAudit>
    var mvrsRound: List<CvrUnderAudit> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
        mvrsUA = ballotsUA.map { CvrUnderAudit(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    fun ballots() = ballots
    override fun Nballots(contestUA: ContestUnderAudit) = ballots.size
    override fun ballotCards() : Iterator<BallotOrCvr> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<CvrUnderAudit> {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }

        setMvrsForRound(sampledMvrs)
        return sampledMvrs
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit> {
        TODO("Not yet implemented")
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        return if (mvrsRound.isEmpty())
            PollWithoutReplacement(contestId, hasStyles, mvrsUA.map { it.cvr } , assorter, allowReset=allowReset)
        else
            PollWithoutReplacement(contestId, hasStyles, mvrsRound.map { it.cvr }, assorter, allowReset=allowReset)
    }
}