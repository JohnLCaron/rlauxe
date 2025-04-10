package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

//// in memory, simulated mvrs for testing

class MvrManagerClcaForTesting(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : MvrManagerClcaIF, MvrManagerTest {
    val cvrsUA: List<AuditableCard>
    val mvrsUA: List<AuditableCard>
    private var mvrsRound: List<AuditableCard> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
        mvrsUA = cvrsUA.map { AuditableCard.fromCvr(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    fun cvrs() = cvrs
    override fun Nballots(contestUA: ContestUnderAudit) = cvrs.size
    override fun ballotCards() : Iterator<BallotOrCvr> = cvrsUA.iterator()
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
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

    override fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        TODO("Not yet implemented")
    }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>  {
        if (mvrsRound.isEmpty()) {
            return mvrsUA.map { it.cvr() }.zip(cvrsUA.map { it.cvr() }) // all of em, for SingleRoundAudit
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA.iterator())

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.desc == cvr.desc)
            require(mvr.index == cvr.index)
            require(mvr.sampleNumber() == cvr.sampleNumber())
        }
        return mvrsRound.map{ it.cvr() }.zip(sampledCvrs.map{ it.cvr() })
    }

    fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorter, allowReset: Boolean): Sampler  {
        if (mvrsRound.isEmpty()) return makeOneRoundSampler(contestId, hasStyles, cassorter, allowReset) // TODO ???
        val cvrPairs = makeCvrPairsForRound()
        return ClcaWithoutReplacement(contestId, hasStyles,cvrPairs, cassorter, allowReset = allowReset)
    }

    // just use the entire cvrs/mvrs
    fun makeOneRoundSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorter, allowReset: Boolean): Sampler {
        val cvrPairs = mvrsUA.map{ it.cvr() }.zip(cvrsUA.map{ it.cvr() })
        return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, allowReset = allowReset)
    }
}

class MvrManagerPollingForTesting(val ballots: List<Ballot>, mvrs: List<Cvr>, seed: Long) : MvrManagerPollingIF, MvrManagerTest {
    val ballotsUA: List<BallotUnderAudit>
    val mvrsUA: List<AuditableCard>
    var mvrsRound: List<AuditableCard> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
        mvrsUA = ballotsUA.map { AuditableCard.fromCvr(mvrs[it.index()], it.index(), it.sampleNumber()) }
    }

    fun ballots() = ballots
    override fun Nballots(contestUA: ContestUnderAudit) = ballots.size
    override fun ballotCards() : Iterator<BallotOrCvr> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
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

    override fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        TODO("Not yet implemented")
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        return if (mvrsRound.isEmpty())
            PollWithoutReplacement(contestId, hasStyles, mvrsUA.map { it.cvr() } , assorter, allowReset=allowReset)
        else
            PollWithoutReplacement(contestId, hasStyles, mvrsRound.map { it.cvr() }, assorter, allowReset=allowReset)
    }
}